package com.promorunner.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

@Service
public class MailService {

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;
    private final String mailFrom;

    public MailService(
            JavaMailSender mailSender,
            TemplateEngine templateEngine,
            @Value("${spring.mail.from:noreply@localhost}") String mailFrom) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
        this.mailFrom = mailFrom;
    }

    public String render(String templateName, Map<String, Object> variables) {
        Context context = new Context(Locale.ENGLISH);
        context.setVariables(variables);
        return templateEngine.process(templateName, context);
    }

    public void sendHtml(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(mailFrom);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new IllegalStateException("Failed to send email to " + to, e);
        }
    }
}
