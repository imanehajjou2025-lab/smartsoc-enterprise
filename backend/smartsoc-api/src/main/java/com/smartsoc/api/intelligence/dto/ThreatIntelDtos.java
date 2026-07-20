package com.smartsoc.api.intelligence.dto;

import com.smartsoc.api.intelligence.dto.IocDtos.IocResponse;
import com.smartsoc.domain.intelligence.Observable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Contrats REST de l'enrichissement CTI des alertes. */
public final class ThreatIntelDtos {

    private ThreatIntelDtos() {
    }

    /**
     * Enrichissement d'une alerte.
     *
     * <p>{@code observables} liste TOUT ce que l'alerte cite, y compris
     * ce qui ne correspond à aucun indicateur : « trois observables, un
     * seul connu » se lit alors directement, ce qu'un simple décompte de
     * correspondances ne dirait pas.
     *
     * <p>Pas de champ {@code matchCount} : il serait la taille de
     * {@code matches}, donc un second endroit où se tromper.
     *
     * <p>{@code evaluatedAt} est l'instant auquel l'activité des
     * indicateurs a été appréciée — celui-là même qui a servi au filtre
     * SQL. Il est renvoyé pour que le client sache de quel moment parle
     * chaque statut affiché dans {@code matches}.
     */
    public record ThreatIntelResponse(
            UUID alertId,
            Instant evaluatedAt,
            List<Observable> observables,
            List<IocResponse> matches) {
    }
}
