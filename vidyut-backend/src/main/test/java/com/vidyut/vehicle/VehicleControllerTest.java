package com.vidyut.vehicle;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidyut.auth.dto.RegisterUserRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class VehicleControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void vehicleTelemetryIsPersistedAndScopedToItsOwner() throws Exception {
        String ownerToken = registerUser("Vehicle Owner");
        String otherToken = registerUser("Other Vehicle Owner");
        String registration = "UP32" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        MvcResult created = mockMvc.perform(post("/api/ev/vehicles")
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "makeAndModel":"Tata Nexon EV Max",
                                  "registrationNumber":"%s",
                                  "batteryCapacity":"40.5 kWh",
                                  "connectorType":"CCS2"
                                }
                                """.formatted(registration)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connectionStatus").value("UNKNOWN"))
                .andExpect(jsonPath("$.data.telemetrySource").value("NOT_AVAILABLE"))
                .andReturn();

        long vehicleId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(patch("/api/ev/vehicles/{id}", vehicleId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "batteryPercent":65,
                                  "remainingRangeKm":238.5,
                                  "connectionStatus":"CONNECTED",
                                  "charging":false,
                                  "bluetoothSupported":true,
                                  "androidAutoSupported":true,
                                  "appleCarPlaySupported":false,
                                  "bluetoothDeviceName":"Nexon EV",
                                  "lastChargingStation":"Green Park Station",
                                  "lastChargingAddress":"Green Park Extension, New Delhi",
                                  "lastChargedAt":"2026-08-09T08:30:00",
                                  "telemetrySource":"MANUAL"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batteryPercent").value(65))
                .andExpect(jsonPath("$.data.remainingRangeKm").value(238.5))
                .andExpect(jsonPath("$.data.connectionStatus").value("CONNECTED"))
                .andExpect(jsonPath("$.data.charging").value(false))
                .andExpect(jsonPath("$.data.bluetoothSupported").value(true))
                .andExpect(jsonPath("$.data.androidAutoSupported").value(true))
                .andExpect(jsonPath("$.data.appleCarPlaySupported").value(false))
                .andExpect(jsonPath("$.data.lastChargingStation").value("Green Park Station"))
                .andExpect(jsonPath("$.data.telemetrySource").value("MANUAL"))
                .andExpect(jsonPath("$.data.telemetryUpdatedAt").isNotEmpty());

        mockMvc.perform(get("/api/ev/vehicles/{id}", vehicleId)
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(vehicleId))
                .andExpect(jsonPath("$.data.batteryPercent").value(65))
                .andExpect(jsonPath("$.data.lastChargingAddress").value("Green Park Extension, New Delhi"));

        mockMvc.perform(patch("/api/ev/vehicles/{id}", vehicleId)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "batteryPercent":100,
                                  "remainingRangeKm":337.5,
                                  "connectionStatus":"CONNECTED",
                                  "bluetoothSupported":true,
                                  "bluetoothDeviceName":"OnePlus Nord Buds 3 Pro",
                                  "telemetrySource":"BLUETOOTH_DEMO"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.batteryPercent").value(100))
                .andExpect(jsonPath("$.data.remainingRangeKm").value(337.5))
                .andExpect(jsonPath("$.data.bluetoothDeviceName").value("OnePlus Nord Buds 3 Pro"))
                .andExpect(jsonPath("$.data.telemetrySource").value("BLUETOOTH_DEMO"));

        mockMvc.perform(get("/api/ev/vehicles")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(vehicleId));

        mockMvc.perform(get("/api/ev/vehicles/{id}", vehicleId)
                        .header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
    }

    private String registerUser(String fullName) throws Exception {
        RegisterUserRequest request = RegisterUserRequest.builder()
                .email("vehicle-" + UUID.randomUUID() + "@vidyut.test")
                .password("Password123!")
                .fullName(fullName)
                .build();
        MvcResult result = mockMvc.perform(post("/api/auth/register/user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path("data").path("token").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
