import Box from '@mui/material/Box';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import ListSubheader from '@mui/material/ListSubheader';
import MenuItem from '@mui/material/MenuItem';
import Paper from '@mui/material/Paper';
import TextField from '@mui/material/TextField';
import Typography from '@mui/material/Typography';
import useMediaQuery from '@mui/material/useMediaQuery';
import { useTheme } from '@mui/material/styles';
import CableOutlinedIcon from '@mui/icons-material/CableOutlined';
import DashboardOutlinedIcon from '@mui/icons-material/DashboardOutlined';
import HandymanOutlinedIcon from '@mui/icons-material/HandymanOutlined';
import HistoryOutlinedIcon from '@mui/icons-material/HistoryOutlined';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import ManageAccountsOutlinedIcon from '@mui/icons-material/ManageAccountsOutlined';
import MonitorHeartOutlinedIcon from '@mui/icons-material/MonitorHeartOutlined';
import NotificationsOutlinedIcon from '@mui/icons-material/NotificationsOutlined';
import PrecisionManufacturingOutlinedIcon from '@mui/icons-material/PrecisionManufacturingOutlined';
import SaveOutlinedIcon from '@mui/icons-material/SaveOutlined';
import SecurityOutlinedIcon from '@mui/icons-material/SecurityOutlined';
import SmartToyOutlinedIcon from '@mui/icons-material/SmartToyOutlined';
import { useSearchParams } from 'react-router-dom';
import AboutSection from './sections/AboutSection';
import AiSection from './sections/AiSection';
import AuditLogSection from './sections/AuditLogSection';
import BackupSection from './sections/BackupSection';
import ComingSoonSection from './sections/ComingSoonSection';
import ConnectorsSection from './sections/ConnectorsSection';
import HealthSection from './sections/HealthSection';
import NotificationsSection from './sections/NotificationsSection';
import OverviewSection from './sections/OverviewSection';
import RbacSection from './sections/RbacSection';
import SecuritySection from './sections/SecuritySection';
import SoarSection from './sections/SoarSection';

interface SettingsCategory {
  key: string;
  label: string;
  icon: React.ReactNode;
  group: string;
}

const CATEGORIES: SettingsCategory[] = [
  {
    key: 'overview',
    label: 'Vue générale',
    icon: <DashboardOutlinedIcon fontSize="small" />,
    group: 'Plateforme',
  },
  {
    key: 'rbac',
    label: 'Utilisateurs & rôles',
    icon: <ManageAccountsOutlinedIcon fontSize="small" />,
    group: 'Plateforme',
  },
  {
    key: 'security',
    label: 'Authentification & sécurité',
    icon: <SecurityOutlinedIcon fontSize="small" />,
    group: 'Plateforme',
  },
  {
    key: 'ai',
    label: 'Intelligence artificielle',
    icon: <SmartToyOutlinedIcon fontSize="small" />,
    group: 'Modules',
  },
  {
    key: 'soar',
    label: 'SOAR',
    icon: <PrecisionManufacturingOutlinedIcon fontSize="small" />,
    group: 'Modules',
  },
  {
    key: 'connectors',
    label: 'Sources de données / Connecteurs',
    icon: <CableOutlinedIcon fontSize="small" />,
    group: 'Modules',
  },
  {
    key: 'notifications',
    label: 'Notifications',
    icon: <NotificationsOutlinedIcon fontSize="small" />,
    group: 'Modules',
  },
  {
    key: 'audit',
    label: "Journal d'audit",
    icon: <HistoryOutlinedIcon fontSize="small" />,
    group: 'Opérations',
  },
  {
    key: 'backup',
    label: 'Sauvegarde & restauration',
    icon: <SaveOutlinedIcon fontSize="small" />,
    group: 'Opérations',
  },
  {
    key: 'health',
    label: 'Santé des services',
    icon: <MonitorHeartOutlinedIcon fontSize="small" />,
    group: 'Opérations',
  },
  {
    key: 'maintenance',
    label: 'Maintenance',
    icon: <HandymanOutlinedIcon fontSize="small" />,
    group: 'Opérations',
  },
  {
    key: 'about',
    label: 'À propos',
    icon: <InfoOutlinedIcon fontSize="small" />,
    group: 'Opérations',
  },
];

