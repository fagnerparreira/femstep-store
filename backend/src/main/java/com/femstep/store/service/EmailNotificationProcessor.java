package com.femstep.store.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
@ConditionalOnProperty(name = "notifications.email.enabled", havingValue = "true")
public class EmailNotificationProcessor {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailNotificationProcessor.class);

    private final NotificationOutboxStore outboxStore;
    private final JavaMailSender mailSender;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final String from;
    private final String publicUrl;

    public EmailNotificationProcessor(
            NotificationOutboxStore outboxStore,
            JavaMailSender mailSender,
            JdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper,
            @Value("${notifications.email.from}") String from,
            @Value("${notifications.public-url}") String publicUrl
    ) {
        this.outboxStore = outboxStore;
        this.mailSender = mailSender;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.from = from;
        this.publicUrl = publicUrl.replaceAll("/+$", "");
    }

    @Scheduled(fixedDelayString = "${notifications.poll-interval-ms:5000}")
    public void processPendingMessages() {
        List<NotificationOutboxStore.OutboxMessage> messages = outboxStore.claimBatch();
        for (NotificationOutboxStore.OutboxMessage message : messages) {
            try {
                EmailContent email = content(message);
                send(email);
                outboxStore.markSent(message.id(), message.aggregateType().equals("USER"));
            } catch (Exception exception) {
                outboxStore.markFailed(message, exception.getMessage());
                LOGGER.warn(
                        "Falha ao enviar notificação id={} tipo={} tentativa={}",
                        message.id(),
                        message.eventType(),
                        message.attempts()
                );
            }
        }
    }

    private EmailContent content(NotificationOutboxStore.OutboxMessage message) {
        if (message.aggregateType().equals("USER")) {
            return accountContent(message);
        }
        if (message.aggregateType().equals("ORDER")) {
            return orderContent(message);
        }
        throw new IllegalArgumentException("Tipo de notificação não suportado");
    }

    private EmailContent accountContent(NotificationOutboxStore.OutboxMessage message) {
        JsonNode payload = json(message.payloadJson());
        String email = requiredText(payload, "email");
        String token = URLEncoder.encode(
                requiredText(payload, "token"),
                StandardCharsets.UTF_8
        );
        return switch (message.eventType()) {
            case "EMAIL_VERIFICATION_REQUESTED" -> new EmailContent(
                    email,
                    "Confirme seu e-mail na MOVAZU",
                    """
                    Olá!

                    Confirme seu e-mail usando este link válido por 24 horas:
                    %s/auth/verify-email?token=%s

                    Se você não criou esta conta, ignore esta mensagem.
                    """.formatted(publicUrl, token)
            );
            case "PASSWORD_RESET_REQUESTED" -> new EmailContent(
                    email,
                    "Redefinição de senha da MOVAZU",
                    """
                    Recebemos uma solicitação para redefinir sua senha.

                    Use este link de uso único, válido por 30 minutos:
                    %s/auth/reset-password?token=%s

                    Se você não solicitou a alteração, ignore esta mensagem.
                    """.formatted(publicUrl, token)
            );
            default -> throw new IllegalArgumentException("Evento de conta não suportado");
        };
    }

    private EmailContent orderContent(NotificationOutboxStore.OutboxMessage message) {
        OrderRecipient order = jdbcTemplate.query("""
                        SELECT customer_email, customer_name, public_number
                        FROM orders
                        WHERE id = ?
                        """,
                (rs, rowNum) -> new OrderRecipient(
                        rs.getString("customer_email"),
                        rs.getString("customer_name"),
                        rs.getString("public_number")
                ),
                Long.valueOf(message.aggregateId())
        ).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Pedido da notificação não encontrado"));

        String statusText = switch (message.eventType()) {
            case "ORDER_CREATED" -> "Seu pedido foi criado e estamos aguardando a confirmação do pagamento.";
            case "PAYMENT_APPROVED" -> "Seu pagamento foi aprovado e o pedido seguirá para preparação.";
            case "ORDER_SHIPPED" -> "Seu pedido foi enviado.";
            default -> throw new IllegalArgumentException("Evento de pedido não suportado");
        };
        return new EmailContent(
                order.email(),
                "Atualização do pedido " + order.orderNumber(),
                """
                Olá, %s!

                %s

                Acompanhe os detalhes em:
                %s/account/orders/%s
                """.formatted(order.name(), statusText, publicUrl, order.orderNumber())
        );
    }

    private void send(EmailContent content) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(content.to());
        message.setSubject(content.subject());
        message.setText(content.body());
        mailSender.send(message);
    }

    private JsonNode json(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Payload da notificação é inválido");
        }
    }

    private String requiredText(JsonNode payload, String field) {
        String value = payload.path(field).asText("");
        if (value.isBlank()) {
            throw new IllegalArgumentException("Payload da notificação está incompleto");
        }
        return value;
    }

    private record EmailContent(String to, String subject, String body) {
    }

    private record OrderRecipient(String email, String name, String orderNumber) {
    }
}
