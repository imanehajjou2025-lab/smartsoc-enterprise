package com.smartsoc.domain.soar;

/** Cycle de vie d'une exécution de playbook. IN_PROGRESS est l'unique état non terminal. */
public enum ExecutionStatus {
    IN_PROGRESS,
    COMPLETED,
    CANCELLED
}
