package com.vidyut.wallet;

import com.vidyut.vehicle.entity.Vehicle;
import com.vidyut.vehicle.repository.VehicleRepository;
import com.vidyut.wallet.dto.VehicleWalletResponse;
import com.vidyut.wallet.dto.VehicleWalletTopUpRequest;
import com.vidyut.wallet.service.VehicleWalletService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class VehicleWalletServiceTest {
    @Autowired private VehicleWalletService walletService;
    @Autowired private VehicleRepository vehicleRepository;

    @Test
    void eachVehicleKeepsAnIndependentBalanceAndLedger() {
        long userId = 7301L;
        Vehicle first = vehicleRepository.save(vehicle(userId, "UP32VW7301", "Tata Nexon EV"));
        Vehicle second = vehicleRepository.save(vehicle(userId, "UP32VW7302", "MG ZS EV"));

        VehicleWalletResponse firstWallet = walletService.topUp(userId, first.getId(), topUp(800));
        VehicleWalletResponse secondWallet = walletService.topUp(userId, second.getId(), topUp(300));
        walletService.deduct(userId, first.getId(), 125, 991L, "Test charging session");

        VehicleWalletResponse refreshedFirst = walletService.getWallet(userId, first.getId());
        VehicleWalletResponse refreshedSecond = walletService.getWallet(userId, second.getId());
        assertThat(firstWallet.getTagUid()).startsWith("VIDYUT-");
        assertThat(secondWallet.getTagUid()).isNotEqualTo(firstWallet.getTagUid());
        assertThat(refreshedFirst.getBalance()).isEqualTo(675);
        assertThat(refreshedSecond.getBalance()).isEqualTo(300);
        assertThat(refreshedFirst.getRecentTransactions()).hasSize(2);
        assertThat(refreshedSecond.getRecentTransactions()).hasSize(1);
    }

    private Vehicle vehicle(Long userId, String registration, String name) {
        return Vehicle.builder().userId(userId).registrationNumber(registration).makeAndModel(name)
                .batteryCapacity("40.5 kWh").connectorType("CCS2").build();
    }

    private VehicleWalletTopUpRequest topUp(double amount) {
        return VehicleWalletTopUpRequest.builder().amount(amount).paymentMethod("UPI_TOKEN")
                .paymentReference("TEST-CONFIRMED-" + amount).build();
    }
}
