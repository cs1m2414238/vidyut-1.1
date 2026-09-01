package com.vidyut.email.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;

import java.io.UnsupportedEncodingException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String senderEmail;

    public void sendEmail(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(senderEmail, "Vidyut");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException exception) {
            throw new IllegalStateException(
                    "Unable to send mail to " + to,
                    exception);

        }
    }

    public void sendVerificationCode(String to, String accountType, String code) {
        String html = """
            <div style="font-family:Arial,sans-serif;max-width:520px;margin:auto">
                    <h2>Verify your Vidyut %s email</h2>
                    <p>Your verification code is:</p>
                    <div style="font-size:32px;font-weight:bold;letter-spacing:8px;
                                padding:18px;background:#f3f7f5;text-align:center">
                        %s
                    </div>
                    <p>This code expires in 15 minutes.</p>
                    <p>If you did not request this code, you can ignore this email.</p>
                </div>
                """.formatted(accountType, code);

        sendEmail(to, "Your Vidyut " + accountType + " verification code", html);
    }

}
