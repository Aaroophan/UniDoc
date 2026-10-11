package com.unidoc.email;

import com.unidoc.config.ApplicationProperties;
import com.unidoc.config.EmailProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@ConditionalOnProperty(prefix = "app.email", name = "enabled", havingValue = "true")
public class SmtpEmailSender implements NotificationEmailSender {
    private final JavaMailSender mailSender;
    private final NotificationEmailFormatter formatter;

    public SmtpEmailSender(JavaMailSender mailSender,
                           EmailProperties email, ApplicationProperties app,
                           @Value("${spring.mail.host:}") String host,
                           @Value("${spring.mail.port:0}") int port,
                           @Value("${spring.mail.username:}") String username,
                           @Value("${spring.mail.password:}") String password,
                           @Value("${spring.mail.properties.mail.smtp.auth:true}") boolean authentication) {
        this.mailSender = Objects.requireNonNull(mailSender);
        if (host == null || host.isBlank() || port < 1 || port > 65535
                || (authentication && (username == null || username.isBlank() || password == null || password.isBlank()))) {
            // Never disclose SMTP credentials (or supplied values) in configuration errors.
            throw new IllegalStateException("Email is enabled but SMTP host, port, or authentication credentials are invalid.");
        }
        try {
            this.formatter = new NotificationEmailFormatter(app, email.from());
        } catch (IllegalArgumentException invalid) {
            throw new IllegalStateException("Email is enabled but the sender address or frontend origin is invalid.");
        }
    }

    @Override
    public void send(NotificationEmailPayload notification) {
        SimpleMailMessage mail = formatter.format(notification);
        mailSender.send(mail);
    }
}
