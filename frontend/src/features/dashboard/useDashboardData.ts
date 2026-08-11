import { useQuery } from '@tanstack/react-query';
import { listAlerts, type Alert } from '../alerts/alertsApi';
import { listAssets, type Asset } from '../assets/assetsApi';
import { listIncidents, type Incident } from '../incidents/incidentsApi';
import { listIocs, type Indicator } from '../intelligence/intelligenceApi';
import {
  getCoverage,
  listTechniques,
  type MitreCoverage,
  type MitreTechnique,
} from '../mitre/mitreApi';
import { listHunts, type HuntQuery } from '../hunting/huntingApi';
import { listPlaybooks } from '../soar/soarApi';
import { listReports, type ReportSummary } from '../reports/reportsApi';
import { getAlertStats, type AlertStats } from './dashboardApi';

const ASSET_PAGE_SIZE = 200;
const INCIDENT_PAGE_SIZE = 200;
const RECENT_SIZE = 8;
const ALERTS_RECENT_SIZE = 10;

export interface DashboardOverview {
  alertStats: AlertStats;
  recentAlerts: Alert[];
  incidents: Incident[];
  incidentsTotal: number;
  assets: Asset[];
  assetsTotal: number;
  playbooksActiveTotal: number;
  hunts: HuntQuery[];
  huntsTotal: number;
  iocs: Indicator[];
  iocsActiveTotal: number;
  techniques: MitreTechnique[];
  coverage: MitreCoverage[];
  recentReports: ReportSummary[];
}

/**
 * Compose une lecture directe de chaque module — jamais du module
 * Rapports pour un KPI principal (Rapports n'apparaît qu'en secours,
 * `recentReports`, pour l'activité récente). Chaque compteur affiché
 * dérive de `totalElements` (vrai total serveur, indépendant de la
 * taille de page) ; les répartitions détaillées (statut, criticité…)
 * sont calculées côté client sur les `items` chargés — exactes tant que
 * le volume réel reste sous le plafond de page (200, le max autorisé par
 * l'API), annoncé ici en clair plutôt que masqué.
 */
export function useDashboardData() {
  return useQuery<DashboardOverview>({
    queryKey: ['dashboard-overview'],
    queryFn: async () => {
      const [
        alertStats,
        recentAlertsPage,
        incidentsPage,
        assetsPage,
        playbooksPage,
        huntsPage,
        iocsPage,
        techniquesPage,
        coverage,
        recentReportsPage,
      ] = await Promise.all([
        getAlertStats(),
        listAlerts({ page: 0, size: ALERTS_RECENT_SIZE }),
        listIncidents({ page: 0, size: INCIDENT_PAGE_SIZE }),
        listAssets({ status: 'ACTIVE', page: 0, size: ASSET_PAGE_SIZE }),
        listPlaybooks('', false, 0, 1),
        listHunts('', 0, RECENT_SIZE),
        listIocs({ status: 'ACTIVE', page: 0, size: RECENT_SIZE }),
        listTechniques({ page: 0, size: 200 }),
        getCoverage(),
        listReports(0, RECENT_SIZE),
      ]);

      return {
        alertStats,
        recentAlerts: recentAlertsPage.items,
        incidents: incidentsPage.items,
        incidentsTotal: incidentsPage.totalElements,
        assets: assetsPage.items,
        assetsTotal: assetsPage.totalElements,
        playbooksActiveTotal: playbooksPage.totalElements,
        hunts: huntsPage.items,
        huntsTotal: huntsPage.totalElements,
        iocs: iocsPage.items,
        iocsActiveTotal: iocsPage.totalElements,
        techniques: techniquesPage.items,
        coverage,
        recentReports: recentReportsPage.items,
      };
    },
  });
}
