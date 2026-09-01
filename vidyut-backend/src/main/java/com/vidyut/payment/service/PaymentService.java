package com.vidyut.payment.service;

import com.vidyut.payment.dto.PaymentRequest;
import com.vidyut.payment.dto.PaymentResponse;

import java.util.List;

public interface PaymentService {
    PaymentResponse processPayment(Long userId, PaymentRequest request);
    List<PaymentResponse> getPaymentsByUserId(Long userId);
}
