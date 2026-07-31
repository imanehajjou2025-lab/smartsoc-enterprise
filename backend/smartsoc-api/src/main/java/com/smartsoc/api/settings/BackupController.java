package com.smartsoc.api.settings;

import com.smartsoc.application.audit.ActorContext;
import com.smartsoc.application.settings.DatabaseBackupPort.BackupResult;
import com.smartsoc.application.settings.DatabaseBackupService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Sauvegarde de la base PostgreSQL (console Paramètres). Restreinte à
 * l'ADMIN — même périmètre que {@code /api/v1/users} (Role.ADMIN :
 * « users, settings, integrations »).
 */
@RestController
@RequestMapping("/api/v1/settings/backup")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class BackupController {

    private final DatabaseBackupService backupService;

    @GetMapping
    public ResponseEntity<byte[]> export(@AuthenticationPrincipal Jwt jwt, HttpServletRequest httpRequest) {
        ActorContext actor = new ActorContext(jwt.getSubject(),
                UUID.fromString(jwt.getClaimAsString("userId")), httpRequest.getRemoteAddr());
        BackupResult result = backupService.exportDatabase(actor);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(result.filename())
                        .build().toString())
                .body(result.content());
    }
}
