package com.smartsoc.infrastructure.connectors.wazuh;

import com.smartsoc.application.connectors.ManagerStatsPort.ManagerHealth;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * ACL de {@code GET /manager/status} — traduit la carte brute de daemons
 * en verdict de santé (ADR-014).
 *
 * <p>{@link #CRITICAL_DAEMONS} distingue les daemons dont l'arrêt casse
 * réellement la détection de ceux légitimement arrêtés sur une
 * installation mono-nœud sans fonctionnalités optionnelles activées
 * (agentless, syslog, e-mail, cluster…) — d'après l'échantillon réel
 * capturé en phase 0, où ces six-là sont arrêtés sur un Manager par
 * ailleurs parfaitement sain. Traiter leur arrêt comme une dégradation
 * aurait été un faux positif permanent.
 */
@Component
public class WazuhManagerStatsMapper {

    private static final Set<String> CRITICAL_DAEMONS = Set.of(
            "wazuh-analysisd",   // moteur de correlation
            "wazuh-remoted",     // reception des donnees agents
            "wazuh-db",
            "wazuh-execd",
            "wazuh-modulesd",
            "wazuh-apid",        // l'API elle-meme
            "wazuh-authd",       // enregistrement des agents
            "wazuh-monitord",
            "wazuh-logcollector",
            "wazuh-syscheckd");  // integrite de fichiers (FIM)

    public ManagerHealth toManagerHealth(Map<String, String> daemons) {
        if (daemons == null || daemons.isEmpty()) {
            // Reponse vide : on ne peut rien affirmer, mais on ne fabrique
            // pas non plus un etat sain sur une absence de donnee.
            return new ManagerHealth(false, List.copyOf(CRITICAL_DAEMONS));
        }
        List<String> stopped = CRITICAL_DAEMONS.stream()
                .filter(name -> !"running".equalsIgnoreCase(daemons.get(name)))
                .sorted()
                .toList();
        return new ManagerHealth(stopped.isEmpty(), stopped);
    }
}
