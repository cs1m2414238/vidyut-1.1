package com.vidyut.payment.service;

import com.vidyut.payment.dto.PaymentRequest;
import com.vidyut.payment.dto.PaymentResponse;
import com.vidyut.payment.entity.Payment;
import com.vidyut.payment.entity.PaymentStatus;
import com.vidyut.payment.repository.PaymentRepository;
import com.vidyut.wallet.service.WalletService;
import com.vidyut.booking.entity.Booking;
import com.vidyut.booking.repository.BookingRepository;
import com.vidyut.common.exception.ResourceNotFoundException;
import com.vidyut.admin.service.OperationalControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final WalletService walletService;
    private final BookingRepository bookingRepository;
    private final OperationalControlService operationalControlService;

    @Override
    public PaymentResponse processPayment(Long userId, PaymentRequest request) {
        operationalControlService.assertPaymentAllowed(userId);
        Booking booking = bookingRepository.findByIdAndUserId(request.getBookingId(), userId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found for this account"));

        walletService.deductBalance(userId, request.getAmount(), booking.getVehicleId(),
                "Charging payment for " + booking.getStationName() + " · Booking #" + booking.getId());

        Payment payment = Payment.builder()
                .userId(userId)
                .bookingId(request.getBookingId())
                .amount(request.getAmount())
                .gatewayTransactionId("TXN_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .status(PaymentStatus.SUCCESS)
                .build();

        return mapToResponse(paymentRepository.save(payment));
    }

    @Override
    public List<PaymentResponse> getPaymentsByUserId(Long userId) {
        return paymentRepository.findByUserId(userId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private PaymentResponse mapToResponse(Payment p) {
        return PaymentResponse.builder()
                .id(p.getId())
                .userId(p.getUserId())
                .bookingId(p.getBookingId())
                .amount(p.getAmount())
                .gatewayTransactionId(p.getGatewayTransactionId())
                .status(p.getStatus())
                .timestamp(p.getTimestamp())
                .build();
    }
}
