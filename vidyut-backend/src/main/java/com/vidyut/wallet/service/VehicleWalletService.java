package com.vidyut.wallet.service;

import com.vidyut.wallet.dto.VehicleWalletResponse;
import com.vidyut.wallet.dto.VehicleWalletTopUpRequest;

import java.util.List;

public interface VehicleWalletService {
    List<VehicleWalletResponse> getWallets(Long userId);
    VehicleWalletResponse getWallet(Long userId, Long vehicleId);
    VehicleWalletResponse topUp(Long userId, Long vehicleId, VehicleWalletTopUpRequest request);
    VehicleWalletResponse deduct(Long userId, Long vehicleId, double amount, Long bookingId, String description);
    VehicleWalletResponse refund(Long userId, Long vehicleId, double amount, Long bookingId, String description);
}
