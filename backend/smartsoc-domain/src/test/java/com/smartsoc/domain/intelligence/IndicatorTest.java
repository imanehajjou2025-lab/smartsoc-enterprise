package com.smartsoc.domain.intelligence;

import com.smartsoc.domain.common.BusinessRuleViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Chaque piège identifié en revue de conception a ici son test de
 * non-régression : normalisation dépendante de la locale, expiration,
 * identité (type + valeur), et notation défangée. Ce sont des bugs
 * SILENCIEUX — ils ne lèvent aucune erreur, ils font juste « 0 IOC
 * corrélé » — donc seuls des tests peuvent les empêcher de revenir.
 */
class IndicatorTest {

    private final Locale defaultLocale = Locale.getDefault();

    @AfterEach
    void restoreLocale() {
        Locale.setDefault(defaultLocale);
    }

    private static Indicator.Observation.ObservationBuilder observation() {
        return Indicator.Observation.builder()
                .type(IndicatorType.IPV4)
                .value("45.83.12.7")
                .confidence(80)
                .feedSource("misp");
    }

    // ------------------------------------------------------------------
    // Cycle de vie
    // ------------------------------------------------------------------

    @Test
    void declareStartsActiveWithNormalizedIdentity() {
        Indicator ioc = Indicator.declare(observation().value("  45.83.12.7 ").build());

        assertThat(ioc.getId()).isNotNull();
        assertThat(ioc.getType()).isEqualTo(IndicatorType.IPV4);
        assertThat(ioc.getValue()).isEqualTo("45.83.12.7");
        assertThat(ioc.getFirstSeen()).isNotNull().isEqualTo(ioc.getLastSeen());
        assertThat(ioc.isRevoked()).isFalse();
        assertThat(ioc.statusAt(Instant.now())).isEqualTo(IndicatorStatus.ACTIVE);
    }

