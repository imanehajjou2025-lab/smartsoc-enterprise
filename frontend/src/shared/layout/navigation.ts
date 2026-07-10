import type { SvgIconComponent } from '@mui/icons-material';
import DashboardIcon from '@mui/icons-material/Dashboard';
import NotificationImportantIcon from '@mui/icons-material/NotificationImportant';
import LocalFireDepartmentIcon from '@mui/icons-material/LocalFireDepartment';
import TravelExploreIcon from '@mui/icons-material/TravelExplore';
import DnsIcon from '@mui/icons-material/Dns';
import PublicIcon from '@mui/icons-material/Public';
import GridOnIcon from '@mui/icons-material/GridOn';
import QueryStatsIcon from '@mui/icons-material/QueryStats';
import AccountTreeIcon from '@mui/icons-material/AccountTree';
import AssessmentIcon from '@mui/icons-material/Assessment';
import SmartToyIcon from '@mui/icons-material/SmartToy';
import ManageAccountsIcon from '@mui/icons-material/ManageAccounts';
import SettingsIcon from '@mui/icons-material/Settings';

export interface NavItem {
  label: string;
  path: string;
  icon: SvgIconComponent;
}

export interface NavSection {
  title: string;
  items: NavItem[];
}

/**
 * Carte de navigation de la console — miroir des bounded contexts du
 * backend (ADR-002) et des modules du cahier des charges.
 */
export const navigation: NavSection[] = [
  {
    title: 'Supervision',
    items: [
      { label: 'Dashboard', path: '/dashboard', icon: DashboardIcon },
      { label: 'Alertes', path: '/alerts', icon: NotificationImportantIcon },
      { label: 'Incidents', path: '/incidents', icon: LocalFireDepartmentIcon },
      { label: 'Investigations', path: '/investigations', icon: TravelExploreIcon },
      { label: 'Actifs', path: '/assets', icon: DnsIcon },
    ],
  },
  {
    title: 'Intelligence',
    items: [
      { label: 'Threat Intelligence', path: '/intelligence', icon: PublicIcon },
      { label: 'MITRE ATT&CK', path: '/mitre', icon: GridOnIcon },
      { label: 'Threat Hunting', path: '/hunting', icon: QueryStatsIcon },
    ],
  },
  {
    title: 'Réponse',
    items: [
      { label: 'Playbooks SOAR', path: '/soar', icon: AccountTreeIcon },
      { label: 'Rapports', path: '/reports', icon: AssessmentIcon },
      { label: 'Assistant IA', path: '/assistant', icon: SmartToyIcon },
    ],
  },
  {
    title: 'Plateforme',
    items: [
      { label: 'Utilisateurs', path: '/admin/users', icon: ManageAccountsIcon },
      { label: 'Paramètres', path: '/settings', icon: SettingsIcon },
    ],
  },
];
