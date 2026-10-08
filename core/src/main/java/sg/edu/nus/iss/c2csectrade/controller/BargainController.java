package sg.edu.nus.iss.c2csectrade.controller;

import sg.edu.nus.iss.c2csectrade.entity.BargainActivity;
import sg.edu.nus.iss.c2csectrade.entity.BargainHelp;
import sg.edu.nus.iss.c2csectrade.entity.User;
import sg.edu.nus.iss.c2csectrade.mapper.UserMapper;
import sg.edu.nus.iss.c2csectrade.service.BargainService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bargain")
public class BargainController {

    @Autowired
    private BargainService bargainService;

    @Autowired
    private UserMapper userMapper;

    /**
     * Start a bargain
     */
    @PostMapping("/start")
    public ResponseEntity<?> startBargain(@RequestBody Map<String, Long> request, Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userMapper.selectByUsername(username);

            Long productId = request.get("productId");
            if (productId == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Product ID must not be empty"));
            }

            BargainActivity bargainActivity = bargainService.startBargain(user.getId(), productId);
            return ResponseEntity.ok(bargainActivity);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Help a bargain
     */
    @PostMapping("/help/{bargainId}")
    public ResponseEntity<?> helpBargain(@PathVariable Long bargainId, Authentication authentication) {
        try {
            Long helperId = null;
            String helperName = "Guest";

            if (authentication != null) {
                String username = authentication.getName();
                User user = userMapper.selectByUsername(username);
                helperId = user.getId();
                helperName = user.getDisplayName() != null ? user.getDisplayName() : user.getUsername();
            }

            BargainHelp bargainHelp = bargainService.helpBargain(bargainId, helperId, helperName);
            BargainActivity updatedActivity = bargainService.getBargainActivity(bargainId);

            Map<String, Object> result = new HashMap<>();
            result.put("help", bargainHelp);
            result.put("activity", updatedActivity);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Get bargain activity details
     */
    @GetMapping("/{bargainId}")
    public ResponseEntity<?> getBargainActivity(@PathVariable Long bargainId) {
        try {
            BargainActivity bargainActivity = bargainService.getBargainActivity(bargainId);
            if (bargainActivity == null) {
                return ResponseEntity.notFound().build();
            }

            List<BargainHelp> helpList = bargainService.getBargainHelpList(bargainId);

            Map<String, Object> result = new HashMap<>();
            result.put("activity", bargainActivity);
            result.put("helpList", helpList);

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Get the user's bargain activities
     */
    @GetMapping("/my-bargains")
    public ResponseEntity<?> getMyBargains(Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userMapper.selectByUsername(username);

            List<BargainActivity> bargainActivities = bargainService.getUserBargainActivities(user.getId());
            return ResponseEntity.ok(bargainActivities);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Check whether the user can start a bargain for a product
     */
    @GetMapping("/check/{productId}")
    public ResponseEntity<?> checkCanStart(@PathVariable Long productId, Authentication authentication) {
        try {
            if (authentication == null) {
                return ResponseEntity.ok(Map.of("canStart", true));
            }

            String username = authentication.getName();
            User user = userMapper.selectByUsername(username);

            BargainActivity existingBargain = bargainService.getBargainActivity(productId);
            boolean canStart = existingBargain == null;

            Map<String, Object> result = new HashMap<>();
            result.put("canStart", canStart);
            if (!canStart) {
                result.put("existingBargain", existingBargain);
            }

            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Give up the bargain and buy at the current price
     */
    @PostMapping("/abandon-and-buy/{bargainId}")
    public ResponseEntity<?> abandonAndBuy(@PathVariable Long bargainId, Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userMapper.selectByUsername(username);

            // Delegate giving up the bargain and buying to the service layer
            Map<String, Object> result = bargainService.abandonAndBuy(bargainId, user.getId());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }

    /**
     * Buy at the successfully bargained price
     */
    @PostMapping("/buy/{bargainId}")
    public ResponseEntity<?> buyAtBargainPrice(@PathVariable Long bargainId, Authentication authentication) {
        try {
            String username = authentication.getName();
            User user = userMapper.selectByUsername(username);

            // Delegate buying after a successful bargain to the service layer
            Map<String, Object> result = bargainService.buyAtBargainPrice(bargainId, user.getId());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        }
    }
}

