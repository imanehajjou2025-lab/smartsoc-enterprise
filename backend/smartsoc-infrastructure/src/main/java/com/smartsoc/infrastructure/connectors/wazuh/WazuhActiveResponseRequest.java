package com.smartsoc.infrastructure.connectors.wazuh;

import java.util.List;

/**
 * Corps de {@code PUT /active-response} — forme confirmée directement
 * contre la spec OpenAPI RÉELLE du manager SOC (jamais devinée) :
 * {@code command} doit correspondre au {@code <command name="...">}
 * enregistré côté serveur (SANS préfixe {@code !}, réservé aux scripts
 * référencés hors de toute déclaration {@code <command>} — non notre cas,
 * voir la configuration active-response réelle capturée avant tout code).
 */
public record WazuhActiveResponseRequest(String command, List<String> arguments, Alert alert) {

    public record Alert(AlertData data) {
    }

    /**
     * {@code srcip} : convention du script Wazuh par défaut {@code firewall-drop}
     * (seule commande active-response réellement configurée sur ce manager) —
     * lit l'IP à bloquer depuis ce champ précis de l'alerte transmise.
     */
    public record AlertData(String srcip) {
    }

    public static WazuhActiveResponseRequest firewallDrop(String ipAddress) {
        return new WazuhActiveResponseRequest("firewall-drop", List.of(), new Alert(new AlertData(ipAddress)));
    }
}
