package com.vidyut.outlet.repository;

import com.vidyut.outlet.entity.OutletPricingTier;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutletPricingTierRepository extends JpaRepository<OutletPricingTier, Long> {
    List<OutletPricingTier> findByStationIdOrderByPriorityAsc(Long stationId);
}