    @Test
    void declareRequiresValueSourceAndSaneConfidence() {
        assertThatThrownBy(() -> Indicator.declare(observation().value(" ").build()))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> Indicator.declare(observation().feedSource(" ").build()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("source");
        assertThatThrownBy(() -> Indicator.declare(observation().confidence(101).build()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Confidence");
    }

    @Test
    void tlpDefaultsToAmberAndIsNeverRelaxedByOmission() {
        // Un flux muet sur le TLP ne rend pas un renseignement librement
        // diffusable : le défaut est restrictif, et une ré-observation sans
        // marquage conserve celui déjà acquis.
        Indicator ioc = Indicator.declare(observation().tlp(null).build());
        assertThat(ioc.getTlp()).isEqualTo(TlpMarking.AMBER);

        Indicator restricted = Indicator.declare(observation().tlp(TlpMarking.RED).build());
        restricted.refreshFrom(observation().tlp(null).build());
        assertThat(restricted.getTlp()).isEqualTo(TlpMarking.RED);

        // Un marquage explicite, lui, fait foi.
        restricted.refreshFrom(observation().tlp(TlpMarking.GREEN).build());
        assertThat(restricted.getTlp()).isEqualTo(TlpMarking.GREEN);
    }

    // ------------------------------------------------------------------
    // PIÈGE 1 — normalisation dépendante de la locale de la JVM
    // ------------------------------------------------------------------

    @Test
    void normalizationIsLocaleIndependent() {
        // En locale turque, "I".toLowerCase() donne 'ı' (i sans point) et non
        // 'i' : sans Locale.ROOT, la clé Java divergerait du lower() SQL et
        // l'IOC ne correspondrait à aucun observable. Bug silencieux, et
        // invisible sur un poste en locale française.
        Locale.setDefault(Locale.forLanguageTag("tr"));

        // Les valeurs de ce test contiennent toutes un 'I' : c'est la seule
        // lettre sur laquelle la locale turque diverge.
        assertThat(IndicatorType.DOMAIN.normalize("EVIL.COM")).isEqualTo("evil.com");
        assertThat(IndicatorType.EMAIL.normalize("INVOICE@EVIL.COM"))
                .isEqualTo("invoice@evil.com");
        assertThat(Indicator.declare(observation()
                .type(IndicatorType.DOMAIN).value("PHISHING-LOGIN.COM").build())
                .getValue()).isEqualTo("phishing-login.com");
    }

    // ------------------------------------------------------------------
    // PIÈGE 2 — notation défangée (MISP, rapports d'analystes)
    // ------------------------------------------------------------------

    @Test
    void defangedNotationIsRefanged() {
        assertThat(IndicatorType.IPV4.normalize("45.83.12[.]7")).isEqualTo("45.83.12.7");
        assertThat(IndicatorType.DOMAIN.normalize("evil(dot)com")).isEqualTo("evil.com");
        assertThat(IndicatorType.DOMAIN.normalize("EVIL[.]COM.")).isEqualTo("evil.com");
        assertThat(IndicatorType.EMAIL.normalize("Contact[at]Evil[.]com"))
                .isEqualTo("contact@evil.com");
        assertThat(IndicatorType.URL.normalize("hxxp://evil[.]com/a"))
                .isEqualTo("http://evil.com/a");
        assertThat(IndicatorType.URL.normalize("hxxps://EVIL.com/a"))
                .isEqualTo("https://evil.com/a");
    }

    // ------------------------------------------------------------------
    // Normalisation propre à chaque type
    // ------------------------------------------------------------------

    @Test
    void hashesAreLowercasedAndLengthChecked() {
        // Les exports MISP sortent régulièrement les hash en majuscules.
        String sha256 = "E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855";
        assertThat(IndicatorType.SHA256.normalize(sha256)).isEqualTo(sha256.toLowerCase(Locale.ROOT));

        // Une longueur qui ne colle pas à l'algorithme est une erreur de
        // saisie, pas un IOC : elle n'entre pas dans le référentiel.
        assertThatThrownBy(() -> IndicatorType.SHA256.normalize("a".repeat(40)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("64-character");
        assertThatThrownBy(() -> IndicatorType.MD5.normalize("zz" + "a".repeat(30)))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void urlKeepsPathCaseButLowercasesSchemeAndHost() {
        // /Login et /login sont deux ressources : mettre le chemin en
        // minuscules fabriquerait un FAUX rapprochement.
        assertThat(IndicatorType.URL.normalize("HTTPS://Evil.COM:8443/Admin/Login?Token=AbC"))
                .isEqualTo("https://evil.com:8443/Admin/Login?Token=AbC");
        assertThatThrownBy(() -> IndicatorType.URL.normalize("evil.com/a"))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void ipv6IsCanonicalizedSoOneAddressHasOneKey() {
        // La même adresse écrite compressée ou développée doit produire la
        // MÊME clé, sinon elle ne se corrèle pas avec elle-même.
        String compressed = IndicatorType.IPV6.normalize("2001:DB8::1");
        String expanded = IndicatorType.IPV6.normalize("2001:0db8:0000:0000:0000:0000:0000:0001");
        assertThat(compressed).isEqualTo(expanded);

        assertThatThrownBy(() -> IndicatorType.IPV6.normalize("evil.com"))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void hugeDomainIsRejectedWithoutBlowingTheStack() {
        // La valeur vient d'un flux CTI externe : un nom à un millier de
        // labels ferait déborder la pile avec une expression régulière à
        // groupe répété (java:S5998). La validation se fait en boucle, et
        // deux gardes de longueur rejettent l'entrée bien avant.
        String milleLabels = "a.".repeat(1000) + "com";
        assertThatThrownBy(() -> IndicatorType.DOMAIN.normalize(milleLabels))
                .isInstanceOf(BusinessRuleViolationException.class);

        String tropLong = "a".repeat(3000) + ".com";
        assertThatThrownBy(() -> IndicatorType.DOMAIN.normalize(tropLong))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("2048");

        // Un domaine légitime, lui, passe toujours — y compris avec
        // plusieurs niveaux et un point final.
        assertThat(IndicatorType.DOMAIN.normalize("mail.corp.evil.co.uk."))
                .isEqualTo("mail.corp.evil.co.uk");
        // Un label ne peut ni commencer ni finir par un tiret.
        assertThatThrownBy(() -> IndicatorType.DOMAIN.normalize("-evil.com"))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> IndicatorType.DOMAIN.normalize("evil-.com"))
                .isInstanceOf(BusinessRuleViolationException.class);
        // Un nom sans point n'est pas un domaine.
        assertThatThrownBy(() -> IndicatorType.DOMAIN.normalize("localhost"))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void malformedValuesAreRejectedPerType() {
        assertThatThrownBy(() -> IndicatorType.IPV4.normalize("999.1.1.1"))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> IndicatorType.DOMAIN.normalize("45.83.12.7"))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> IndicatorType.EMAIL.normalize("not-an-email"))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    // ------------------------------------------------------------------
    // PIÈGE 3 — identité = type + valeur, jamais modifiée par un upsert
    // ------------------------------------------------------------------

    @Test
    void refreshUpdatesMetadataButNeverIdentity() {
        Instant firstSeen = Instant.now().minus(10, ChronoUnit.DAYS);
        Indicator ioc = Indicator.declare(observation()
                .observedAt(firstSeen).confidence(60).tags(Set.of("Botnet")).build());
        UUID id = ioc.getId();

        Instant later = Instant.now();
        ioc.refreshFrom(observation()
                .observedAt(later).confidence(95).feedSource("MISP")
                .tags(Set.of("c2", "botnet")).build());

        // Identité intacte…
        assertThat(ioc.getId()).isEqualTo(id);
        assertThat(ioc.getType()).isEqualTo(IndicatorType.IPV4);
        assertThat(ioc.getValue()).isEqualTo("45.83.12.7");
        // …métadonnées rafraîchies.
        assertThat(ioc.getConfidence()).isEqualTo(95);
        assertThat(ioc.getFeedSource()).isEqualTo("misp");
        assertThat(ioc.getTags()).containsExactlyInAnyOrder("botnet", "c2");
        assertThat(ioc.getFirstSeen()).isEqualTo(firstSeen);
        assertThat(ioc.getLastSeen()).isEqualTo(later);
    }

    @Test
    void refreshRejectsAnObservationOfAnotherIndicator() {
        Indicator ioc = Indicator.declare(observation().build());

        // Même valeur, autre type → autre indicateur : la corrélation se
        // fait sur le COUPLE (type, valeur), jamais sur la valeur seule.
        assertThatThrownBy(() -> ioc.refreshFrom(observation()
                .type(IndicatorType.SHA256).value("a".repeat(64)).build()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("identity");

        assertThatThrownBy(() -> ioc.refreshFrom(observation().value("8.8.8.8").build()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("identity");
    }

    // ------------------------------------------------------------------
    // PIÈGE 4 — expiration déduite, jamais un statut stocké qui traîne
    // ------------------------------------------------------------------

    @Test
    void expiryIsDerivedFromValidityWindow() {
        Instant now = Instant.now();
        Indicator expired = Indicator.declare(observation()
                .validUntil(now.minus(1, ChronoUnit.HOURS)).build());

        assertThat(expired.statusAt(now)).isEqualTo(IndicatorStatus.EXPIRED);
        assertThat(expired.isActionableAt(now)).isFalse();
        // Le MÊME indicateur était actionnable avant sa date de péremption :
        // l'état dépend de l'instant regardé, pas d'une colonne à maintenir.
        assertThat(expired.isActionableAt(now.minus(2, ChronoUnit.HOURS))).isTrue();

        Indicator eternal = Indicator.declare(observation().validUntil(null).build());
        assertThat(eternal.isActionableAt(now.plus(3650, ChronoUnit.DAYS))).isTrue();
    }

    @Test
    void revocationSurvivesFeedRefresh() {
        // Un flux qui continue de pousser un IOC déclaré faux positif ne
        // doit pas le ressusciter : la décision d'analyste prime.
        Indicator ioc = Indicator.declare(observation().build());
        ioc.revoke("Plage d'IP du proxy interne — faux positif confirmé");

        ioc.refreshFrom(observation().confidence(100).build());

        assertThat(ioc.isRevoked()).isTrue();
        assertThat(ioc.statusAt(Instant.now())).isEqualTo(IndicatorStatus.REVOKED);
        assertThat(ioc.isActionableAt(Instant.now())).isFalse();
        assertThat(ioc.getRevocationReason()).contains("faux positif");
        assertThat(ioc.getRevokedAt()).isNotNull();
    }

    @Test
    void revocationRequiresAReasonAndHappensOnce() {
        Indicator ioc = Indicator.declare(observation().build());

        assertThatThrownBy(() -> ioc.revoke("  "))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("reason");

        ioc.revoke("Faux positif");
        assertThatThrownBy(() -> ioc.revoke("Encore"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("already revoked");
    }
}
