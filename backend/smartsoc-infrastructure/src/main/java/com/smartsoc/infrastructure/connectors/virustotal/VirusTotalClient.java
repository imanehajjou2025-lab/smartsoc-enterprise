package com.smartsoc.infrastructure.connectors.virustotal;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Client Feign de l'API REST publique VirusTotal — un endpoint par
 * type d'observable (ADR-014 phase 3), authentifié par la clé statique
 * du compte {@code smartsoc.connectors.virustotal.api-key} portée en
 * en-tête {@code x-apikey} (voir {@link VirusTotalClientConfig}).
 * N'est instancié qu'en mode live.
 */
@FeignClient(name = "virustotal-api",
        url = "${smartsoc.connectors.virustotal.url}",
        configuration = VirusTotalClientConfig.class)
public interface VirusTotalClient {

    @GetMapping("/ip_addresses/{ip}")
    VirusTotalReportResponse getIpAddress(@PathVariable("ip") String ip);

    @GetMapping("/domains/{domain}")
    VirusTotalReportResponse getDomain(@PathVariable("domain") String domain);

    /** @param urlId identifiant VirusTotal d'une URL — base64 URL-safe SANS remplissage de l'URL complète */
    @GetMapping("/urls/{urlId}")
    VirusTotalReportResponse getUrl(@PathVariable("urlId") String urlId);

    @GetMapping("/files/{hash}")
    VirusTotalReportResponse getFile(@PathVariable("hash") String hash);
}
