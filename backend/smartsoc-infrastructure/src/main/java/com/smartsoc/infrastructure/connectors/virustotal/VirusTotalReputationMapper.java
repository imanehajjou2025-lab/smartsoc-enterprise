package com.smartsoc.infrastructure.connectors.virustotal;

import com.smartsoc.application.connectors.ObservableReputationPort.Lookup;
import com.smartsoc.application.connectors.SocConnectorException;
import org.springframework.stereotype.Component;

/**
 * Anti-Corruption Layer VirusTotal (ADR-014 phase 3) — seul point du
 * système qui connaît la forme {@code last_analysis_stats} de VirusTotal.
 * Vérifié contre deux échantillons réels capturés le 2026-08-09 (IP et
 * domaine) — même structure `data.attributes.last_analysis_stats` sur
 * les quatre types d'endpoints, réutilisée telle quelle pour URL/hash.
 */
@Component
public class VirusTotalReputationMapper {

    public Lookup toLookup(VirusTotalReportResponse response) {
        VirusTotalReportResponse.LastAnalysisStats stats = extractStats(response);
        return new Lookup(stats.malicious(), stats.suspicious(), stats.harmless(), stats.undetected());
    }

    private VirusTotalReportResponse.LastAnalysisStats extractStats(VirusTotalReportResponse response) {
        if (response == null || response.data() == null || response.data().attributes() == null
                || response.data().attributes().lastAnalysisStats() == null) {
            throw new SocConnectorException("VirusTotal response carried no analysis stats");
        }
        return response.data().attributes().lastAnalysisStats();
    }
}
