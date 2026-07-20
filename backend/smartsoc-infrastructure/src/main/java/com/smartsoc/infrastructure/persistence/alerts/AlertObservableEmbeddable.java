package com.smartsoc.infrastructure.persistence.alerts;

import com.smartsoc.domain.intelligence.IndicatorType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Projection de persistance d'un observable. {@code @Embeddable} et non
 * entité : un observable n'a pas d'identité propre, il appartient à son
 * alerte et disparaît avec elle.
 *
 * <p>{@code equals}/{@code hashCode} portent sur le couple complet —
 * indispensable ici : la collection est un {@code Set}, et c'est cette
 * égalité qui dédoublonne côté Java comme la clé primaire dédoublonne
 * côté base.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class AlertObservableEmbeddable {

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 10)
    private IndicatorType type;

    @Column(name = "value", nullable = false, length = 2048)
    private String value;
}
