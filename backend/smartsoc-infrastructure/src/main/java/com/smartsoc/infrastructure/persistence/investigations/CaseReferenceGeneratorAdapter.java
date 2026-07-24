package com.smartsoc.infrastructure.persistence.investigations;

import com.smartsoc.domain.investigations.CaseReferenceGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Year;

/**
 * Génère {@code CASE-YYYY-NNNN} à partir d'une séquence PostgreSQL : unicité
 * et monotonie garanties par la base même sous concurrence (pas de course
 * possible, contrairement à un max()+1 applicatif).
 *
 * <p><b>Année lue en UTC, explicitement.</b> {@code Year.now()} sans fuseau
 * suit celui de la JVM : un cas ouvert le 31 décembre à 23h30 UTC serait
 * référencé sur l'année suivante depuis un poste en Europe/Paris, et sur
 * l'année courante depuis la CI en UTC — deux références différentes pour
 * le même instant. C'est la même classe de défaut que le décalage des
 * statistiques d'alertes corrigé en PR #46, et la plateforme raisonne en
 * UTC partout.
 */
@Component
@RequiredArgsConstructor
public class CaseReferenceGeneratorAdapter implements CaseReferenceGenerator {

    private final JdbcTemplate jdbcTemplate;
    private final Clock platformClock;

    @Override
    public String nextReference() {
        Long next = jdbcTemplate.queryForObject("SELECT nextval('case_reference_seq')", Long.class);
        return "CASE-%d-%04d".formatted(Year.now(platformClock).getValue(), next);
    }
}
