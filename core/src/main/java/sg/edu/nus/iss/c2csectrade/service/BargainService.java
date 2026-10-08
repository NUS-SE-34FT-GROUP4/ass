package sg.edu.nus.iss.c2csectrade.service;

import sg.edu.nus.iss.c2csectrade.entity.BargainActivity;
import sg.edu.nus.iss.c2csectrade.entity.BargainHelp;
import sg.edu.nus.iss.c2csectrade.entity.Product;
import sg.edu.nus.iss.c2csectrade.mapper.BargainActivityMapper;
import sg.edu.nus.iss.c2csectrade.mapper.BargainHelpMapper;
import sg.edu.nus.iss.c2csectrade.mapper.ProductMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Date;
import java.util.HashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
public class BargainService {

    @Autowired
    private BargainActivityMapper bargainActivityMapper;

    @Autowired
    private BargainHelpMapper bargainHelpMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private OrderService orderService;

    private static final Random random = new Random();

    /**
     * Start a bargain activity
     */
    @Transactional
    public BargainActivity startBargain(Long userId, Long productId) {
        // Check that the product exists
        Product product = productMapper.selectById(productId);
        if (product == null) {
            throw new RuntimeException("Product not found");
        }

        // Check whether the user has already started a bargain for this product
        BargainActivity existingBargain = bargainActivityMapper.selectActiveByUserAndProduct(userId, productId);
        if (existingBargain != null) {
            throw new RuntimeException("You have already started a bargain for this product");
        }

        // Create the bargain activity
        BargainActivity bargainActivity = new BargainActivity();
        bargainActivity.setUserId(userId);
        bargainActivity.setProductId(productId);
        bargainActivity.setOriginalPrice(product.getPrice());

        // Target price is 60%-80% of the original price (random)
        BigDecimal targetRatio = BigDecimal.valueOf(0.6 + random.nextDouble() * 0.2);
        BigDecimal targetPrice = product.getPrice().multiply(targetRatio).setScale(2, RoundingMode.HALF_UP);
        bargainActivity.setTargetPrice(targetPrice);

        bargainActivity.setCurrentPrice(product.getPrice());
        bargainActivity.setStatus("ACTIVE");

        // Expires in 24 hours
        Date expireTime = new Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000);
        bargainActivity.setExpireTime(expireTime);

        bargainActivity.setCreatedAt(new Date());
        bargainActivity.setUpdatedAt(new Date());

        bargainActivityMapper.insert(bargainActivity);

