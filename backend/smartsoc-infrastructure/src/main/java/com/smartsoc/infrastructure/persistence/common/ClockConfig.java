package com.smartsoc.infrastructure.persistence.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Horloge de la plateforme, en UTC et INJECTÉE.
 *
 * <p>Deux raisons, pas une seule :
 * <ul>
 *   <li><b>UTC partout</b> — tout ce qui date ou numérote raisonne dans le
 *       même fuseau, quel que soit celui de la machine. C'est la règle déjà
 *       posée pour les statistiques d'alertes (PR #46) ;</li>
 *   <li><b>testabilité</b> — une horloge figée permet d'écrire un vrai test
 *       de passage d'année. Avec {@code Year.now()} en dur, le
 *       comportement au 31 décembre à 23h30 UTC n'est vérifiable que deux
 *       jours par an, autant dire jamais.</li>
 * </ul>
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock platformClock() {
        return Clock.systemUTC();
    }
}
