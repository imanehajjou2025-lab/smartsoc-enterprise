import { createBrowserRouter, Navigate } from 'react-router-dom';
import UsersPage from '../features/admin/UsersPage';
import AlertsPage from '../features/alerts/AlertsPage';
import IncidentsPage from '../features/incidents/IncidentsPage';
import AssetsPage from '../features/assets/AssetsPage';
import IntelligencePage from '../features/intelligence/IntelligencePage';
import InvestigationsPage from '../features/investigations/InvestigationsPage';
import DashboardPage from '../features/dashboard/DashboardPage';
import LoginPage from '../features/auth/LoginPage';
import { RequireAuth, RequireRole } from '../features/auth/guards';
import AppLayout from '../shared/layout/AppLayout';
import ForbiddenPage from '../shared/components/ForbiddenPage';
import PageStub from '../shared/components/PageStub';

/**
 * Routage de la console. Chaque module pointe vers un stub qui sera
 * remplacé par la vraie feature à son jalon (le chemin, lui, est définitif).
 * Toute la console est derrière RequireAuth ; les routes d'administration
 * sont en plus derrière RequireRole(ADMIN).
 */
export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    element: <RequireAuth />,
    children: [
      {
        path: '/',
        element: <AppLayout />,
        children: [
          { index: true, element: <Navigate to="/dashboard" replace /> },
          { path: 'dashboard', element: <DashboardPage /> },
          { path: 'alerts', element: <AlertsPage /> },
          { path: 'incidents', element: <IncidentsPage /> },
          { path: 'investigations', element: <InvestigationsPage /> },
          { path: 'assets', element: <AssetsPage /> },
          { path: 'intelligence', element: <IntelligencePage /> },
          {
            path: 'mitre',
            element: (
              <PageStub
                title="MITRE ATT&CK"
                description="Matrice des tactiques et techniques, couverture de détection."
                milestone="jalon CTI"
              />
            ),
          },
          {
            path: 'hunting',
            element: (
              <PageStub
                title="Threat Hunting"
                description="Requêtes de chasse vers OpenSearch via les connecteurs SOC."
                milestone="jalon connecteurs"
              />
            ),
          },
          {
            path: 'soar',
            element: (
              <PageStub
                title="Playbooks SOAR"
                description="Builder graphique de playbooks, exécutions et historique des réponses automatisées."
                milestone="jalon SOAR"
              />
            ),
          },
          {
            path: 'reports',
            element: (
              <PageStub
                title="Rapports"
                description="Rapports périodiques et à la demande, export PDF."
                milestone="jalon Rapports"
              />
            ),
          },
          {
            path: 'assistant',
            element: (
              <PageStub
                title="Assistant IA"
                description="Agent conversationnel (service IA externe, ADR-005) : analyse d'alertes, résumés d'incidents, recommandations."
                milestone="jalon intégration IA"
              />
            ),
          },
          {
            element: <RequireRole roles={['ADMIN']} />,
            children: [{ path: 'admin/users', element: <UsersPage /> }],
          },
          {
            path: 'settings',
            element: (
              <PageStub
                title="Paramètres"
                description="Configuration de la plateforme : intégrations, notifications, préférences."
                milestone="jalon Paramètres"
              />
            ),
          },
          { path: 'forbidden', element: <ForbiddenPage /> },
        ],
      },
    ],
  },
]);
