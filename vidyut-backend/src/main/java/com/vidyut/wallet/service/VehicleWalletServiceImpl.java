package com.vidyut.wallet.service;

import com.vidyut.common.exception.BadRequestException;
import com.vidyut.common.exception.ResourceNotFoundException;
import com.vidyut.vehicle.entity.Vehicle;
import com.vidyut.vehicle.repository.VehicleRepository;
import com.vidyut.wallet.dto.*;
import com.vidyut.wallet.entity.*;
import com.vidyut.wallet.repository.*;
import com.vidyut.notification.entity.NotificationType;
import com.vidyut.notification.service.NotificationService;
import com.vidyut.admin.service.OperationalControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;

@Service
@RequiredArgsConstructor
public class VehicleWalletServiceImpl implements VehicleWalletService {
    private static final double LOW_BALANCE_THRESHOLD = 200;

    private final VehicleWalletRepository walletRepository;
    private final VehicleWalletTransactionRepository transactionRepository;
    private final VehicleRepository vehicleRepository;
    private final VehicleAutoRechargeRuleRepository autoRechargeRuleRepository;
    private final NotificationService notificationService;
    private final OperationalControlService operationalControlService;

    @Value("${vidyut.payments.demo-enabled:false}")
    private boolean demoPaymentsEnabled;

    @Override
    @Transactional
    public List<VehicleWalletResponse> getWallets(Long userId) {
        vehicleRepository.findByUserId(userId).forEach(vehicle -> getOrCreate(userId, vehicle));
        return walletRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream().map(this::map).toList();
    }

    @Override
    @Transactional
    public VehicleWalletResponse getWallet(Long userId, Long vehicleId) {
        Vehicle vehicle = ownedVehicle(userId, vehicleId);
        return map(getOrCreate(userId, vehicle));
    }

    @Override
    @Transactional
    public VehicleWalletResponse topUp(Long userId, Long vehicleId, VehicleWalletTopUpRequest request) {
        operationalControlService.assertPaymentAllowed(userId);
        if (!demoPaymentsEnabled) {
            throw new BadRequestException("A verified payment provider confirmation is required before wallet credit");
        }
        Vehicle vehicle = ownedVehicle(userId, vehicleId);
        getOrCreate(userId, vehicle);
        VehicleWallet wallet = walletRepository.findLocked(userId, vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle wallet not found"));
        String confirmedReference = "DEMO-CONFIRMED-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        applyCredit(wallet, request.getAmount(), null, TransactionType.TOP_UP,
                "Vehicle wallet top up (development payment simulator)", request.getPaymentMethod(), confirmedReference);
        return map(wallet);
    }

