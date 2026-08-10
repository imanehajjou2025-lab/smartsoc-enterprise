import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen } from '@testing-library/react';
import { ThemeProvider } from '@mui/material/styles';
import { Provider } from 'react-redux';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import SettingsPage from './SettingsPage';
import type {
  AboutInfo,
  AiSettings,
  ConnectorOverview,
  NotificationsSettings,
  PlatformHealth,
  SecuritySettings,
} from './settingsApi';
import type { PageResponse } from '../alerts/alertsApi';
import type { AuditLogEntry } from './settingsApi';
import type { PlatformUser } from '../admin/usersApi';
import type { Playbook } from '../soar/soarApi';
import { store } from '../../app/store';
import { theme } from '../../app/theme';

const security: SecuritySettings = {
  jwtAccessTokenExpirationMinutes: 15,
  jwtRefreshTokenExpirationDays: 7,
  ingestWebhookConfigured: true,
  aiToolsApiKeyConfigured: false,
};

const ai: AiSettings = {
  mode: 'simulation',
  classifier: { configured: false, url: 'http://localhost:8000', status: 'NOT_CONFIGURED' },
  assistant: { configured: false, url: 'http://localhost:8001', status: 'NOT_CONFIGURED' },
};

const notifications: NotificationsSettings = {
  mode: 'simulation',
  fromAddress: 'smartsoc@localhost',
  smtpConfigured: false,
};

const about: AboutInfo = {
  version: '0.1.0-SNAPSHOT',
  javaVersion: '21.0.11',
  activeProfile: 'test',
  startedAt: '2026-07-31T08:00:00Z',
  uptimeSeconds: 3600,
};

const health: PlatformHealth = { status: 'UP' };

const connectors: ConnectorOverview[] = [
  {
    type: 'WAZUH',
    status: 'CONNECTED',
    detectedVersion: null,
    capabilities: [],
    lastCheckedAt: '2026-08-08T20:00:00Z',
    lastSuccessfulSyncAt: '2026-08-08T20:00:00Z',
    lastError: null,
    lastSync: {
      startedAt: '2026-08-08T20:00:00Z',
      finishedAt: '2026-08-08T20:00:05Z',
      outcome: 'SUCCESS',
      itemsProcessed: 5,
      itemsRejected: 0,
      errorMessage: null,
    },
  },
  {
    type: 'SHUFFLE',
    status: 'CONNECTED',
    detectedVersion: null,
    capabilities: [],
    lastCheckedAt: '2026-08-10T20:00:00Z',
    lastSuccessfulSyncAt: '2026-08-10T20:00:00Z',
    lastError: null,
    lastSync: null,
  },
];

const auditPage: PageResponse<AuditLogEntry> = {
  items: [
    {
      id: 'audit-1',
      occurredAt: '2026-07-31T09:00:00Z',
      action: 'LOGIN_SUCCEEDED',
      actorUsername: 'admin',
      actorId: 'u-1',
      targetType: 'User',
      targetId: 'u-1',
      details: null,
      ipAddress: '172.18.0.1',
    },
  ],
  totalElements: 1,
  page: 0,
  size: 25,
  totalPages: 1,
};

const users: PlatformUser[] = [
  {
    id: 'u-1',
    username: 'admin',
    email: 'admin@smartsoc.local',
    fullName: 'Admin',
    role: 'ADMIN',
    enabled: true,
  },
];

const playbooksPage: PageResponse<Playbook> = {
  items: [
    {
      id: 'p-1',
      name: 'Confinement hôte',
      description: null,
      version: 1,
      steps: [],
      archived: false,
      shuffleWorkflowId: null,
      shuffleWebhookPath: null,
    },
  ],
  totalElements: 1,
  page: 0,
  size: 200,
  totalPages: 1,
};

vi.mock('./settingsApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./settingsApi')>()),
  getSecuritySettings: () => Promise.resolve(security),
  getAiSettings: () => Promise.resolve(ai),
  getNotificationsSettings: () => Promise.resolve(notifications),
  getAboutInfo: () => Promise.resolve(about),
  getPlatformHealth: () => Promise.resolve(health),
  listAuditLogs: () => Promise.resolve(auditPage),
  listConnectors: () => Promise.resolve(connectors),
}));

vi.mock('../admin/usersApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../admin/usersApi')>()),
  listUsers: () => Promise.resolve(users),
}));

vi.mock('../soar/soarApi', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../soar/soarApi')>()),
  listPlaybooks: () => Promise.resolve(playbooksPage),
}));

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <ThemeProvider theme={theme}>
          <MemoryRouter initialEntries={['/settings']}>
            <SettingsPage />
          </MemoryRouter>
        </ThemeProvider>
      </QueryClientProvider>
    </Provider>,
  );
}

describe('SettingsPage', () => {
  it('renders the overview section by default with real KPI data', async () => {
    renderPage();

    expect(await screen.findByText('Opérationnelle')).toBeInTheDocument();
    // Le mode simulation apparaît deux fois (IA + notifications), toutes
    // deux issues de vraies réponses mockées, pas d'un doublon accidentel.
    expect(screen.getAllByText('Simulation')).toHaveLength(2);
  });

  it('switches to another section when its category is selected', async () => {
    renderPage();
    await screen.findByText('Opérationnelle');

    fireEvent.click(screen.getByText('Authentification & sécurité'));

    expect(await screen.findByText("Jetons d'authentification (JWT)")).toBeInTheDocument();
    expect(screen.getByText('15 min')).toBeInTheDocument();
  });

  it('shows the real connector state for Wazuh and Shuffle, both now implemented', async () => {
    renderPage();
    await screen.findByText('Opérationnelle');

    fireEvent.click(screen.getByText('Sources de données / Connecteurs'));

    // Deux cartes "Connecté" : Wazuh et Shuffle (phase 5, connecteur d'action sans planificateur).
    expect(await screen.findAllByText('Connecté')).toHaveLength(2);
    expect(screen.getByText('Shuffle')).toBeInTheDocument();
  });
});
