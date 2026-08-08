package com.smartsoc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * {@code @EnableScheduling} : nécessaire aux synchronisations périodiques
 * des connecteurs (ADR-014) — absent jusqu'ici, rien dans le projet n'en
 * avait besoin avant.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class SmartSocApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartSocApplication.class, args);
    }
}
