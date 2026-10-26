package com.duoc.cursos.notification.controller;

import com.duoc.cursos.common.dto.ApiResponse;
import com.duoc.cursos.notification.dto.NotificationRequest;
import com.duoc.cursos.notification.dto.NotificationResponse;
import com.duoc.cursos.notification.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Envío de notificaciones push vía ntfy.
 *
 * Se portó desde la versión del profesor SIN el endpoint GET /topic/{email}, que devolvía el tópico
 * de cualquier email a quien lo pidiera. En ntfy el tópico es el único secreto del canal: quien lo
 * conoce puede leer y publicar en él, así que exponerlo por API abría el canal de cualquier usuario.
 * El cliente móvil no necesitaba ese endpoint: deriva el tópico localmente en NtfyTopics.kt con el
 * mismo algoritmo (SHA-256 del email, truncado).
 */
@RestController
@RequestMapping("/api/notificar")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<NotificationResponse>> enviarNotificacion(
            @Valid @RequestBody NotificationRequest request) {
        NotificationResponse response = notificationService.sendNotification(request);

        if (response.isSuccess()) {
            return ResponseEntity.ok(ApiResponse.ok(response, "Notificación enviada exitosamente"));
        }
        return ResponseEntity.badRequest().body(ApiResponse.error(response.getMessage()));
    }
}
