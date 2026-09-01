package com.vidyut.payment.controller;

import com.vidyut.common.response.ApiResponse;
import com.vidyut.common.util.CurrentUserUtil;
import com.vidyut.payment.dto.PaymentRequest;
import com.vidyut.payment.dto.PaymentResponse;
import com.vidyut.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ev/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final CurrentUserUtil currentUser;

    @PostMapping
    public ResponseEntity<ApiResponse<PaymentResponse>> processPayment(@Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Payment processed successfully",
                paymentService.processPayment(currentUser.getCurrentAccountId(), request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> getPayments() {
        return ResponseEntity.ok(ApiResponse.success(
                paymentService.getPaymentsByUserId(currentUser.getCurrentAccountId())));
    }
}
