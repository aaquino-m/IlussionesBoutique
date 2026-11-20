package com.keycode.ilussioness_backend.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends plain-text e-mails when SMTP is configured ({@code spring.mail.host}).
 * Without SMTP the message is dropped and the caller is told so; the
 * application keeps working.
 */
@Service
public class EmailService {

    private static final Logger LOG = LoggerFactory.getLogger(EmailService.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;
    private final boolean hostConfigured;

    public EmailService(
            ObjectProvider<JavaMailSender> mailSender,
            @Value("${app.mail.from:no-reply@ilussiones.com}") String from,
            @Value("${spring.mail.host:}") String host
    ) {
        this.mailSender = mailSender;
        this.from = from;
        this.hostConfigured = host != null && !host.isBlank();
    }

    public boolean isEnabled() {
        return hostConfigured && mailSender.getIfAvailable() != null;
    }

    /**
     * @return true when the message was handed to the SMTP server.
     */
    public boolean send(String to, String subject, String body) {
        JavaMailSender sender = hostConfigured ? mailSender.getIfAvailable() : null;
        if (sender == null) {
            LOG.warn("E-mail not sent: SMTP is not configured (spring.mail.host)");
            return false;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try {
            sender.send(message);
            return true;
        } catch (MailException ex) {
            LOG.warn("E-mail delivery failed ({})", ex.getClass().getSimpleName());
            return false;
        }
    }
}
