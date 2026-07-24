package com.smartsoc.infrastructure.persistence;

import com.smartsoc.infrastructure.persistence.incidents.IncidentReferenceGeneratorAdapter;
import com.smartsoc.infrastructure.persistence.investigations.CaseReferenceGeneratorAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Verrouille la correction du passage d'année (java:S8688) sur les
 * générateurs de références CASE/INC.
 *
 * <p>Le scénario piège : le 31 décembre à 23h30 UTC. Avec l'année lue
 * dans le fuseau de la JVM, un poste en Europe/Paris (UTC+1) serait déjà
 * en janvier suivant et produirait une référence sur la MAUVAISE année,
 * là où la CI en UTC produirait la bonne — deux références différentes
 * pour le même instant. L'horloge injectée en UTC ferme ce cas ; le test
 * le prouve en forçant en plus une locale JVM à décalage positif.
 */
@ExtendWith(MockitoExtension.class)
class ReferenceGeneratorYearRolloverTest {

    // 31 déc. 2026, 23h30 UTC : en UTC on est en 2026, en UTC+1 en 2027.
    private static final Clock NEW_YEARS_EVE_UTC =
            Clock.fixed(Instant.parse("2026-12-31T23:30:00Z"), ZoneOffset.UTC);

    private final TimeZone defaultZone = TimeZone.getDefault();

    @Mock
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void restoreZone() {
        TimeZone.setDefault(defaultZone);
    }

    @Test
    void caseReferenceUsesUtcYearRegardlessOfJvmZone() {
        // Fuseau JVM volontairement à l'est de UTC : sans l'horloge UTC,
        // Year.now() basculerait sur 2027.
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Europe/Paris")));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(42L);

        String reference = new CaseReferenceGeneratorAdapter(jdbcTemplate, NEW_YEARS_EVE_UTC)
                .nextReference();

        assertThat(reference).isEqualTo("CASE-2026-0042");
    }

    @Test
    void incidentReferenceUsesUtcYearRegardlessOfJvmZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of("Asia/Tokyo"))); // UTC+9
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(7L);

        String reference = new IncidentReferenceGeneratorAdapter(jdbcTemplate, NEW_YEARS_EVE_UTC)
                .nextReference();

        assertThat(reference).isEqualTo("INC-2026-0007");
    }
}
