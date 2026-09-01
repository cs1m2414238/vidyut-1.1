package com.vidyut.station.repository;

import com.vidyut.station.entity.ChargingConnector;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChargingConnectorRepository extends JpaRepository<ChargingConnector, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from ChargingConnector c where c.id = :id")
    Optional<ChargingConnector> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);
    List<ChargingConnector> findByStation_HostUserId(Long ownerAccountId);
    Optional<ChargingConnector> findByIdAndStation_HostUserId(Long id, Long ownerAccountId);
    Optional<ChargingConnector> findByChargerCode(String chargerCode);
    boolean existsByChargerCodeIgnoreCase(String chargerCode);
}
