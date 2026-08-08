package com.smartsoc.domain.connectors;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.Objects;

/**
 * État courant de la relation de la plateforme à UN outil SOC (ADR-014).
 * Un seul {@code SocConnector} par {@link ConnectorType} : ce n'est pas
 * une trace d'appel (voir {@link SyncRun} pour ça), mais l'état affiché
 * à la console — « comment va ce connecteur, là, maintenant ».
 *
 * <p>{@code NOT_CONFIGURED} et {@code DISABLED} sont des états
 * VOLONTAIRES : {@link #recordFailure} ne les écrase jamais, un connecteur
 * délibérément coupé ne doit jamais avoir l'air « en panne ».
 */
@Getter
@Builder(toBuilder = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SocConnector {

    private final ConnectorType type;
    private ConnectorStatus status;
    private ConnectorDescriptor descriptor;
    private Instant lastCheckedAt;
    private Instant lastSuccessfulSyncAt;
    private String lastError;

    /** État initial d'un connecteur avant toute configuration ou sonde. */
    public static SocConnector notConfigured(ConnectorType type) {
        Objects.requireNonNull(type, "type");
        return SocConnector.builder()
                .type(type)
                .status(ConnectorStatus.NOT_CONFIGURED)
                .descriptor(ConnectorDescriptor.unknown())
                .build();
    }

    /** Une sonde ou une synchronisation a réussi : l'outil répond. */
    public void recordSuccess(Instant checkedAt, ConnectorDescriptor descriptor) {
        if (status == ConnectorStatus.DISABLED) {
            return;
        }
        this.status = ConnectorStatus.CONNECTED;
        this.lastCheckedAt = Objects.requireNonNull(checkedAt, "checkedAt");
        this.lastSuccessfulSyncAt = checkedAt;
        this.lastError = null;
        if (descriptor != null) {
            this.descriptor = descriptor;
        }
    }

    /**
     * Une sonde ou une synchronisation a échoué. N'écrase jamais un état
     * volontaire ({@code NOT_CONFIGURED}/{@code DISABLED}) : on ne sonde
     * normalement pas un connecteur dans ces états, mais un appelant
     * distrait ne doit pas pouvoir les corrompre.
     */
    public void recordFailure(Instant checkedAt, String error) {
        if (status == ConnectorStatus.NOT_CONFIGURED || status == ConnectorStatus.DISABLED) {
            return;
        }
        this.status = ConnectorStatus.DISCONNECTED;
        this.lastCheckedAt = Objects.requireNonNull(checkedAt, "checkedAt");
        this.lastError = error;
    }

    public void disable() {
        this.status = ConnectorStatus.DISABLED;
        this.lastError = null;
    }

    /** Sort de l'état désactivé pour redevenir « non configuré » — une sonde tranchera l'état réel. */
    public void enable() {
        if (status == ConnectorStatus.DISABLED) {
            this.status = ConnectorStatus.NOT_CONFIGURED;
        }
    }
}
