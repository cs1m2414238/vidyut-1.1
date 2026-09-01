package com.vidyut.wallet.dto;

import com.vidyut.wallet.entity.WalletTransaction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletResponse {
    private Long walletId;
    private Long userId;
    private double balance;
    private List<WalletTransaction> recentTransactions;
}
