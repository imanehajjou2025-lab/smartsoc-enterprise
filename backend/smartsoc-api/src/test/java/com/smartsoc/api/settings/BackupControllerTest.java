package com.smartsoc.api.settings;

import com.smartsoc.application.settings.DatabaseBackupPort.BackupResult;
import com.smartsoc.application.settings.DatabaseBackupService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Chemin de succès (200, en-têtes de téléchargement) testé directement,
 * sans passer par un vrai pg_dump (voir {@code PgDumpBackupAdapterTest}
 * et la vérification manuelle contre la stack Docker réelle) — ici c'est
 * la construction de la réponse HTTP qui est sous test, indépendamment
 * de l'outil externe.
 */
@ExtendWith(MockitoExtension.class)
class BackupControllerTest {

    @Mock
    private DatabaseBackupService backupService;

    @Mock
    private HttpServletRequest httpRequest;

    @Test
    void exportReturnsTheDumpAsADownloadableAttachment() {
        BackupController controller = new BackupController(backupService);
        BackupResult result = new BackupResult(new byte[] {1, 2, 3, 4}, "smartsoc-backup-test.dump", Instant.now());
        when(backupService.exportDatabase(any())).thenReturn(result);
        when(httpRequest.getRemoteAddr()).thenReturn("203.0.113.7");
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("admin")
                .claim("userId", UUID.randomUUID().toString())
                .build();

        ResponseEntity<byte[]> response = controller.export(jwt, httpRequest);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isEqualTo(result.content());
        assertThat(response.getHeaders().get(HttpHeaders.CONTENT_DISPOSITION))
                .anyMatch(header -> header.contains("smartsoc-backup-test.dump"));
    }
}
