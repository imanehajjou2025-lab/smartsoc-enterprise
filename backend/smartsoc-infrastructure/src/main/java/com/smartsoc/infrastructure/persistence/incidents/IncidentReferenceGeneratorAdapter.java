package com.smartsoc.infrastructure.persistence.incidents;

import com.smartsoc.domain.incidents.IncidentReferenceGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Year;

/**
 * Génère {@code INC-YYYY-NNNN} à partir d'une séquence PostgreSQL : unicité
 * et monotonie garanties par la base même sous concurrence (pas de course
 * possible, contrairement à un max()+1 applicatif).
 *
 * <p><b>Année lue en UTC, explicitement</b> — même raison que pour les cas
 * d'investigation : sans fuseau, un incident escaladé le 31 décembre à
 * 23h30 UTC porterait une année différente selon le fuseau de la JVM.
 */
@Component
@RequiredArgsConstructor
public class IncidentReferenceGeneratorAdapter implements IncidentReferenceGenerator {

    private final JdbcTemplate jdbcTemplate;
    private final Clock platformClock;

    @Override
    public String nextReference() {
        Long next = jdbcTemplate.queryForObject("SELECT nextval('incident_reference_seq')", Long.class);
        return "INC-%d-%04d".formatted(Year.now(platformClock).getValue(), next);
    }
}
