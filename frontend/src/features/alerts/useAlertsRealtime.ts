import { useEffect, useState } from 'react';
import { Client } from '@stomp/stompjs';
import { useQueryClient } from '@tanstack/react-query';
import { getAccessToken } from '../../shared/api/tokens';

/**
 * Abonnement temps réel à /topic/alerts (ADR : WebSocket, jamais de polling).
 * - URL relative (/ws) : même topologie que l'API — proxy Vite en dev,
 *   nginx en conteneur ; aucun hôte codé en dur.
 * - Authentification : le JWT de session est posé sur la trame STOMP
 *   CONNECT (beforeConnect relit le token courant à chaque reconnexion,
 *   donc compatible avec la rotation du refresh).
 * - À chaque alerte reçue : invalidation du cache React Query — la file
 *   se rafraîchit en respectant filtres et pagination courants.
 */
export function useAlertsRealtime() {
  const queryClient = useQueryClient();
  const [connected, setConnected] = useState(false);

  useEffect(() => {
    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
    const client = new Client({
      brokerURL: `${protocol}://${window.location.host}/ws`,
      reconnectDelay: 5000,
      beforeConnect: () => {
        client.connectHeaders = { Authorization: `Bearer ${getAccessToken() ?? ''}` };
      },
      onConnect: () => {
        setConnected(true);
        client.subscribe('/topic/alerts', () => {
          void queryClient.invalidateQueries({ queryKey: ['alerts'] });
        });
      },
      onWebSocketClose: () => setConnected(false),
    });
    client.activate();

    return () => {
      void client.deactivate();
    };
  }, [queryClient]);

  return { connected };
}
