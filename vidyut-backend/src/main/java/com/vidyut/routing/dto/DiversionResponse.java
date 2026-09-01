package com.vidyut.routing.dto;

import com.vidyut.booking.dto.BookingResponse;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiversionResponse {
    private BookingResponse cancelledBooking;
    private BookingResponse replacementBooking;
    private String message;
}
