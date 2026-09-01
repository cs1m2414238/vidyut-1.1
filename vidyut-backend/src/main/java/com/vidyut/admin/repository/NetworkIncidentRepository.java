package com.vidyut.admin.repository;

import com.vidyut.admin.entity.IncidentStatus;
import com.vidyut.admin.entity.NetworkIncident;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NetworkIncidentRepository extends JpaRepository<NetworkIncident, Long> {
    List<NetworkIncident> findAllByOrderByCreatedAtDesc();
    List<NetworkIncident> findByStatusInOrderByCreatedAtDesc(Collection<IncidentStatus> statuses);
    Optional<NetworkIncident> findFirstByConnectorIdAndStatusInOrderByCreatedAtDesc(Long connectorId, Collection<IncidentStatus> statuses);
}
