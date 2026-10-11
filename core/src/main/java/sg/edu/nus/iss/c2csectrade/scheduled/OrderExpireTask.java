package sg.edu.nus.iss.c2csectrade.scheduled;

import sg.edu.nus.iss.c2csectrade.mapper.OrderMapper;
import sg.edu.nus.iss.c2csectrade.service.RedisCacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Closes expired orders automatically
 * Runs every minute and marks overdue PENDING orders EXPIRED.
 *
 * Nothing else is undone: stock is deducted only when an order is paid, and
 * the cart is kept until payment, so an unpaid order holds neither.
 * Every Core instance runs this task; the Redis lock keeps it to one run per
 * minute, and the conditional update keeps it correct even without the lock.
 */
@Component
public class OrderExpireTask {

    private static final Logger logger = LoggerFactory.getLogger(OrderExpireTask.class);

    static final String LOCK_RESOURCE = "order-expire-task";
    // Shorter than the schedule, so a crashed holder never blocks the next run
    static final long LOCK_SECONDS = 50;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private RedisCacheService redisCacheService;

    @Scheduled(fixedRate = 60000) // Every 60 seconds
    public void closeExpiredOrders() {
        String lockId = UUID.randomUUID().toString();
        boolean locked;
        try {
            locked = redisCacheService.acquireLock(LOCK_RESOURCE, lockId, LOCK_SECONDS);
        } catch (Exception e) {
            logger.warn("Redis lock unavailable, closing expired orders without it: {}", e.getMessage());
            expireOrders();
            return;
        }
        if (!locked) {
            return; // Another instance is running it
        }
        try {
            expireOrders();
        } finally {
            try {
                redisCacheService.releaseLock(LOCK_RESOURCE, lockId);
            } catch (Exception e) {
                logger.warn("Failed to release the order expiry lock; it times out in {}s", LOCK_SECONDS);
            }
        }
    }

    private void expireOrders() {
        try {
            int closed = orderMapper.expirePendingOrders();
            if (closed > 0) {
                logger.info("Closed {} expired orders", closed);
            }
        } catch (Exception e) {
            logger.error("Expired order task failed: {}", e.getMessage(), e);
        }
    }
}
