package com.smartsoc.infrastructure.persistence.alerts;

import com.smartsoc.domain.alerts.Alert;
import com.smartsoc.domain.intelligence.Observable;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AlertJpaMapper {

    AlertJpaEntity toJpa(Alert alert);

    Alert toDomain(AlertJpaEntity entity);

    /**
     * Traversée d'un observable, dans les deux sens.
     *
     * <p>La valeur est déjà normalisée de part et d'autre — le domaine
     * l'a produite par {@code IndicatorType.normalize()}, et la
     * contrainte {@code ck_alert_observables_value_normalized} la grave
     * en base. La conversion est donc un pur transport, sans retouche :
     * re-normaliser ici créerait un second endroit où la règle pourrait
     * diverger, exactement ce qu'on cherche à éviter.
     */
    default AlertObservableEmbeddable toJpa(Observable observable) {
        return new AlertObservableEmbeddable(observable.type(), observable.value());
    }

    default Observable toDomain(AlertObservableEmbeddable embeddable) {
        return new Observable(embeddable.getType(), embeddable.getValue());
    }
}
