package com.vidyut.company.repository;

import com.vidyut.company.entity.CompanyMaintenanceTicket;
import com.vidyut.company.entity.MaintenanceTicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CompanyMaintenanceTicketRepository extends JpaRepository<CompanyMaintenanceTicket, Long> {
    List<CompanyMaintenanceTicket> findAllByOrderByUpdatedAtDesc();
    List<CompanyMaintenanceTicket> findByCompanyIdOrderByUpdatedAtDesc(Long companyId);
    Optional<CompanyMaintenanceTicket> findByIdAndCompanyId(Long id, Long companyId);
    boolean existsByCompanyIdAndChargerIdAndStatusIn(Long companyId, Long chargerId,
                                                     Collection<MaintenanceTicketStatus> statuses);
    Optional<CompanyMaintenanceTicket> findFirstByCompanyIdAndChargerIdAndStatusInOrderByUpdatedAtDesc(Long companyId, Long chargerId,
                                                                                                      Collection<MaintenanceTicketStatus> statuses);
}
