package com.example.coach.service;

import com.example.coach.config.CoachProperties;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Sends email over SMTP (any provider: Mailpit locally, or SendGrid, Mailgun, Amazon SES, Gmail and so on).
 * When no SMTP host is configured, emails are written to the log instead so local sign-up still works.
 */
@Service
public class MailService {

    /** A ready-to-send email with a plain-text and an HTML body. */
    public record OutgoingEmail(String to, String subject, String text, String html) {}

    private static final Logger log = LoggerFactory.getLogger(MailService.class);

    private final JavaMailSenderImpl sender;
    private final String from;

    public MailService(CoachProperties properties) {
        CoachProperties.Mail mail = properties.mail();
        this.from = mail.from();
        this.sender = mail.enabled() ? smtp(mail) : null;
        if (sender == null) {
            log.warn("MAIL_HOST is not set: emails are not sent. Confirmation and password-reset links are "
                    + "written to this log instead.");
        }
    }

    public boolean isEnabled() {
        return sender != null;
    }

    /** Sends synchronously; throws {@link MailSendException} if the SMTP server rejects the message. */
    public void send(OutgoingEmail email) {
        if (sender == null) {
            log.info("Email not sent (no SMTP server configured).\nTo: {}\nSubject: {}\n\n{}", email.to(),
                    email.subject(), email.text());
            return;
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(email.to());
            helper.setSubject(email.subject());
            helper.setText(email.text(), email.html());
            sender.send(message);
            log.info("Sent '{}' email", email.subject());
        } catch (MessagingException e) {
            throw new MailSendException("Could not build the email", e);
        }
    }

    private static JavaMailSenderImpl smtp(CoachProperties.Mail mail) {
        JavaMailSenderImpl smtp = new JavaMailSenderImpl();
        smtp.setHost(mail.host());
        smtp.setPort(mail.port());
        boolean authenticate = mail.username() != null && !mail.username().isBlank();
        if (authenticate) {
            smtp.setUsername(mail.username());
            smtp.setPassword(mail.password());
        }
        smtp.setDefaultEncoding("UTF-8");
        Properties props = smtp.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", String.valueOf(authenticate));
        props.put("mail.smtp.starttls.enable", String.valueOf(mail.starttls()));
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "10000");
        props.put("mail.smtp.writetimeout", "10000");
        return smtp;
    }
}