        return bargainActivityMapper.selectById(bargainActivity.getId());
    }

    /**
     * Help a bargain
     */
    @Transactional
    public BargainHelp helpBargain(Long bargainId, Long helperId, String helperName) {
        // Check that the bargain exists
        BargainActivity bargainActivity = bargainActivityMapper.selectById(bargainId);
        if (bargainActivity == null) {
            throw new RuntimeException("Bargain not found");
        }

        // Check whether the bargain has expired
        if (bargainActivity.getExpireTime().before(new Date())) {
            bargainActivityMapper.updateStatus(bargainId, "EXPIRED");
            throw new RuntimeException("This bargain has expired; help failed");
        }

        // Check the bargain status
        if (!"ACTIVE".equals(bargainActivity.getStatus())) {
            String message = "EXPIRED".equals(bargainActivity.getStatus()) ? "This bargain has expired" : "This bargain has ended";
            throw new RuntimeException(message);
        }

        // Check the product still exists and is in stock (it may have been bought by someone else)
        Product product = productMapper.selectById(bargainActivity.getProductId());
        if (product == null) {
            bargainActivityMapper.updateStatus(bargainId, "FAILED");
            throw new RuntimeException("The product has been delisted; help failed");
        }
        if (product.getStock() < 1) {
            bargainActivityMapper.updateStatus(bargainId, "FAILED");
            throw new RuntimeException("The product is sold out; help failed");
        }

        // Check whether this helper has already helped (when helperId is present)
        if (helperId != null) {
            BargainHelp existingHelp = bargainHelpMapper.selectByBargainIdAndHelperId(bargainId, helperId);
            if (existingHelp != null) {
                throw new RuntimeException("You have already helped this bargain");
            }
        }

        // Work out the cut
        BigDecimal remainingAmount = bargainActivity.getCurrentPrice().subtract(bargainActivity.getTargetPrice());
        BigDecimal cutAmount;

        if (remainingAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RuntimeException("This bargain is already complete");
        }

        // Random cut: 5%-20% of the remaining amount
        double ratio = 0.05 + random.nextDouble() * 0.15;
        cutAmount = remainingAmount.multiply(BigDecimal.valueOf(ratio)).setScale(2, RoundingMode.HALF_UP);

        // Cut at least 0.01
        if (cutAmount.compareTo(new BigDecimal("0.01")) < 0) {
            cutAmount = new BigDecimal("0.01");
        }

        // Never cut more than the remaining amount
        if (cutAmount.compareTo(remainingAmount) > 0) {
            cutAmount = remainingAmount;
        }

        // Record the help
        BargainHelp bargainHelp = new BargainHelp();
        bargainHelp.setBargainId(bargainId);
        bargainHelp.setHelperId(helperId);
        bargainHelp.setHelperName(helperName);
        bargainHelp.setCutAmount(cutAmount);
        bargainHelp.setCreatedAt(new Date());

        bargainHelpMapper.insert(bargainHelp);

        // Update the current price
        BigDecimal newPrice = bargainActivity.getCurrentPrice().subtract(cutAmount);
        bargainActivity.setCurrentPrice(newPrice);

        // Check whether the target price has been reached
        if (newPrice.compareTo(bargainActivity.getTargetPrice()) <= 0) {
            bargainActivity.setCurrentPrice(bargainActivity.getTargetPrice());
            bargainActivity.setStatus("SUCCESS");
        }

        bargainActivity.setUpdatedAt(new Date());
        bargainActivityMapper.update(bargainActivity);

        return bargainHelp;
    }

    /**
     * Get bargain activity details
     */
    public BargainActivity getBargainActivity(Long bargainId) {
        return bargainActivityMapper.selectById(bargainId);
    }

    /**
     * Get the user's bargain activities
     */
    public List<BargainActivity> getUserBargainActivities(Long userId) {
        return bargainActivityMapper.selectByUserId(userId);
    }

    /**
     * Get the help records for a bargain
     */
    public List<BargainHelp> getBargainHelpList(Long bargainId) {
        return bargainHelpMapper.selectByBargainId(bargainId);
    }

    /**
     * Scheduled task: close expired bargains
     */
    @Transactional
    public void closeExpiredBargains() {
        List<BargainActivity> expiredActivities = bargainActivityMapper.selectExpiredActivities();
        for (BargainActivity activity : expiredActivities) {
            bargainActivityMapper.updateStatus(activity.getId(), "EXPIRED");
        }
    }

    /**
     * Give up the bargain and buy at the current price
     */
    @Transactional
    public Map<String, Object> abandonAndBuy(Long bargainId, Long userId) {
        // Load the bargain
        BargainActivity bargainActivity = bargainActivityMapper.selectById(bargainId);
        if (bargainActivity == null) {
            throw new RuntimeException("Bargain not found");
        }

        // Check the caller started this bargain
        if (!bargainActivity.getUserId().equals(userId)) {
            throw new RuntimeException("Only the person who started the bargain can give it up");
        }

        // Check whether the bargain has expired
        if (bargainActivity.getExpireTime().before(new Date())) {
            bargainActivityMapper.updateStatus(bargainId, "EXPIRED");
            throw new RuntimeException("This bargain has expired (over 24 hours) and can no longer be bought");
        }

        // Check the bargain status
        if (!"ACTIVE".equals(bargainActivity.getStatus())) {
            String message = "EXPIRED".equals(bargainActivity.getStatus()) ?
                "This bargain has expired" : "This bargain has ended";
            throw new RuntimeException(message);
        }

        // Check the product is still on sale (row lock against concurrent purchases)
        Product product = productMapper.selectByIdForUpdate(bargainActivity.getProductId());
        if (product == null) {
            bargainActivityMapper.updateStatus(bargainId, "FAILED");
            throw new RuntimeException("The product has been delisted");
        }
        if (product.getStock() < 1) {
            bargainActivityMapper.updateStatus(bargainId, "FAILED");
            throw new RuntimeException("The product is sold out; bargain failed");
        }

        // Mark the bargain as given up (but bought)
        bargainActivity.setStatus("ABANDONED");
        bargainActivity.setUpdatedAt(new Date());
        bargainActivityMapper.update(bargainActivity);

        // Create the order at the current bargain price (stock is deducted only now)
        Map<String, Object> result = createBargainOrder(userId, bargainActivity.getProductId(),
                                                        bargainActivity.getCurrentPrice());

        return result;
    }

    /**
     * Buy at the successfully bargained price
     */
    @Transactional
    public Map<String, Object> buyAtBargainPrice(Long bargainId, Long userId) {
        // Load the bargain
        BargainActivity bargainActivity = bargainActivityMapper.selectById(bargainId);
        if (bargainActivity == null) {
            throw new RuntimeException("Bargain not found");
        }

        // Check the caller started this bargain
        if (!bargainActivity.getUserId().equals(userId)) {
            throw new RuntimeException("Only the person who started the bargain can buy");
        }

        // Check whether the bargain has expired (a successful bargain also has a 24-hour window)
        if (bargainActivity.getExpireTime().before(new Date())) {
            bargainActivityMapper.updateStatus(bargainId, "EXPIRED");
            throw new RuntimeException("This bargain has expired (over 24 hours) and can no longer be bought");
        }

        // The bargain must have succeeded
        if (!"SUCCESS".equals(bargainActivity.getStatus())) {
            if ("EXPIRED".equals(bargainActivity.getStatus())) {
                throw new RuntimeException("This bargain has expired and can no longer be bought");
            } else if ("COMPLETED".equals(bargainActivity.getStatus())) {
                throw new RuntimeException("This bargain has already been bought");
            } else if ("FAILED".equals(bargainActivity.getStatus())) {
                throw new RuntimeException("This bargain has failed");
            }
            throw new RuntimeException("This bargain has not succeeded and cannot be bought");
        }

        // Check the product is still on sale (row lock against concurrent purchases)
        Product product = productMapper.selectByIdForUpdate(bargainActivity.getProductId());
        if (product == null) {
            bargainActivityMapper.updateStatus(bargainId, "FAILED");
            throw new RuntimeException("The product has been delisted; bargain failed");
        }
        if (product.getStock() < 1) {
            // Someone else bought the product; mark the bargain as failed
            bargainActivityMapper.updateStatus(bargainId, "FAILED");
            throw new RuntimeException("The product is sold out; bargain failed");
        }

        // Mark the bargain as used
        bargainActivity.setStatus("COMPLETED");
        bargainActivity.setUpdatedAt(new Date());
        bargainActivityMapper.update(bargainActivity);

        // Create the order at the target (bargained) price (stock is deducted only now)
        Map<String, Object> result = createBargainOrder(userId, bargainActivity.getProductId(),
                                                        bargainActivity.getTargetPrice());

        return result;
    }

    /**
     * Helper that creates the order for a bargain
     */
    private Map<String, Object> createBargainOrder(Long userId, Long productId, BigDecimal price) {
        // Ask the order service to create an order with a custom price
        sg.edu.nus.iss.c2csectrade.entity.Order order = orderService.createBargainOrder(
            userId.intValue(),
            productId,
            1,
            price
        );

        // Return the order details
        Map<String, Object> result = new HashMap<>();
        result.put("id", order.getId());
        result.put("userId", order.getUserId());
        result.put("totalAmount", order.getTotalAmount());
        result.put("status", order.getStatus());
        result.put("createdAt", order.getCreatedAt());

        return result;
    }
}

