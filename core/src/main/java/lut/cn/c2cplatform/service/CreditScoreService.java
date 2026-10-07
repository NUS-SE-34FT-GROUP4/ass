package lut.cn.c2cplatform.service;

import lut.cn.c2cplatform.dto.CreditScoreResponse;
import lut.cn.c2cplatform.entity.CreditScore;

public interface CreditScoreService {

    /**
     * Get a user's credit score
     */
    CreditScoreResponse getUserCreditScore(Long userId);

    /**
     * Update a user's credit score (called after a review or a completed trade)
     */
    void updateCreditScore(Long userId);

    /**
     * Initialise a user's credit score
     */
    void initializeCreditScore(Long userId);
}

