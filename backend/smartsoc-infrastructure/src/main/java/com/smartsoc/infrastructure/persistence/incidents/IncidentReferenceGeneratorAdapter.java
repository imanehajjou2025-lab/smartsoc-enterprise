package com.smartsoc.infrastructure.persistence.incidents;

import com.smartsoc.domain.incidents.IncidentReferenceGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Year;

/**
 * Génère {@code INC-YYYY-NNNN} à partir d'une séquence PostgreSQL : unicité
 * et monotonie garanties par la base même sous concurrence (pas de course
 * possible, contrairement à un max()+1 applicatif).
 */
@Component
@RequiredArgsConstructor
public class IncidentReferenceGeneratorAdapter implements IncidentReferenceGenerator {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public String nextReference() {
        Long next = jdbcTemplate.queryForObject("SELECT nextval('incident_reference_seq')", Long.class);
        return "INC-%d-%04d".formatted(Year.now().getValue(), next);
    }
}
