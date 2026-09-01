package com.vidyut.autopilot.service;

import com.vidyut.vehicle.entity.Vehicle;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VehicleChargingProfileService {

    private static final double DEFAULT_MAX_DC_POWER_KW = 50.0;
    private static final double DEFAULT_CHARGING_EFFICIENCY = 0.90;

    public ChargingProfile forVehicle(Vehicle vehicle) {
        double maxPowerKw = validPositive(vehicle.getMaxDcChargePowerKw())
                ? vehicle.getMaxDcChargePowerKw()
                : DEFAULT_MAX_DC_POWER_KW;
        double efficiency = vehicle.getChargingEfficiency() != null
                && vehicle.getChargingEfficiency() > 0
                && vehicle.getChargingEfficiency() <= 1
                ? vehicle.getChargingEfficiency()
                : DEFAULT_CHARGING_EFFICIENCY;
        return new ChargingProfile(
                maxPowerKw,
                efficiency,
                List.of(
                        new ChargingRouteOptimizer.ChargingCurvePoint(0, 60, maxPowerKw),
                        new ChargingRouteOptimizer.ChargingCurvePoint(60, 80, maxPowerKw * 0.80),
                        new ChargingRouteOptimizer.ChargingCurvePoint(80, 100, maxPowerKw * 0.40)
                ));
    }

    private boolean validPositive(Double value) {
        return value != null && Double.isFinite(value) && value > 0;
    }

    public record ChargingProfile(
            double maximumDcPowerKw,
            double efficiency,
            List<ChargingRouteOptimizer.ChargingCurvePoint> curve
    ) {
    }
}
