package com.smartsoc.api.mitre;

import com.smartsoc.TestcontainersConfiguration;
import com.smartsoc.domain.alerts.Alert;
import com.smartsoc.domain.alerts.AlertRepository;
import com.smartsoc.domain.alerts.Severity;
import com.smartsoc.domain.common.PageQuery;
import com.smartsoc.domain.common.PageResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Corrélation technique → alertes (retro-hunt MITRE) sur PostgreSQL réel :
 * les alertes citant une technique sont retrouvées, le total est le
 * compteur de la liste, et — preuve mesurée — le containment {@code @>}
 * emprunte bien l'index GIN {@code ix_alerts_mitre_techniques} de V10.
 */
@SpringBootTest(properties = "smartsoc.security.bootstrap-admin.password=IntegrationTest123!")
@Import(TestcontainersConfiguration.class)
class AlertMitreCorrelationPersistenceIntegrationTest {

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private static Alert alert(String externalId, List<String> techniques) {
        return Alert.ingest(Alert.IngestionData.builder()
                .source("wazuh")
                .externalId(externalId)
                .title("Suspicious activity")
                .description("d")
                .severity(Severity.HIGH)
                .detectedAt(Instant.now())
                .hostname("srv-01")
                .ruleId("100")
                .mitreTechniques(techniques)
                .rawPayload("{}")
                .build());
    }

    @Test
    void findsAlertsCitingATechniqueAndTheTotalEqualsTheList() {
        String run = UUID.randomUUID().toString().substring(0, 8);
        alertRepository.save(alert("mitre-a-" + run, List.of("T1059", "T1078")));
        alertRepository.save(alert("mitre-b-" + run, List.of("T1059")));
        alertRepository.save(alert("mitre-c-" + run, List.of("T1566")));

        PageResult<Alert> t1059 = alertRepository.findByMitreTechnique("T1059", PageQuery.of(0, 200));

        assertThat(t1059.items()).extracting(Alert::getExternalId)
                .contains("mitre-a-" + run, "mitre-b-" + run)
                .doesNotContain("mitre-c-" + run);
        // Le total EST le compteur de la liste : même prédicat, jamais un
        // count séparé qui pourrait diverger.
        assertThat(t1059.totalElements()).isEqualTo(t1059.items().size());
    }

    @Test
    void containmentQueryIsServedByTheGinIndex() {
        // enable_seqscan = off : si @> ne pouvait pas emprunter le GIN, le
        // plan resterait un Seq Scan (désactivé) faute d'alternative. Il
        // montre au contraire un Bitmap Index Scan sur l'index de V10 — la
        // preuve, sur le même principe que la mesure de l'index de tags CTI.
        String plan = jdbc.execute((ConnectionCallback<String>) connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.execute("SET enable_seqscan = off");
                StringBuilder builder = new StringBuilder();
                try (ResultSet rows = statement.executeQuery(
                        "EXPLAIN SELECT * FROM alerts "
                                + "WHERE mitre_techniques @> '[\"T1059\"]'::jsonb")) {
                    while (rows.next()) {
                        builder.append(rows.getString(1)).append('\n');
                    }
                }
                // Restaure la connexion avant qu'elle ne retourne au pool.
                statement.execute("RESET enable_seqscan");
                return builder.toString();
            }
        });

        assertThat(plan).contains("ix_alerts_mitre_techniques");
    }
}
