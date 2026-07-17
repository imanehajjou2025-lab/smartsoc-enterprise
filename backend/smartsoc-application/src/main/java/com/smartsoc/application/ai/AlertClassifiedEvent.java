package com.smartsoc.application.ai;

import com.smartsoc.domain.alerts.Alert;

/** Une alerte vient de recevoir son verdict IA (score + verdict appliqués). */
public record AlertClassifiedEvent(Alert alert) {
}
