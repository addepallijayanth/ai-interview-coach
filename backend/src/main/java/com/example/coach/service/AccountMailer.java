package com.example.coach.service;

import com.example.coach.config.CoachProperties;
import com.example.coach.model.User;
import com.example.coach.service.MailService.OutgoingEmail;
import jakarta.annotation.PreDestroy;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.util.HtmlUtils;

/**
 * The account emails (confirm your address, reset your password). They are sent in the background once the
 * surrounding transaction commits, so a slow SMTP server never delays sign-up and no email goes out for an
 * account that was rolled back.
 */
@Component
public class AccountMailer {

    private static final Logger log = LoggerFactory.getLogger(AccountMailer.class);

    private final MailService mail;
    private final CoachProperties.App app;
    private final CoachProperties.Security security;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public AccountMailer(MailService mail, CoachProperties properties) {
        this.mail = mail;
        this.app = properties.app();
        this.security = properties.security();
    }

    public void sendVerification(User user, String token) {
        String link = app.link("/verify-email?token=" + encode(token));
        String expiry = humanize(security.emailVerificationTtl());
        dispatch(new OutgoingEmail(user.getEmail(), "Confirm your email for AI Interview Coach",
                greeting(user) + "\n\n"
                        + "Thanks for signing up for AI Interview Coach. Confirm your email address to activate "
                        + "your account:\n\n" + link + "\n\n"
                        + "This link expires in " + expiry + ". If you didn't create an account, you can ignore "
                        + "this email.",
                html(user, "Confirm your email address",
                        "Thanks for signing up for AI Interview Coach. Confirm your email address to activate your "
                                + "account.",
                        "Confirm email", link,
                        "This link expires in " + expiry + ". If you didn't create an account, you can ignore this "
                                + "email.")));
    }

    public void sendPasswordReset(User user, String token) {
        String link = app.link("/reset-password?token=" + encode(token));
        String expiry = humanize(security.passwordResetTtl());
        dispatch(new OutgoingEmail(user.getEmail(), "Reset your AI Interview Coach password",
                greeting(user) + "\n\n"
                        + "We received a request to reset your password. Choose a new one here:\n\n" + link + "\n\n"
                        + "This link expires in " + expiry + " and works once. If you didn't ask for this, ignore "
                        + "this email: your password stays the same.",
                html(user, "Reset your password",
                        "We received a request to reset your password. Choose a new one with the button below.",
                        "Choose a new password", link,
                        "This link expires in " + expiry + " and works once. If you didn't ask for this, ignore "
                                + "this email: your password stays the same.")));
    }

    private void dispatch(OutgoingEmail email) {
        Runnable send = () -> executor.execute(() -> {
            try {
                mail.send(email);
            } catch (RuntimeException e) {
                log.warn("Could not send the '{}' email: {}", email.subject(), e.getMessage());
            }
        });
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    private static String greeting(User user) {
        return user.getFirstName() == null || user.getFirstName().isBlank() ? "Hi," : "Hi " + user.getFirstName() + ",";
    }

    /** A simple, email-client-safe layout: one column, inline styles, a single call-to-action button. */
    private static String html(User user, String heading, String intro, String button, String link, String footnote) {
        String safeLink = HtmlUtils.htmlEscape(link);
        return """
                <!doctype html>
                <html><body style="margin:0;padding:24px;background:#f1f5f9;font-family:Segoe UI,Helvetica,Arial,sans-serif;color:#0f172a">
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0"><tr><td align="center">
                    <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:520px;background:#ffffff;border-radius:12px;padding:32px">
                      <tr><td>
                        <p style="margin:0 0 8px;font-size:14px;font-weight:600;color:#4f46e5">AI Interview Coach</p>
                        <h1 style="margin:0 0 16px;font-size:22px">%s</h1>
                        <p style="margin:0 0 8px;font-size:15px;line-height:1.5">%s</p>
                        <p style="margin:0 0 24px;font-size:15px;line-height:1.5">%s</p>
                        <p style="margin:0 0 24px"><a href="%s" style="display:inline-block;background:#4f46e5;color:#ffffff;text-decoration:none;font-weight:600;padding:12px 20px;border-radius:8px">%s</a></p>
                        <p style="margin:0 0 8px;font-size:13px;color:#64748b;line-height:1.5">Button not working? Paste this link into your browser:<br><a href="%s" style="color:#4f46e5;word-break:break-all">%s</a></p>
                        <p style="margin:16px 0 0;font-size:13px;color:#64748b;line-height:1.5">%s</p>
                      </td></tr>
                    </table>
                  </td></tr></table>
                </body></html>
                """.formatted(HtmlUtils.htmlEscape(heading), HtmlUtils.htmlEscape(greeting(user)),
                HtmlUtils.htmlEscape(intro), safeLink, HtmlUtils.htmlEscape(button), safeLink, safeLink,
                HtmlUtils.htmlEscape(footnote));
    }

    private static String encode(String token) {
        return URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    /** "24 hours", "1 hour", "30 minutes". */
    static String humanize(Duration duration) {
        long minutes = duration.toMinutes();
        if (minutes >= 60 && minutes % 60 == 0) {
            long hours = minutes / 60;
            return hours + (hours == 1 ? " hour" : " hours");
        }
        return minutes + (minutes == 1 ? " minute" : " minutes");
    }

    @PreDestroy
    void shutdown() throws InterruptedException {
        executor.shutdown();
        if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
    }
}