const GROUPS = ['Plateforme', 'Modules', 'Opérations'];

function renderSection(key: string) {
  switch (key) {
    case 'overview':
      return <OverviewSection />;
    case 'rbac':
      return <RbacSection />;
    case 'security':
      return <SecuritySection />;
    case 'ai':
      return <AiSection />;
    case 'soar':
      return <SoarSection />;
    case 'connectors':
      return <ConnectorsSection />;
    case 'notifications':
      return <NotificationsSection />;
    case 'audit':
      return <AuditLogSection />;
    case 'backup':
      return <BackupSection />;
    case 'health':
      return <HealthSection />;
    case 'maintenance':
      return (
        <ComingSoonSection
          title="Maintenance"
          description="Aucun mode maintenance ni purge de cache n'existe encore côté plateforme. Cette section restera vide tant qu'aucune vraie capacité de maintenance n'est construite."
        />
      );
    case 'about':
      return <AboutSection />;
    default:
      return <OverviewSection />;
  }
}

/**
 * Console d'administration Enterprise. Sous-navigation par catégorie
 * (deep-link `?section=`), zone de contenu qui charge ses propres données
 * — chaque section reste indépendante, aucune ne bloque les autres.
 */
function SettingsPage() {
  const theme = useTheme();
  const isNarrow = useMediaQuery(theme.breakpoints.down('md'));
  const [searchParams, setSearchParams] = useSearchParams();
  const activeKey = searchParams.get('section') ?? 'overview';
  const active = CATEGORIES.find((c) => c.key === activeKey) ?? CATEGORIES[0];

  function selectCategory(key: string) {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev);
      next.set('section', key);
      return next;
    });
  }

  return (
    <Box>
      <Typography variant="h5" component="h2" sx={{ mb: 0.5 }}>
        Paramètres
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2.5 }}>
        Console d'administration de la plateforme — configuration, sécurité, intégrations et
        exploitation.
      </Typography>

      {isNarrow ? (
        <TextField
          select
          fullWidth
          size="small"
          label="Section"
          value={active.key}
          onChange={(event) => selectCategory(event.target.value)}
          sx={{ mb: 2.5 }}
        >
          {GROUPS.flatMap((group) => [
            <MenuItem key={group} disabled divider sx={{ opacity: 1, fontWeight: 700 }}>
              {group}
            </MenuItem>,
            ...CATEGORIES.filter((c) => c.group === group).map((c) => (
              <MenuItem key={c.key} value={c.key} sx={{ pl: 3 }}>
                {c.label}
              </MenuItem>
            )),
          ])}
        </TextField>
      ) : (
        <Box sx={{ display: 'flex', gap: 3, alignItems: 'flex-start' }}>
          <Paper
            variant="outlined"
            sx={{ width: 268, flexShrink: 0, borderRadius: 3, overflow: 'hidden' }}
          >
            {GROUPS.map((group) => (
              <List
                key={group}
                dense
                subheader={
                  <ListSubheader
                    component="div"
                    sx={{ fontSize: 11, fontWeight: 700, letterSpacing: 0.5, lineHeight: '32px' }}
                  >
                    {group.toUpperCase()}
                  </ListSubheader>
                }
                sx={{ py: 0 }}
              >
                {CATEGORIES.filter((c) => c.group === group).map((c) => (
                  <ListItemButton
                    key={c.key}
                    selected={c.key === active.key}
                    onClick={() => selectCategory(c.key)}
                    sx={{ borderRadius: 0 }}
                  >
                    <ListItemIcon sx={{ minWidth: 34 }}>{c.icon}</ListItemIcon>
                    <ListItemText
                      slotProps={{
                        primary: {
                          variant: 'body2',
                          sx: { fontWeight: c.key === active.key ? 700 : 500 },
                        },
                      }}
                    >
                      {c.label}
                    </ListItemText>
                  </ListItemButton>
                ))}
              </List>
            ))}
          </Paper>
          <Box sx={{ flex: 1, minWidth: 0 }}>{renderSection(active.key)}</Box>
        </Box>
      )}
      {isNarrow && <Box>{renderSection(active.key)}</Box>}
    </Box>
  );
}

export default SettingsPage;
