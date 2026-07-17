package com.smartsoc.api.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Active @Async pour les traitements hors chemin critique (ADR-008 : la
 * classification IA court APRÈS la réponse au webhook d'ingestion).
 * Exécuteur : le ThreadPoolTaskExecutor auto-configuré par Spring Boot
 * (spring.task.execution.*).
 */
@Configuration(proxyBeanMethods = false)
@EnableAsync
public class AsyncConfig {
}
