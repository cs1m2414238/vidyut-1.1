package com.vidyut.station.repository;

import com.vidyut.station.entity.ChargingStation;
import com.vidyut.station.entity.ChargerStatus;
import com.vidyut.station.entity.ConnectorType;
import com.vidyut.station.entity.StationAvailability;
import com.vidyut.station.entity.StationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChargingStationRepository extends JpaRepository<ChargingStation, Long> {
    List<ChargingStation> findByCityContainingIgnoreCase(String city);
    @Query("select s from ChargingStation s where s.hostUserId is not null "
            + "or s.operatorCompanyId is not null or s.supplierCompanyId is not null "
            + "or (:includeDemo = true and s.demoData = true)")
    List<ChargingStation> findPublishedStations(@Param("includeDemo") boolean includeDemo);
    List<ChargingStation> findByHostUserId(Long hostUserId);
    List<ChargingStation> findBySupplierCompanyId(Long supplierCompanyId);
    List<ChargingStation> findByOperatorCompanyId(Long operatorCompanyId);
    Optional<ChargingStation> findByIdAndOperatorCompanyId(Long id, Long operatorCompanyId);
    Optional<ChargingStation> findByIdAndHostUserId(Long id, Long hostUserId);
    Optional<ChargingStation> findByDemoSeedKey(String demoSeedKey);
    boolean existsByDemoSeedKey(String demoSeedKey);
    Optional<ChargingStation> findByName(String name);

    @Query("select distinct s from ChargingStation s join fetch s.connectors c "
            + "where s.id <> :stationId and s.status = :stationStatus "
            + "and s.availability <> :unavailable and s.emergencyDisabled = false "
            + "and s.latitude between :minLatitude and :maxLatitude "
            + "and s.longitude between :minLongitude and :maxLongitude "
            + "and c.type = :connectorType and c.status = :connectorStatus "
            + "and c.available = true and c.maintenanceMode = false")
    List<ChargingStation> findCompatibleAlternativeStations(
            @Param("stationId") Long stationId,
            @Param("stationStatus") StationStatus stationStatus,
            @Param("unavailable") StationAvailability unavailable,
            @Param("connectorType") ConnectorType connectorType,
            @Param("connectorStatus") ChargerStatus connectorStatus,
            @Param("minLatitude") double minLatitude,
            @Param("maxLatitude") double maxLatitude,
            @Param("minLongitude") double minLongitude,
            @Param("maxLongitude") double maxLongitude
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select distinct s from ChargingStation s left join fetch s.connectors where s.id = :id")
    Optional<ChargingStation> findLockedById(@Param("id") Long id);

    @Query("select s from ChargingStation s where s.latitude between :minLat and :maxLat " +
           "and s.longitude between :minLng and :maxLng " +
           "and (s.hostUserId is not null or s.operatorCompanyId is not null " +
           "     or s.supplierCompanyId is not null or (:includeDemo = true and s.demoData = true))")
    List<ChargingStation> findPublishedStationsWithinBounds(
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLng") double minLng,
            @Param("maxLng") double maxLng,
            @Param("includeDemo") boolean includeDemo);

    @Query("select s from ChargingStation s where s.latitude between :minLat and :maxLat " +
           "and s.longitude between :minLng and :maxLng " +
           "and (s.hostUserId is not null or s.operatorCompanyId is not null " +
           "     or s.supplierCompanyId is not null or (:includeDemo = true and s.demoData = true))")
    List<ChargingStation> findPublishedStationsWithinBounds(
            @Param("minLat") double minLat,
            @Param("maxLat") double maxLat,
            @Param("minLng") double minLng,
            @Param("maxLng") double maxLng,
            @Param("includeDemo") boolean includeDemo,
            Pageable pageable);
}
