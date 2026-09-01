package com.vidyut.wallet.service;

import com.vidyut.wallet.dto.WalletResponse;
import com.vidyut.wallet.dto.WalletTopUpRequest;
import com.vidyut.wallet.dto.AutoRechargeRuleRequest;
import com.vidyut.wallet.dto.AutoRechargeRuleResponse;

import java.util.List;

public interface WalletService {
    WalletResponse getWalletByUserId(Long userId);
    WalletResponse topUpWallet(Long userId, WalletTopUpRequest request);
    void deductBalance(Long userId, double amount, String description);
    void deductBalance(Long userId, double amount, Long vehicleId, String description);
    List<AutoRechargeRuleResponse> getAutoRechargeRules(Long userId);
    AutoRechargeRuleResponse saveAutoRechargeRule(Long userId, AutoRechargeRuleRequest request);
    AutoRechargeRuleResponse disableAutoRechargeRule(Long userId, Long vehicleId);
}
