package com.vidyut.admin.dto;

import com.vidyut.company.entity.VerificationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyApprovalRequest {
    private VerificationStatus status;
}
