package com.duoc.cursos.notification.service;

import com.duoc.cursos.notification.config.NotificationProperties;
import com.duoc.cursos.notification.dto.NotificationRequest;
import com.duoc.cursos.notification.dto.NotificationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationProperties properties;
    private final RestClient restClient;

    /**
     * El RestClient se construye en el constructor, no como inicializador de campo: los inicializadores
     * corren antes de que se asignen los parámetros del constructor, por lo que ahí "properties" sería null
     * y los timeouts no podrían leerse.
     */
    public NotificationService(NotificationProperties properties) {
        this.properties = properties;

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeout()))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(properties.getReadTimeout()));

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE)
                .build();
    }

    public NotificationResponse sendNotification(NotificationRequest request) {
        String topic = generateTopic(request.getEmail());
        try {
            // 1. El tópico identifica al destinatario; la URL es baseUrl/topic.
            String ntfyUrl = properties.getBaseUrl() + "/" + topic;

            // 2. Enviar la petición POST al servidor ntfy.
            String ntfyResponse = restClient.post()
                    .uri(ntfyUrl)
                    .headers(headers -> {
                        if (request.getTitle() != null && !request.getTitle().isBlank()) {
                            headers.set("Title", request.getTitle());
                        }
                        if (request.getTags() != null && !request.getTags().isBlank()) {
                            headers.set("Tags", request.getTags());
                        }
                        if (request.getPriority() != null) {
                            headers.set("Priority", String.valueOf(request.getPriority()));
                        }
                    })
                    .body(request.getMessage())
                    .retrieve()
                    .body(String.class);

            return NotificationResponse.builder()
                    .success(true)
                    .topic(topic)
                    .message("Notificación enviada con éxito")
                    .ntfyResponse(ntfyResponse)
                    .build();

        } catch (RuntimeException e) {
            // El detalle va al log, no a la respuesta: el mensaje de la excepción puede incluir la URL
            // completa del tópico, que es el único secreto del canal ntfy.
            log.error("No se pudo enviar la notificación a ntfy ({}): {}",
                    e.getClass().getSimpleName(), e.getMessage());
            return NotificationResponse.builder()
                    .success(false)
                    .topic(topic)
                    .message("No se pudo enviar la notificación")
                    .build();
        }
    }

    public String generateTopic(String email) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(email.trim().toLowerCase().getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return properties.getTopicPrefix() + hexString.toString().substring(0, properties.getTopicHashLength());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
