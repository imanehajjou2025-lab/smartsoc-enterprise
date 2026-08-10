import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { Provider } from 'react-redux';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import DashboardPage from './DashboardPage';
import type { DashboardOverview } from './useDashboardData';
import { store } from '../../app/store';
import { ThemeModeProvider } from '../../app/ThemeModeProvider';

const overview: DashboardOverview = {
  alertStats: {
    total: 12,
    bySeverity: { CRITICAL: 3, HIGH: 4, MEDIUM: 3, LOW: 1, INFO: 1 },
    byStatus: { NEW: 7, ACKNOWLEDGED: 2, RESOLVED: 2, FALSE_POSITIVE: 1 },
    bySource: { wazuh: 8, suricata: 3, shuffle: 1 },
    timeline: Array.from({ length: 7 }, (_, i) => ({ date: `2026-07-0${i + 5}`, count: i })),
  },
  recentAlerts: [
    {
      id: 'a-1',
      source: 'wazuh',
      externalId: 'e-1',
      title: 'Brute force SSH',
      description: null,
      severity: 'CRITICAL',
      status: 'NEW',
      detectedAt: '2026-07-26T10:00:00Z',
      receivedAt: '2026-07-26T10:00:01Z',
      hostname: 'srv-web-01',
      ruleId: null,
      mitreTechniques: [],
      observables: [],
      rawPayload: null,
      aiScore: null,
      aiVerdict: null,
      aiZone: null,
      aiHardOverride: false,
      aiJustifications: [],
    },
  ],
  incidents: [
    {
      id: 'i-1',
      reference: 'INC-2026-0001',
      title: 'Compromission srv-web-01',
      description: null,
      severity: 'HIGH',
      status: 'OPEN',
      assigneeUsername: null,
      openedAt: '2026-07-26T09:00:00Z',
    },
  ],
  incidentsTotal: 1,
  assets: [
    {
      id: 'as-1',
      hostname: 'srv-web-01',
      displayName: 'srv-web-01',
      type: 'SERVER',
      criticality: 'CRITICAL',
      exposure: 'INTERNET_FACING',
      ipAddress: null,
      owner: null,
      description: null,
      status: 'ACTIVE',
      registeredAt: '2026-07-01T00:00:00Z',
      decommissionedAt: null,
      operatingSystem: null,
      lastSeenAt: null,
      hardwareSummary: null,
      externalSource: null,
      agentConnectionStatus: null,
    },
  ],
  assetsTotal: 1,
  playbooksActiveTotal: 2,
  hunts: [],
  huntsTotal: 0,
  iocs: [],
  iocsActiveTotal: 0,
  techniques: [
    {
      attackId: 'T1110',
      subTechnique: false,
      parentId: null,
      name: 'Brute Force',
      description: null,
      url: null,
      tactics: ['credential-access'],
      deprecated: false,
      attackVersion: null,
    },
  ],
  coverage: [{ attackId: 'T1110', alertCount: 6 }],
  recentReports: [],
};

vi.mock('./useDashboardData', () => ({
  useDashboardData: () => ({ data: overview, isPending: false, isError: false, error: null }),
}));

// jsdom : ni WebSocket ni canvas — temps réel et graphes simulés.
vi.mock('../alerts/useAlertsRealtime', () => ({
  useAlertsRealtime: () => ({ connected: true }),
}));
vi.mock('../../shared/components/EChart', () => ({
  default: () => <div data-testid="echart" />,
}));

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <Provider store={store}>
      <QueryClientProvider client={queryClient}>
        <ThemeModeProvider>
          <MemoryRouter>
            <DashboardPage />
          </MemoryRouter>
        </ThemeModeProvider>
      </QueryClientProvider>
    </Provider>,
  );
}

describe('DashboardPage', () => {
  it('renders KPI tiles derived directly from each module, not from reports', async () => {
    renderPage();

    expect(await screen.findByText('Alertes totales')).toBeInTheDocument();
    expect(screen.getByText('12')).toBeInTheDocument();
    expect(screen.getByText('Incidents ouverts')).toBeInTheDocument();
    expect(screen.getByText('Couverture MITRE')).toBeInTheDocument();
  });

  it('renders charts, the realtime badge and the operational status', async () => {
    renderPage();
    await screen.findByText('Alertes totales');

    expect(screen.getAllByTestId('echart').length).toBeGreaterThan(0);
    expect(screen.getByText('En temps réel')).toBeInTheDocument();
    expect(screen.getByText('Plateforme opérationnelle')).toBeInTheDocument();
    expect(screen.getByText('Activité — 7 derniers jours')).toBeInTheDocument();
  });
});
