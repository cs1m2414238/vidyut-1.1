package com.vidyut.admin.service;

import com.vidyut.admin.dto.AdminDashboardResponse;
import com.vidyut.admin.dto.CompanyApprovalRequest;

public interface AdminService {
    AdminDashboardResponse getDashboardStats();
    void approveCompany(Long companyId, CompanyApprovalRequest request);
}
