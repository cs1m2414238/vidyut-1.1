package com.vidyut.booking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vidyut.auth.dto.RegisterUserRequest;
import com.vidyut.station.dto.StationCreateRequest;
import com.vidyut.station.dto.StationResponse;
import com.vidyut.station.entity.ConnectorType;
import com.vidyut.station.service.ChargingStationService;
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
class BookingControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ChargingStationService stationService;

    @Test
    void authenticatedBookingLifecycleIsPersistedAndScoped() throws Exception {
        String token = registerUser();
        StationResponse station = stationService.createStation(StationCreateRequest.builder()
                .name("Booking API Station " + UUID.randomUUID())
                .address("Gomti Nagar, Lucknow")
                .pricePerKwh(14.0)
                .connectorType(ConnectorType.CCS2)
                .powerKw(22.0)
                .bookingSlotMinutes(30)
                .build(), 1L);

        MvcResult created = mockMvc.perform(post("/api/ev/bookings")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stationId":%d,"durationMinutes":30}
                                """.formatted(station.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stationId").value(station.getId()))
                .andExpect(jsonPath("$.data.durationMinutes").value(30))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andReturn();
        long bookingId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        mockMvc.perform(get("/api/ev/bookings/unread-count").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(1));
        mockMvc.perform(get("/api/ev/bookings").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(bookingId));
        mockMvc.perform(patch("/api/ev/bookings/mark-seen").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/ev/bookings/unread-count").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(0));

        mockMvc.perform(post("/api/ev/bookings/{id}/cancel", bookingId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/ev/bookings/{id}", bookingId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    private String registerUser() throws Exception {
        RegisterUserRequest request = RegisterUserRequest.builder()
                .email("booking-" + UUID.randomUUID() + "@vidyut.test")
                .password("Password123!")
                .fullName("Booking Test Driver")
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
