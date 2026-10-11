package com.unidoc.email;

import com.unidoc.config.ApplicationProperties;
import com.unidoc.notification.NotificationService;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.mail.SimpleMailMessage;

import java.net.URI;
import java.util.Objects;

/** Builds a plain-text, authenticated link; never produces HTML or file attachments. */
public class NotificationEmailFormatter {
    private final URI frontendOrigin;
    private final String from;

    public NotificationEmailFormatter(ApplicationProperties app, String from) {
        this.frontendOrigin = checkedFrontendOrigin(Objects.requireNonNull(app).frontendUrl());
        this.from = validatedAddress(from);
    }

    public SimpleMailMessage format(NotificationEmailPayload data) {
        Objects.requireNonNull(data, "Email payload is required.");
        String recipient = validatedAddress(data.recipientEmail());
        if (data.title() == null || data.title().isBlank() || data.message() == null || data.message().isBlank()) {
            throw new IllegalArgumentException("Email notification content is required.");
        }
        String title = data.title().replace('\r', ' ').replace('\n', ' ').trim();
        String path = NotificationService.safeActionPath(data.actionPath());
        if (path == null) path = "/notifications";
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(from);
        mail.setTo(recipient);
        mail.setSubject("[UniDoc] " + title);
        mail.setText(title + "\n\n" + data.message() + "\n\nOpen UniDoc to view this update:\n"
                + frontendOrigin + path + "\n\n"
                + "This is an automatic message from UniDoc. Please do not reply.");
        return mail;
    }

    public static String validatedAddress(String value) {
        if (value == null || value.isBlank() || !value.equals(value.trim()) || value.contains("\r") || value.contains("\n")) {
            throw new IllegalArgumentException("A valid notification email address is required.");
        }
        try {
            InternetAddress address = new InternetAddress(value, true);
            address.validate();
            if (!value.equals(address.getAddress()) || !value.contains("@")) {
                throw new IllegalArgumentException("A valid notification email address is required.");
            }
            return value;
        } catch (AddressException invalid) {
            throw new IllegalArgumentException("A valid notification email address is required.");
        }
    }

    private static URI checkedFrontendOrigin(String value) {
        try {
            URI uri = URI.create(value.trim());
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                    || uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || (uri.getRawPath() != null && !uri.getRawPath().isEmpty() && !uri.getRawPath().equals("/"))) {
                throw new IllegalArgumentException("The frontend origin must be a valid HTTP(S) origin.");
            }
            String raw = uri.toString();
            return URI.create(raw.endsWith("/") ? raw.substring(0, raw.length() - 1) : raw);
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("The frontend origin must be a valid HTTP(S) origin.");
        }
    }
}
