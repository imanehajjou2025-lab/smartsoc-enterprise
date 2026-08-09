package com.smartsoc.api.reputation;

import com.smartsoc.TestcontainersConfiguration;
import com.smartsoc.domain.intelligence.IndicatorType;
import com.smartsoc.domain.reputation.ObservableReputation;
import com.smartsoc.domain.reputation.ObservableReputationRepository;
import com.smartsoc.domain.reputation.ReputationVerdict;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Persistance du contexte reputation (V19, ADR-014 phase 3) sur un vrai
 * PostgreSQL : identité (source, type, valeur), rafraîchissement en
 * place, contrainte d'unicité.
 */
@SpringBootTest(properties = "smartsoc.security.bootstrap-admin.password=IntegrationTest123!")
@Import(TestcontainersConfiguration.class)
class ReputationPersistenceIntegrationTest {

    @Autowired
    private ObservableReputationRepository repository;

    private static ObservableReputation.Lookup.LookupBuilder lookup(String value) {
        return ObservableReputation.Lookup.builder()
                .source("virustotal")
                .type(IndicatorType.IPV4)
                .value(value)
                .maliciousCount(0).suspiciousCount(0).harmlessCount(53).undetectedCount(38)
                .checkedAt(Instant.now());
    }

    @Test
    void savesAndReloadsAReputationByIdentity() {
        String value = "10.0." + Math.abs(UUID.randomUUID().hashCode() % 250) + ".1";
        repository.save(ObservableReputation.record(lookup(value).build()));

        ObservableReputation reloaded = repository.findByIdentity("virustotal", IndicatorType.IPV4, value)
                .orElseThrow();
        assertThat(reloaded.getVerdict()).isEqualTo(ReputationVerdict.HARMLESS);
        assertThat(reloaded.getHarmlessCount()).isEqualTo(53);
    }

    @Test
    void refreshingAndSavingTheSameInstanceUpdatesRatherThanDuplicates() {
        String value = "10.1." + Math.abs(UUID.randomUUID().hashCode() % 250) + ".1";
        ObservableReputation reputation = ObservableReputation.record(lookup(value).build());
        repository.save(reputation);

        reputation.refreshFrom(lookup(value).maliciousCount(5).build());
        repository.save(reputation);

        ObservableReputation reloaded = repository.findByIdentity("virustotal", IndicatorType.IPV4, value)
                .orElseThrow();
        assertThat(reloaded.getId()).isEqualTo(reputation.getId());
        assertThat(reloaded.getVerdict()).isEqualTo(ReputationVerdict.MALICIOUS);
        assertThat(reloaded.getMaliciousCount()).isEqualTo(5);
    }

    @Test
    void duplicateIdentityIsRejectedByTheUniqueIndex() {
        String value = "10.2." + Math.abs(UUID.randomUUID().hashCode() % 250) + ".1";
        repository.save(ObservableReputation.record(lookup(value).build()));

        assertThatThrownBy(() -> repository.save(ObservableReputation.record(lookup(value).build())))
                .isInstanceOf(RuntimeException.class);
    }
}
