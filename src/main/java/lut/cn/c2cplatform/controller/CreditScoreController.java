package lut.cn.c2cplatform.controller;

import lut.cn.c2cplatform.dto.CreditScoreResponse;
import lut.cn.c2cplatform.service.CreditScoreService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/credit-score")
public class CreditScoreController {

    @Autowired
    private CreditScoreService creditScoreService;

    /**
     * Get a user's credit score
     */
    @GetMapping("/{userId}")
    public ResponseEntity<?> getCreditScore(@PathVariable Long userId) {
        try {
            CreditScoreResponse creditScore = creditScoreService.getUserCreditScore(userId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", creditScore);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Recalculate a credit score manually (normally triggered by the system; administrators can call this to force it)
     */
    @PostMapping("/update/{userId}")
    public ResponseEntity<?> updateCreditScore(@PathVariable Long userId) {
        try {
            creditScoreService.updateCreditScore(userId);
            CreditScoreResponse creditScore = creditScoreService.getUserCreditScore(userId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Credit score updated");
            response.put("data", creditScore);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}

