package com.vidyut.wallet.service;

import com.vidyut.common.exception.BadRequestException;
import com.vidyut.common.exception.ResourceNotFoundException;
import com.vidyut.vehicle.entity.Vehicle;
import com.vidyut.vehicle.repository.VehicleRepository;
import com.vidyut.wallet.dto.AutoRechargeRuleRequest;
import com.vidyut.wallet.dto.AutoRechargeRuleResponse;
import com.vidyut.wallet.dto.WalletResponse;
import com.vidyut.wallet.dto.WalletTopUpRequest;
import com.vidyut.wallet.entity.EvWallet;
import com.vidyut.wallet.entity.TransactionType;
import com.vidyut.wallet.entity.VehicleAutoRechargeRule;
import com.vidyut.wallet.entity.WalletTransaction;
import com.vidyut.wallet.repository.EvWalletRepository;
import com.vidyut.wallet.repository.VehicleAutoRechargeRuleRepository;
import com.vidyut.wallet.repository.WalletTransactionRepository;
import com.vidyut.notification.entity.NotificationType;
import com.vidyut.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private static final double DEFAULT_WALLET_BALANCE = 0.0;
    private static final double LOW_BALANCE_THRESHOLD = 200.0;

    private final EvWalletRepository walletRepository;
    private final WalletTransactionRepository transactionRepository;
    private final VehicleAutoRechargeRuleRepository autoRechargeRuleRepository;
    private final VehicleRepository vehicleRepository;
    private final NotificationService notificationService;

    @Override
    @Transactional
    public WalletResponse getWalletByUserId(Long userId) {
        EvWallet wallet = getOrCreateWallet(userId);
        List<WalletTransaction> transactions = transactionRepository
                .findByWalletIdOrderByTimestampDesc(wallet.getId());
        return WalletResponse.builder()
                .walletId(wallet.getId())
                .userId(wallet.getUserId())
                .balance(wallet.getBalance())
                .recentTransactions(transactions)
                .build();
    }

    @Override
    @Transactional
    public WalletResponse topUpWallet(Long userId, WalletTopUpRequest request) {
        EvWallet wallet = getOrCreateLockedWallet(userId);
        wallet.setBalance(wallet.getBalance() + request.getAmount());
        wallet.setUpdatedAt(LocalDateTime.now());
        walletRepository.save(wallet);

        transactionRepository.save(WalletTransaction.builder()
                .walletId(wallet.getId())
                .amount(request.getAmount())
                .type(TransactionType.TOP_UP)
                .description("Top up via " + normalizedPaymentMethod(request.getPaymentMethod()))
                .build());

        return buildWalletResponse(wallet);
    }

    @Override
    @Transactional
    public void deductBalance(Long userId, double amount, String description) {
        deductBalance(userId, amount, null, description);
    }

    @Override
    @Transactional
    public void deductBalance(Long userId, double amount, Long vehicleId, String description) {
        if (amount <= 0) {
            throw new BadRequestException("Payment amount must be greater than zero");
        }

        EvWallet wallet = getOrCreateLockedWallet(userId);
        VehicleAutoRechargeRule rule = findEnabledOwnedRule(userId, vehicleId);
        double projectedBalance = wallet.getBalance() - amount;

        if (rule != null && projectedBalance < rule.getBalanceThreshold()) {
            triggerAutoRecharge(wallet, rule);
        }

        if (wallet.getBalance() < amount) {
            throw new BadRequestException("Insufficient wallet balance");
        }

        wallet.setBalance(wallet.getBalance() - amount);
        wallet.setUpdatedAt(LocalDateTime.now());
        walletRepository.save(wallet);

        transactionRepository.save(WalletTransaction.builder()
                .walletId(wallet.getId())
                .vehicleId(vehicleId)
                .amount(-amount)
                .type(TransactionType.CHARGING_PAYMENT)
                .description(description)
                .build());
        if (wallet.getBalance() < LOW_BALANCE_THRESHOLD) {
            notificationService.sendNotification(userId, "Wallet balance is low",
                    "₹" + wallet.getBalance() + " remains. Top up before your next charge.",
                    NotificationType.WALLET_LOW_BALANCE, "vidyut://wallet");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<AutoRechargeRuleResponse> getAutoRechargeRules(Long userId) {
        return autoRechargeRuleRepository.findByUserIdOrderByUpdatedAtDesc(userId)
                .stream()
                .map(this::mapRule)
                .toList();
    }

    @Override
    @Transactional
    public AutoRechargeRuleResponse saveAutoRechargeRule(Long userId, AutoRechargeRuleRequest request) {
        Vehicle vehicle = vehicleRepository.findByIdAndUserId(request.getVehicleId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found for this account"));

        VehicleAutoRechargeRule rule = autoRechargeRuleRepository
                .findByUserIdAndVehicle_Id(userId, vehicle.getId())
                .orElseGet(() -> VehicleAutoRechargeRule.builder()
                        .userId(userId)
                        .vehicle(vehicle)
                        .createdAt(LocalDateTime.now())
                        .build());

        rule.setEnabled(request.isEnabled());
        rule.setBalanceThreshold(request.getBalanceThreshold());
        rule.setRechargeAmount(request.getRechargeAmount());
        rule.setPaymentMethod(normalizedPaymentMethod(request.getPaymentMethod()));
        rule.setUpdatedAt(LocalDateTime.now());
        return mapRule(autoRechargeRuleRepository.save(rule));
    }

    @Override
    @Transactional
    public AutoRechargeRuleResponse disableAutoRechargeRule(Long userId, Long vehicleId) {
        VehicleAutoRechargeRule rule = autoRechargeRuleRepository
                .findByUserIdAndVehicle_Id(userId, vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Auto-recharge rule not found for this vehicle"));
        rule.setEnabled(false);
        rule.setUpdatedAt(LocalDateTime.now());
        return mapRule(autoRechargeRuleRepository.save(rule));
    }

    private VehicleAutoRechargeRule findEnabledOwnedRule(Long userId, Long vehicleId) {
        if (vehicleId == null) return null;
        return autoRechargeRuleRepository.findByUserIdAndVehicle_Id(userId, vehicleId)
                .filter(VehicleAutoRechargeRule::isEnabled)
                .orElse(null);
    }

    private void triggerAutoRecharge(EvWallet wallet, VehicleAutoRechargeRule rule) {
        wallet.setBalance(wallet.getBalance() + rule.getRechargeAmount());
        wallet.setUpdatedAt(LocalDateTime.now());
        walletRepository.save(wallet);

        rule.setLastTriggeredAt(LocalDateTime.now());
        rule.setUpdatedAt(LocalDateTime.now());
        autoRechargeRuleRepository.save(rule);

        transactionRepository.save(WalletTransaction.builder()
                .walletId(wallet.getId())
                .vehicleId(rule.getVehicle().getId())
                .amount(rule.getRechargeAmount())
                .type(TransactionType.AUTO_RECHARGE)
                .description("Auto-recharge for " + rule.getVehicle().getMakeAndModel()
                        + " via " + rule.getPaymentMethod())
                .build());
        notificationService.sendNotification(wallet.getUserId(), "Wallet auto-recharged",
                "₹" + rule.getRechargeAmount() + " was added for " + rule.getVehicle().getMakeAndModel() + ".",
                NotificationType.AUTO_RECHARGE, "vidyut://wallet");
    }

    private AutoRechargeRuleResponse mapRule(VehicleAutoRechargeRule rule) {
        return AutoRechargeRuleResponse.builder()
                .id(rule.getId())
                .vehicleId(rule.getVehicle().getId())
                .vehicleName(rule.getVehicle().getMakeAndModel())
                .registrationNumber(rule.getVehicle().getRegistrationNumber())
                .enabled(rule.isEnabled())
                .balanceThreshold(rule.getBalanceThreshold())
                .rechargeAmount(rule.getRechargeAmount())
                .paymentMethod(rule.getPaymentMethod())
                .lastTriggeredAt(rule.getLastTriggeredAt())
                .updatedAt(rule.getUpdatedAt())
                .build();
    }

    private WalletResponse buildWalletResponse(EvWallet wallet) {
        return WalletResponse.builder()
                .walletId(wallet.getId())
                .userId(wallet.getUserId())
                .balance(wallet.getBalance())
                .recentTransactions(transactionRepository
                        .findByWalletIdOrderByTimestampDesc(wallet.getId()))
                .build();
    }

    private EvWallet getOrCreateWallet(Long userId) {
        return walletRepository.findByUserId(userId)
                .orElseGet(() -> walletRepository.save(EvWallet.builder()
                        .userId(userId)
                        .balance(DEFAULT_WALLET_BALANCE)
                        .build()));
    }

    private EvWallet getOrCreateLockedWallet(Long userId) {
        return walletRepository.findLockedByUserId(userId)
                .orElseGet(() -> walletRepository.save(EvWallet.builder()
                        .userId(userId)
                        .balance(DEFAULT_WALLET_BALANCE)
                        .build()));
    }

    private String normalizedPaymentMethod(String paymentMethod) {
        return paymentMethod == null || paymentMethod.isBlank()
                ? "UPI"
                : paymentMethod.trim();
    }
}
