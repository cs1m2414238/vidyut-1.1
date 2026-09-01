package com.vidyut.wallet.dto;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VehicleWalletResponse {
    private Long walletId;
    private Long vehicleId;
    private String vehicleName;
    private String registrationNumber;
    private String tagUid;
    private double balance;
    private boolean active;
    private boolean lowBalance;
    private List<VehicleWalletTransactionResponse> recentTransactions;
}
