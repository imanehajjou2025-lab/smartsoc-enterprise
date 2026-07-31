package com.smartsoc.application.settings;

/**
 * Port d'envoi d'un e-mail de diagnostic (console Paramètres, bouton
 * « Tester l'envoi »). À la différence de {@code ReportNotifier} — qui
 * n'échoue jamais bruyamment car une notification manquée ne doit pas
 * gâcher un rapport déjà généré — ici l'échec DOIT remonter : c'est
 * l'unique but de l'action, un administrateur qui clique ce bouton veut
 * savoir si le SMTP réel fonctionne.
 */
public interface NotificationTestPort {

    /** @throws NotificationTestException si l'envoi échoue (SMTP injoignable, etc.) */
    void sendTestEmail(String recipientEmail);

    class NotificationTestException extends RuntimeException {
        public NotificationTestException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
