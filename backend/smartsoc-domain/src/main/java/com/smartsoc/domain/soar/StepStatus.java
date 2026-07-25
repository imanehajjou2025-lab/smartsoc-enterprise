package com.smartsoc.domain.soar;

/**
 * Avancement d'une étape d'exécution — même liberté que
 * {@code CaseTask.Status} (une checklist se coche et se décoche
 * librement), avec {@code SKIPPED} en plus : une procédure générale ne
 * s'applique pas forcément intégralement à chaque incident précis.
 */
public enum StepStatus {
    TODO,
    IN_PROGRESS,
    DONE,
    SKIPPED
}
