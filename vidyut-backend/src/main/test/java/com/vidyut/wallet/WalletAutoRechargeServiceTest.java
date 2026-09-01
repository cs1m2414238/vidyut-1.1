package com.vidyut.wallet;

import com.vidyut.common.exception.BadRequestException;
import com.vidyut.common.exception.ResourceNotFoundException;
import com.vidyut.vehicle.entity.Vehicle;
import com.vidyut.vehicle.repository.VehicleRepository;
import com.vidyut.wallet.dto.AutoRechargeRuleRequest;
import com.vidyut.wallet.dto.AutoRechargeRuleResponse;
import com.vidyut.wallet.dto.WalletResponse;
import com.vidyut.wallet.entity.TransactionType;
import com.vidyut.wallet.repository.VehicleAutoRechargeRuleRepository;
import com.vidyut.wallet.service.WalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class WalletAutoRechargeServiceTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private VehicleRepository vehicleRepository;

    @Autowired
    private VehicleAutoRechargeRuleRepository ruleRepository;

    @Test
    void autoRechargeTriggersForTheVehicleBeforeChargingPayment() {
        long userId = 4201L;
        Vehicle vehicle = saveVehicle(userId, "UP32QA4201");
        AutoRechargeRuleResponse rule = walletService.saveAutoRechargeRule(userId,
                ruleRequest(vehicle.getId(), true));

        walletService.deductBalance(userId, 900.0, vehicle.getId(), "QA charging payment");
        WalletResponse wallet = walletService.getWalletByUserId(userId);

        assertThat(rule.isEnabled()).isTrue();
        assertThat(wallet.getBalance()).isEqualTo(100.0);
        assertThat(wallet.getRecentTransactions())
                .extracting(transaction -> transaction.getType())
                .contains(TransactionType.AUTO_RECHARGE, TransactionType.CHARGING_PAYMENT);
        assertThat(wallet.getRecentTransactions())
                .filteredOn(transaction -> transaction.getType() == TransactionType.AUTO_RECHARGE)
                .singleElement()
                .satisfies(transaction -> {
                    assertThat(transaction.getVehicleId()).isEqualTo(vehicle.getId());
                    assertThat(transaction.getAmount()).isEqualTo(1000.0);
                });
        assertThat(ruleRepository.findByUserIdAndVehicle_Id(userId, vehicle.getId()))
                .get()
                .extracting(savedRule -> savedRule.getLastTriggeredAt())
                .isNotNull();
    }

    @Test
    void disabledRuleDoesNotTopUpAnInsufficientWallet() {
        long userId = 4202L;
        Vehicle vehicle = saveVehicle(userId, "UP32QA4202");
        walletService.saveAutoRechargeRule(userId, ruleRequest(vehicle.getId(), false));

        assertThatThrownBy(() -> walletService.deductBalance(
                userId, 2000.0, vehicle.getId(), "QA charging payment"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Insufficient wallet balance");
    }

    @Test
    void ruleCannotBeAttachedToAnotherUsersVehicle() {
        Vehicle anotherUsersVehicle = saveVehicle(4203L, "UP32QA4203");

        assertThatThrownBy(() -> walletService.saveAutoRechargeRule(
                9999L, ruleRequest(anotherUsersVehicle.getId(), true)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Vehicle not found for this account");
    }

    private Vehicle saveVehicle(Long userId, String registrationNumber) {
        return vehicleRepository.save(Vehicle.builder()
                .userId(userId)
                .makeAndModel("Tata Nexon EV Max")
                .registrationNumber(registrationNumber)
                .batteryCapacity("40.5 kWh")
                .connectorType("CCS2")
                .build());
    }

    private AutoRechargeRuleRequest ruleRequest(Long vehicleId, boolean enabled) {
        return AutoRechargeRuleRequest.builder()
                .vehicleId(vehicleId)
                .enabled(enabled)
                .balanceThreshold(500.0)
                .rechargeAmount(1000.0)
                .paymentMethod("UPI ending 9832")
                .build();
    }
}
