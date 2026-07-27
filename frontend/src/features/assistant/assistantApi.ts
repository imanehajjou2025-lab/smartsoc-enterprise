import { api } from '../../shared/api/client';

export type ChatRole = 'user' | 'assistant';

export interface ChatMessage {
  role: ChatRole;
  content: string;
}

export interface ChatContext {
  alertId?: string | null;
  incidentId?: string | null;
  summary?: string | null;
}

export interface ChatResponse {
  reply: string;
  model: string;
  generatedAt: string;
}

/**
 * > 30 s (le timeout Feign côté plateforme, ADR-008) : laisse la
 * dégradation gracieuse (503 AI_UNAVAILABLE) arriver avant un abandon
 * client, plutôt que de couper la requête nous-mêmes en premier.
 */
const CHAT_TIMEOUT_MS = 35_000;

/** Sans état côté backend : l'historique complet est renvoyé à chaque appel. */
export async function chatWithAssistant(
  messages: ChatMessage[],
  context?: ChatContext | null,
): Promise<ChatResponse> {
  const { data } = await api.post<ChatResponse>(
    '/assistant/chat',
    { messages, context: context ?? undefined },
    { timeout: CHAT_TIMEOUT_MS },
  );
  return data;
}
