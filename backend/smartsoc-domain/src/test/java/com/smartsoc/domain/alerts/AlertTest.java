package com.smartsoc.domain.alerts;

import com.smartsoc.domain.common.BusinessRuleViolationException;
import com.smartsoc.domain.intelligence.IndicatorType;
import com.smartsoc.domain.intelligence.Observable;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AlertTest {

    private static Alert.IngestionData.IngestionDataBuilder sampleData() {
        return Alert.IngestionData.builder()
                .source("Wazuh")
                .externalId("evt-42")
                .title("Brute force detected")
                .description("10 failed SSH logins")
                .severity(Severity.HIGH)
                .detectedAt(Instant.parse("2026-07-11T08:00:00Z"))
                .hostname("srv-web-01")
                .ruleId("5710")
                .mitreTechniques(List.of("T1110"))
                .rawPayload("{\"full\":\"payload\"}");
    }

    private static Alert sampleAlert() {
        return Alert.ingest(sampleData().build());
    }

    @Test
    void ingestNormalizesSourceAndStartsInNewStatus() {
        Alert alert = sampleAlert();

        assertThat(alert.getId()).isNotNull();
        assertThat(alert.getSource()).isEqualTo("wazuh");
        assertThat(alert.getStatus()).isEqualTo(AlertStatus.NEW);
        assertThat(alert.getReceivedAt()).isNotNull();
        assertThat(alert.getMitreTechniques()).containsExactly("T1110");
        assertThat(alert.getAiScore()).isNull();
        assertThat(alert.getAiVerdict()).isNull();
    }

    @Test
    void anAlertWithoutObservablesKeepsWorkingExactlyAsBefore() {
        // Rétrocompatibilité stricte du contrat d'ingestion : les
        // producteurs actuels ne déclarent pas d'observables. Liste vide,
        // jamais null, aucune régression.
        Alert alert = sampleAlert();

        assertThat(alert.getObservables()).isNotNull().isEmpty();
    }

    @Test
    void declaredObservablesAreCarriedByTheAlert() {
        // Les observables sont DÉCLARÉS par le producteur, jamais extraits
        // de rawPayload (ADR-009) : ici l'alerte cite explicitement le
        // domaine et l'IP contactés.
        Observable domaine = Observable.of(IndicatorType.DOMAIN, "evil-c2[.]com");
        Observable ip = Observable.of(IndicatorType.IPV4, "45.83.12.7");

        Alert alert = Alert.ingest(sampleData()
                .observables(List.of(domaine, ip)).build());

        assertThat(alert.getObservables()).containsExactly(domaine, ip);
        // Valeurs déjà normalisées par le type au moment de la construction.
        assertThat(alert.getObservables().getFirst().value()).isEqualTo("evil-c2.com");
    }

    @Test
    void theObservableListOfAnAlertIsImmutable() {
        // Une alerte est une pièce d'evidence : ce qu'elle cite ne se
        // réécrit pas après coup.
        Alert alert = Alert.ingest(sampleData()
                .observables(List.of(Observable.of(IndicatorType.IPV4, "45.83.12.7")))
                .build());
        List<Observable> observables = alert.getObservables();
        Observable autre = Observable.of(IndicatorType.DOMAIN, "evil.com");

        assertThatThrownBy(() -> observables.add(autre))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void immutabilityHoldsEvenForAnAlertBuiltOutsideIngest() {
        // Le chemin que le test précédent ne couvrait PAS : une alerte
        // relue depuis la base est reconstruite par le builder, avec une
        // liste ordinaire et modifiable. Sans accesseur défensif,
        // l'entité laisserait réécrire ce qu'elle est censée protéger
        // (java/internal-representation-exposure).
        List<Observable> listeModifiable =
                new ArrayList<>(List.of(Observable.of(IndicatorType.IPV4, "45.83.12.7")));
        Alert relue = Alert.ingest(sampleData().build()).toBuilder()
                .observables(listeModifiable)
                .build();

        List<Observable> exposee = relue.getObservables();
        Observable autre = Observable.of(IndicatorType.DOMAIN, "evil.com");

        assertThatThrownBy(() -> exposee.add(autre))
                .isInstanceOf(UnsupportedOperationException.class);
        // Et modifier la liste d'origine ne change rien à ce que l'alerte
        // a déjà rendu : la protection n'est pas qu'une apparence.
        assertThat(relue.getObservables()).hasSize(1);
    }

    @Test
    void anAlertBuiltWithoutObservablesNeverExposesNull() {
        // Robustesse du même accesseur : une alerte construite hors
        // ingest() peut avoir un champ null ; l'appelant doit recevoir
        // une liste vide, jamais un NullPointerException différé.
        Alert sansListe = Alert.ingest(sampleData().build()).toBuilder()
                .observables(null)
                .build();

        assertThat(sansListe.getObservables()).isNotNull().isEmpty();
    }

    @Test
    void ingestRejectsMissingMandatoryFields() {
        // Donnees construites HORS des lambdas : une seule invocation
        // susceptible de lever par assertion (Sonar S5778).
        Alert.IngestionData blankExternalId = sampleData().externalId(" ").build();
        Alert.IngestionData missingSeverity = sampleData().severity(null).build();

        assertThatThrownBy(() -> Alert.ingest(blankExternalId))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("externalId");

        assertThatThrownBy(() -> Alert.ingest(missingSeverity))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("severity");
    }

    @Test
    void lifecycleAllowsTheNominalTriageFlow() {
        Alert alert = sampleAlert();

        alert.transitionTo(AlertStatus.ACKNOWLEDGED);
        alert.transitionTo(AlertStatus.IN_PROGRESS);
        alert.transitionTo(AlertStatus.RESOLVED);

        assertThat(alert.getStatus()).isEqualTo(AlertStatus.RESOLVED);
        assertThat(alert.getStatus().isTerminal()).isTrue();
    }

    @Test
    void lifecycleRejectsIllegalTransitions() {
        Alert alert = sampleAlert();

        // NEW -> RESOLVED interdit : une alerte doit être prise en charge d'abord.
        assertThatThrownBy(() -> alert.transitionTo(AlertStatus.RESOLVED))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("NEW")
                .hasMessageContaining("RESOLVED");

        alert.transitionTo(AlertStatus.FALSE_POSITIVE);
        // Statut terminal : plus aucune transition.
        assertThatThrownBy(() -> alert.transitionTo(AlertStatus.ACKNOWLEDGED))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void aiAssessmentIsBoundedAndOptional() {
        Alert alert = sampleAlert();

        alert.applyAiAssessment(0.93, AiVerdict.TRUE_POSITIVE);
        assertThat(alert.getAiScore()).isEqualTo(0.93);
        assertThat(alert.getAiVerdict()).isEqualTo(AiVerdict.TRUE_POSITIVE);

        assertThatThrownBy(() -> alert.applyAiAssessment(1.2, AiVerdict.FALSE_POSITIVE))
                .isInstanceOf(BusinessRuleViolationException.class);
    }
}