    @Override
    @Transactional
    public VehicleWalletResponse deduct(Long userId, Long vehicleId, double amount, Long bookingId, String description) {
        operationalControlService.assertPaymentAllowed(userId);
        if (amount <= 0) throw new BadRequestException("Payment amount must be greater than zero");
        Vehicle vehicle = ownedVehicle(userId, vehicleId);
        getOrCreate(userId, vehicle);
        VehicleWallet wallet = walletRepository.findLocked(userId, vehicleId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle wallet not found"));
        VehicleAutoRechargeRule rule = autoRechargeRuleRepository.findByUserIdAndVehicle_Id(userId, vehicleId)
                .filter(VehicleAutoRechargeRule::isEnabled).orElse(null);
        if (rule != null && wallet.getBalance() - amount < rule.getBalanceThreshold()) {
            applyCredit(wallet, rule.getRechargeAmount(), null, TransactionType.AUTO_RECHARGE,
                    "Auto-recharge for " + vehicle.getMakeAndModel(), rule.getPaymentMethod(),
                    "AUTO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            rule.setLastTriggeredAt(LocalDateTime.now());
            rule.setUpdatedAt(LocalDateTime.now());
            autoRechargeRuleRepository.save(rule);
            notificationService.sendNotification(userId, "Vehicle wallet auto-recharged",
                    "₹" + rule.getRechargeAmount() + " was added for " + vehicle.getMakeAndModel() + ".",
                    NotificationType.AUTO_RECHARGE, "vidyut://wallet");
        }
        if (wallet.getBalance() < amount) throw new BadRequestException("Insufficient vehicle wallet balance");
        double before = wallet.getBalance();
        wallet.setBalance(before - amount);
        wallet.setUpdatedAt(LocalDateTime.now());
        walletRepository.save(wallet);
        saveTransaction(wallet, -amount, before, wallet.getBalance(), bookingId,
                TransactionType.CHARGING_PAYMENT, description, null, null);
        if (wallet.getBalance() < LOW_BALANCE_THRESHOLD) {
            notificationService.sendNotification(userId, "Vehicle wallet balance is low",
                    "₹" + wallet.getBalance() + " remains for " + vehicle.getMakeAndModel() + ".",
                    NotificationType.WALLET_LOW_BALANCE, "vidyut://wallet");
        }
        return map(wallet);
    }

    @Override
    @Transactional
    public VehicleWalletResponse refund(Long userId, Long vehicleId, double amount, Long bookingId, String description) {
        if (amount <= 0) return getWallet(userId, vehicleId);
        Vehicle vehicle = ownedVehicle(userId, vehicleId);
        VehicleWallet wallet = getOrCreate(userId, vehicle);
        applyCredit(wallet, amount, bookingId, TransactionType.REFUND, description, null, null);
        return map(wallet);
    }

    private void applyCredit(VehicleWallet wallet, double amount, Long bookingId, TransactionType type,
                             String description, String paymentMethod, String paymentReference) {
        double before = wallet.getBalance();
        wallet.setBalance(before + amount);
        wallet.setUpdatedAt(LocalDateTime.now());
        walletRepository.save(wallet);
        saveTransaction(wallet, amount, before, wallet.getBalance(), bookingId, type, description,
                paymentMethod, paymentReference);
    }

    private void saveTransaction(VehicleWallet wallet, double amount, double before, double after, Long bookingId,
                                 TransactionType type, String description, String paymentMethod, String reference) {
        transactionRepository.save(VehicleWalletTransaction.builder()
                .walletId(wallet.getId()).vehicleId(wallet.getVehicle().getId()).bookingId(bookingId)
                .amount(amount).balanceBefore(before).balanceAfter(after).type(type)
                .description(description).paymentMethod(paymentMethod).paymentReference(reference).build());
    }

    private Vehicle ownedVehicle(Long userId, Long vehicleId) {
        return vehicleRepository.findByIdAndUserId(vehicleId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Vehicle not found for this account"));
    }

    private VehicleWallet getOrCreate(Long userId, Vehicle vehicle) {
        return walletRepository.findByUserIdAndVehicle_Id(userId, vehicle.getId())
                .orElseGet(() -> walletRepository.save(VehicleWallet.builder()
                        .userId(userId).vehicle(vehicle)
                        .tagUid("VIDYUT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase())
                        .build()));
    }

    private VehicleWalletResponse map(VehicleWallet wallet) {
        List<VehicleWalletTransactionResponse> transactions = transactionRepository
                .findTop50ByWalletIdOrderByTimestampDesc(wallet.getId()).stream()
                .map(transaction -> VehicleWalletTransactionResponse.builder()
                        .id(transaction.getId()).bookingId(transaction.getBookingId()).amount(transaction.getAmount())
                        .balanceBefore(transaction.getBalanceBefore()).balanceAfter(transaction.getBalanceAfter())
                        .type(transaction.getType()).description(transaction.getDescription())
                        .paymentMethod(transaction.getPaymentMethod()).paymentReference(transaction.getPaymentReference())
                        .timestamp(transaction.getTimestamp()).build())
                .toList();
        return VehicleWalletResponse.builder()
                .walletId(wallet.getId()).vehicleId(wallet.getVehicle().getId())
                .vehicleName(wallet.getVehicle().getMakeAndModel())
                .registrationNumber(wallet.getVehicle().getRegistrationNumber())
                .tagUid(wallet.getTagUid()).balance(wallet.getBalance()).active(wallet.isActive())
                .lowBalance(wallet.getBalance() < LOW_BALANCE_THRESHOLD).recentTransactions(transactions).build();
    }
}
