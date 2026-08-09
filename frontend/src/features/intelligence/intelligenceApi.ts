import { api } from '../../shared/api/client';
import type { Alert, PageResponse } from '../alerts/alertsApi';

export type IndicatorType =
  'IPV4' | 'IPV6' | 'DOMAIN' | 'URL' | 'MD5' | 'SHA1' | 'SHA256' | 'EMAIL';

/**
 * Statut CALCULÉ côté serveur (déduit de validUntil, jamais stocké). Le
 * front l'affiche tel quel et ne le recalcule JAMAIS : re-dériver
 * l'expiration dans le navigateur recréerait le décalage temporel que le
 * backend a précisément fermé (l'instant d'évaluation est le sien).
 */
export type IndicatorStatus = 'ACTIVE' | 'EXPIRED' | 'REVOKED';

/** Traffic Light Protocol 2.0 — contrainte de partage, pas de sécurité. */
export type TlpMarking = 'CLEAR' | 'GREEN' | 'AMBER' | 'RED';

export interface Indicator {
  id: string;
  type: IndicatorType;
  value: string;
  status: IndicatorStatus;
  confidence: number;
  tlp: TlpMarking;
  feedSource: string;
  externalId: string | null;
  description: string | null;
  tags: string[];
  firstSeen: string;
  lastSeen: string;
  validUntil: string | null;
  revoked: boolean;
  revocationReason: string | null;
  revokedAt: string | null;
}

export interface IndicatorFilters {
  type?: IndicatorType | '';
  status?: IndicatorStatus | '';
  feedSource?: string;
  tag?: string;
  minConfidence?: number | '';
  search?: string;
  page: number;
  size: number;
}

export interface DeclareIocPayload {
  type: IndicatorType;
  value: string;
  confidence: number;
  tlp?: TlpMarking;
  description?: string;
  tags?: string[];
  validUntil?: string;
}

/** Un observable cité par une alerte — même couple (type, valeur) qu'un IOC. */
export interface Observable {
  type: IndicatorType;
  value: string;
}

/** Verdict DÉRIVÉ côté serveur des compteurs VirusTotal (règle « pire cas gagne »), jamais recalculé ici. */
export type ReputationVerdict = 'MALICIOUS' | 'SUSPICIOUS' | 'HARMLESS' | 'UNDETECTED';

/**
 * Réputation d'un observable, obtenue à la demande (ADR-014 phase 3 —
 * VirusTotal, jamais sur le flux). Peut provenir du cache serveur
 * (jusqu'à 24 h) plutôt que d'un appel frais — `checkedAt` en fait foi.
 */
export interface Reputation {
  id: string;
  source: string;
  type: IndicatorType;
  value: string;
  verdict: ReputationVerdict;
  maliciousCount: number;
  suspiciousCount: number;
  harmlessCount: number;
  undetectedCount: number;
  firstCheckedAt: string;
  checkedAt: string;
}

/**
 * Enrichissement CTI d'une alerte. `observables` liste TOUT ce que
 * l'alerte cite, y compris ce qui ne correspond à aucun indicateur ;
 * `matches` ne contient que les IOC ACTIFS correspondants. `evaluatedAt`
 * est l'instant auquel l'activité a été appréciée (le serveur le fixe).
 */
export interface ThreatIntel {
  alertId: string;
  evaluatedAt: string;
  observables: Observable[];
  matches: Indicator[];
}

export async function listIocs(filters: IndicatorFilters): Promise<PageResponse<Indicator>> {
  const params = new URLSearchParams();
  if (filters.type) params.set('type', filters.type);
  if (filters.status) params.set('status', filters.status);
  if (filters.feedSource) params.set('feedSource', filters.feedSource);
  if (filters.tag) params.set('tag', filters.tag);
  if (filters.minConfidence !== '' && filters.minConfidence !== undefined) {
    params.set('minConfidence', String(filters.minConfidence));
  }
  if (filters.search) params.set('search', filters.search);
  params.set('page', String(filters.page));
  params.set('size', String(filters.size));
  const { data } = await api.get<PageResponse<Indicator>>(`/iocs?${params}`);
  return data;
}

export async function getIoc(id: string): Promise<Indicator> {
  const { data } = await api.get<Indicator>(`/iocs/${id}`);
  return data;
}

export async function declareIoc(payload: DeclareIocPayload): Promise<Indicator> {
  const { data } = await api.post<Indicator>('/iocs', payload);
  return data;
}

export async function revokeIoc(id: string, reason: string): Promise<Indicator> {
  const { data } = await api.post<Indicator>(`/iocs/${id}/revoke`, { reason });
  return data;
}

/**
 * Retro-hunt : alertes citant cet indicateur. totalElements = LE
 * compteur de corrélation (même prédicat que la liste). Disponible même
 * sur un IOC révoqué ou périmé — l'indicateur est l'entrée de la
 * requête, pas un résultat.
 */
export async function listMatchingAlerts(
  id: string,
  page: number,
  size: number,
): Promise<PageResponse<Alert>> {
  const { data } = await api.get<PageResponse<Alert>>(
    `/iocs/${id}/alerts?page=${page}&size=${size}`,
  );
  return data;
}

/**
 * Enrichissement d'une alerte (sens alerte → IOC). Strictement en
 * lecture. Une alerte sans observable répond normalement avec deux
 * listes vides — ce n'est pas une erreur, juste une alerte non enrichie.
 */
export async function getAlertThreatIntel(alertId: string): Promise<ThreatIntel> {
  const { data } = await api.get<ThreatIntel>(`/alerts/${alertId}/threat-intel`);
  return data;
}

/**
 * Interroge VirusTotal (ou sert le cache serveur) pour un observable.
 * Réservé aux rôles d'écriture côté API : chaque appel consomme un quota
 * externe réel, jamais déclenché automatiquement.
 */
export async function getReputation(type: IndicatorType, value: string): Promise<Reputation> {
  const { data } = await api.get<Reputation>(
    `/reputation?type=${encodeURIComponent(type)}&value=${encodeURIComponent(value)}`,
  );
  return data;
}
