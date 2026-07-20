package com.smartsoc.domain.intelligence;

import com.smartsoc.domain.common.BusinessRuleViolationException;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La propriété qui fait vivre tout l'enrichissement : un observable et un
 * indicateur se normalisent par le MÊME code. Si ces deux chemins
 * divergeaient un jour, la corrélation retournerait « 0 IOC » sans lever
 * la moindre erreur — le pire mode de défaillance pour un SOC.
 */
class ObservableTest {

    @Test
    void normalizationIsExactlyTheOneOfTheIndicatorType() {
        // Pour chaque type, la valeur d'un observable DOIT être identique
        // à ce que produit IndicatorType.normalize() sur la même entrée.
        // Le test compare les deux chemins plutôt que de figer une chaîne :
        // si la règle évolue, les deux évoluent ensemble ou le test casse.
        record Cas(IndicatorType type, String brut) {
        }
        List<Cas> cas = List.of(
                new Cas(IndicatorType.DOMAIN, "  EVIL[.]COM. "),
                new Cas(IndicatorType.IPV4, "45.83.12[.]7"),
                new Cas(IndicatorType.IPV6, "2001:DB8::1"),
                new Cas(IndicatorType.URL, "hxxps://EVIL.com/Compte/Connexion"),
                new Cas(IndicatorType.SHA256, "A".repeat(64)),
                new Cas(IndicatorType.EMAIL, "  Contact[at]Evil[.]com "));

        for (Cas c : cas) {
            assertThat(Observable.of(c.type(), c.brut()).value())
                    .as("normalisation de %s", c.type())
                    .isEqualTo(c.type().normalize(c.brut()));
        }
    }

    @Test
    void identityIsTheWholePairTypeAndValue() {
        // La corrélation joint sur le COUPLE : deux types différents ne
        // désignent jamais le même observable, même à valeur égale.
        Observable domaine = Observable.of(IndicatorType.DOMAIN, "evil.com");
        Observable memeDomaine = Observable.of(IndicatorType.DOMAIN, "EVIL[.]COM");

        assertThat(memeDomaine).isEqualTo(domaine);
        assertThat(memeDomaine.hashCode()).isEqualTo(domaine.hashCode());
        assertThat(Observable.of(IndicatorType.URL, "http://evil.com/a"))
                .isNotEqualTo(domaine);
    }

    @Test
    void aMalformedObservableIsRejectedOnItsOwn() {
        assertThatThrownBy(() -> Observable.of(IndicatorType.SHA256, "pas-un-hash"))
                .isInstanceOf(BusinessRuleViolationException.class);
        assertThatThrownBy(() -> Observable.of(null, "evil.com"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("type");
    }

    @Test
    void tolerantParsingKeepsTheValidOnesAndNamesTheRejected() {
        // Une alerte est une pièce d'evidence : un observable mal formé ne
        // doit jamais la faire perdre. Le troisième élément prouve que le
        // traitement ne s'arrête pas au rejet.
        List<Observable.Raw> declares = List.of(
                new Observable.Raw(IndicatorType.DOMAIN, "evil-c2[.]com"),
                new Observable.Raw(IndicatorType.SHA256, "pas-un-hash"),
                new Observable.Raw(IndicatorType.IPV4, "45.83.12.7"));

        Observable.ParseResult resultat = Observable.parseTolerant(declares);

        assertThat(resultat.accepted()).containsExactly(
                Observable.of(IndicatorType.DOMAIN, "evil-c2.com"),
                Observable.of(IndicatorType.IPV4, "45.83.12.7"));
        assertThat(resultat.rejected()).hasSize(1);
        Observable.Rejection rejet = resultat.rejected().getFirst();
        assertThat(rejet.index()).isEqualTo(1);
        assertThat(rejet.type()).isEqualTo(IndicatorType.SHA256);
        assertThat(rejet.value()).isEqualTo("pas-un-hash");
        assertThat(rejet.reason()).contains("SHA256");
    }

    @Test
    void duplicatesAreMergedAndNullEntriesRejected() {
        // Deux déclarations qui se normalisent pareil désignent le même
        // observable : la table de corrélation n'a pas à les porter deux fois.
        List<Observable.Raw> declares = Arrays.asList(
                new Observable.Raw(IndicatorType.DOMAIN, "evil.com"),
                new Observable.Raw(IndicatorType.DOMAIN, "  EVIL[.]COM.  "),
                null);

        Observable.ParseResult resultat = Observable.parseTolerant(declares);

        assertThat(resultat.accepted()).hasSize(1);
        assertThat(resultat.rejected()).hasSize(1);
        assertThat(resultat.rejected().getFirst().index()).isEqualTo(2);
    }

    @Test
    void anEmptyOrAbsentDeclarationIsNotAnError() {
        // Rétrocompatibilité : un producteur qui ne déclare rien reste
        // parfaitement valide.
        assertThat(Observable.parseTolerant(null).accepted()).isEmpty();
        assertThat(Observable.parseTolerant(null).rejected()).isEmpty();
        assertThat(Observable.parseTolerant(List.of()).accepted()).isEmpty();
    }

    @Test
    void theNumberOfObservablesPerAlertIsBounded() {
        // Le domaine se borne lui-même : un producteur qui joindrait des
        // milliers d'observables ferait gonfler la table de corrélation
        // sans rien apporter à l'analyse. Le surplus est écarté, nommé,
        // et l'alerte reste ingérable.
        List<Observable.Raw> trop = new ArrayList<>();
        for (int i = 0; i < Observable.MAX_PER_ALERT + 5; i++) {
            trop.add(new Observable.Raw(IndicatorType.DOMAIN, "hote-%d.evil.com".formatted(i)));
        }

        Observable.ParseResult resultat = Observable.parseTolerant(trop);

        assertThat(resultat.accepted()).hasSize(Observable.MAX_PER_ALERT);
        assertThat(resultat.rejected()).hasSize(5);
        assertThat(resultat.rejected().getFirst().reason()).contains("At most");
    }
}
