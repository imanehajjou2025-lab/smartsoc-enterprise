import type { ChatContext } from './assistantApi';

const CONTEXT_STORAGE_KEY = 'smartsoc.assistant.context';

/**
 * Contexte transmis par un tiroir métier (alerte/incident) avant de
 * naviguer vers `/assistant` — sessionStorage plutôt qu'un paramètre
 * d'URL : évite de faire transiter un résumé potentiellement long dans
 * la barre d'adresse, cohérent avec "le frontend est propriétaire de la
 * conversation" (contrat docs/integration/ai-assistant-api.yaml).
 */
export function setAssistantContext(context: ChatContext): void {
  sessionStorage.setItem(CONTEXT_STORAGE_KEY, JSON.stringify(context));
}

export function readAssistantContext(): ChatContext | null {
  const raw = sessionStorage.getItem(CONTEXT_STORAGE_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as ChatContext;
  } catch {
    return null;
  }
}

export function clearAssistantContext(): void {
  sessionStorage.removeItem(CONTEXT_STORAGE_KEY);
}

/** Résumés construits UNIQUEMENT à partir de champs réels déjà chargés — jamais inventés. */
export function summarizeAlertForAssistant(alert: {
  title: string;
  severity: string;
  hostname: string | null;
  mitreTechniques: string[];
}): string {
  const parts = [`Alerte : ${alert.title}`, `Sévérité : ${alert.severity}`];
  if (alert.hostname) {
    parts.push(`Hôte : ${alert.hostname}`);
  }
  if (alert.mitreTechniques.length > 0) {
    parts.push(`Techniques MITRE : ${alert.mitreTechniques.join(', ')}`);
  }
  return parts.join(' — ');
}

export function summarizeIncidentForAssistant(incident: {
  reference: string;
  title: string;
  severity: string;
  status: string;
}): string {
  return (
    `Incident ${incident.reference} : ${incident.title} — ` +
    `Sévérité : ${incident.severity} — Statut : ${incident.status}`
  );
}
