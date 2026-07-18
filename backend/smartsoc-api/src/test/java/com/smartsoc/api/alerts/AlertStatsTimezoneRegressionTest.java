package com.smartsoc.api.alerts;

import com.smartsoc.TestcontainersConfiguration;
import com.smartsoc.domain.alerts.Alert;
import com.smartsoc.domain.alerts.AlertRepository;
import com.smartsoc.domain.alerts.AlertStatistics;
import com.smartsoc.domain.alerts.Severity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Non-régression du bug de fuseau horaire de la timeline des stats
 * (détecté lors d'un run local à 00:50, invisible en CI UTC) :
 * `date_trunc('day', detected_at)` sur un timestamptz est évalué dans le
 * FUSEAU DE SESSION PostgreSQL (aligné par pgJDBC sur celui de la JVM),
 * alors que la fenêtre de 7 jours est construite en UTC côté Java — une
 * alerte de fin de journée UTC glissait dans le bucket du lendemain,
 * hors fenêtre.
 *
 * Reproduction DÉTERMINISTE, indépendante de l'heure réelle :
 * - connection-init-sql force la session PostgreSQL en Europe/Paris
 *   (UTC+1 ou +2 selon la saison) pour CHAQUE connexion du pool — la CI
 *   UTC exerce donc désormais ce cas en permanence ;
 * - l'alerte témoin est détectée HIER à 23:30 UTC : toujours dans la
 *   fenêtre de 7 jours, et toujours le lendemain en heure de Paris.
 * Sans le correctif (AT TIME ZONE 'UTC'), l'alerte est comptée dans le
 * bucket d'aujourd'hui ; avec, dans celui d'hier.
 */
@SpringBootTest(properties = {
        "smartsoc.security.bootstrap-admin.password=IntegrationTest123!",
        "spring.datasource.hikari.connection-init-sql=SET TIME ZONE 'Europe/Paris'",
})
@Import(TestcontainersConfiguration.class)
class AlertStatsTimezoneRegressionTest {

    @Autowired
    private AlertRepository alertRepository;

    @Test
    void lateUtcEveningAlertLandsInItsUtcDayBucket() {
        LocalDate yesterdayUtc = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        long before = countFor(yesterdayUtc);

        alertRepository.save(Alert.ingest(Alert.IngestionData.builder()
                .source("wazuh")
                .externalId("evt-tz-" + UUID.randomUUID())
                .title("Alerte de fin de journée UTC")
                .severity(Severity.HIGH)
                .detectedAt(yesterdayUtc.atTime(LocalTime.of(23, 30)).toInstant(ZoneOffset.UTC))
                .build()));

        long after = countFor(yesterdayUtc);
        assertThat(after)
                .as("une alerte détectée hier à 23:30 UTC doit être comptée dans le "
                        + "bucket UTC d'hier, quel que soit le fuseau de session SQL")
                .isEqualTo(before + 1);
    }

    private long countFor(LocalDate day) {
        return alertRepository.statistics(7).timeline().stream()
                .filter(d -> d.date().equals(day))
                .mapToLong(AlertStatistics.DailyCount::count)
                .sum();
    }
}
