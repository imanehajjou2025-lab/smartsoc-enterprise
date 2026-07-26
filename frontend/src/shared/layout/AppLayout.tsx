import { useEffect, useMemo, useState } from 'react';
import AppBar from '@mui/material/AppBar';
import Box from '@mui/material/Box';
import Breadcrumbs from '@mui/material/Breadcrumbs';
import Button from '@mui/material/Button';
import Drawer from '@mui/material/Drawer';
import IconButton from '@mui/material/IconButton';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import ListSubheader from '@mui/material/ListSubheader';
import Stack from '@mui/material/Stack';
import Toolbar from '@mui/material/Toolbar';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import ChevronLeftIcon from '@mui/icons-material/ChevronLeft';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import SearchIcon from '@mui/icons-material/Search';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { useAlertsRealtime } from '../../features/alerts/useAlertsRealtime';
import UserMenu from '../../features/auth/UserMenu';
import BrandLogo from './BrandLogo';
import GlobalSearch from './GlobalSearch';
import { navigation } from './navigation';
import NotificationsBell from './NotificationsBell';
import UtcClock from './UtcClock';
import { useSidebarCollapsed } from './useSidebarCollapsed';

const EXPANDED_WIDTH = 248;
const COLLAPSED_WIDTH = 64;

/** Fil d'Ariane dérivé de la route courante et de la carte de navigation — aucune donnée par page à maintenir. */
function useBreadcrumb(pathname: string) {
  return useMemo(() => {
    for (const section of navigation) {
      const item = section.items.find((i) => pathname.startsWith(i.path));
      if (item) return { section: section.title, label: item.label };
    }
    return null;
  }, [pathname]);
}

/**
 * Squelette de la console : topbar Enterprise (fil d'Ariane, horloge,
 * recherche, notifications, profil), sidebar repliable, zone de contenu
 * routée — confinée verticalement (jamais de scroll horizontal de page).
 */
function AppLayout() {
  const location = useLocation();
  const { collapsed, toggle } = useSidebarCollapsed();
  const [searchOpen, setSearchOpen] = useState(false);
  const breadcrumb = useBreadcrumb(location.pathname);
  const drawerWidth = collapsed ? COLLAPSED_WIDTH : EXPANDED_WIDTH;

  // Connexion temps réel globale (alertes) : garde le badge de notifications
  // à jour quelle que soit la page affichée, pas seulement Alertes/Dashboard.
  useAlertsRealtime();

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault();
        setSearchOpen(true);
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, []);

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh', maxWidth: '100vw', overflowX: 'hidden' }}>
      <AppBar position="fixed" sx={{ zIndex: (t) => t.zIndex.drawer + 1 }}>
        <Toolbar variant="dense" sx={{ gap: 2 }}>
          <Stack sx={{ flexGrow: 1, minWidth: 0 }}>
            <Breadcrumbs
              separator="›"
              sx={{ '& .MuiBreadcrumbs-separator': { color: 'text.secondary' } }}
            >
              <Typography variant="caption" color="text.secondary">
                Accueil
              </Typography>
              {breadcrumb && (
                <Typography variant="caption" color="text.secondary">
                  {breadcrumb.section}
                </Typography>
              )}
              {breadcrumb && (
                <Typography variant="caption" sx={{ fontWeight: 600 }}>
                  {breadcrumb.label}
                </Typography>
              )}
            </Breadcrumbs>
          </Stack>

          <Button
            onClick={() => setSearchOpen(true)}
            startIcon={<SearchIcon fontSize="small" />}
            variant="outlined"
            color="inherit"
            sx={{
              width: 260,
              display: { xs: 'none', sm: 'flex' },
              justifyContent: 'flex-start',
              color: 'text.secondary',
              borderColor: 'divider',
              textTransform: 'none',
              fontWeight: 400,
            }}
          >
            Rechercher…
            <Box sx={{ flexGrow: 1 }} />
            <Typography variant="caption" color="text.secondary">
              Ctrl+K
            </Typography>
          </Button>

          <UtcClock />
          <NotificationsBell />
          <UserMenu />
        </Toolbar>
      </AppBar>

      <Drawer
        variant="permanent"
        sx={{
          width: drawerWidth,
          flexShrink: 0,
          transition: (t) => t.transitions.create('width', { duration: 200 }),
          '& .MuiDrawer-paper': {
            width: drawerWidth,
            boxSizing: 'border-box',
            overflowX: 'hidden',
            transition: (t) => t.transitions.create('width', { duration: 200 }),
          },
        }}
      >
        <Toolbar
          variant="dense"
          sx={{ px: collapsed ? 1 : 2, justifyContent: collapsed ? 'center' : 'flex-start' }}
        >
          <BrandLogo variant="mark" height={28} />
          {!collapsed && (
            <Typography variant="subtitle2" sx={{ ml: 1.5, fontWeight: 700, letterSpacing: 0.5 }}>
              ISIX
            </Typography>
          )}
        </Toolbar>

        <Box
          component="nav"
          aria-label="Navigation principale"
          sx={{ overflowY: 'auto', overflowX: 'hidden', flexGrow: 1, pt: 1 }}
        >
          {navigation.map((section) => (
            <List
              key={section.title}
              dense
              subheader={
                !collapsed ? (
                  <ListSubheader sx={{ bgcolor: 'transparent', lineHeight: '32px' }}>
                    {section.title}
                  </ListSubheader>
                ) : undefined
              }
            >
              {section.items.map((item) => {
                const Icon = item.icon;
                const selected = location.pathname.startsWith(item.path);
                const button = (
                  <ListItemButton
                    key={item.path}
                    component={NavLink}
                    to={item.path}
                    selected={selected}
                    sx={{
                      justifyContent: collapsed ? 'center' : 'flex-start',
                      px: collapsed ? 1.5 : 2,
                    }}
                  >
                    <ListItemIcon sx={{ minWidth: collapsed ? 0 : 36, justifyContent: 'center' }}>
                      <Icon fontSize="small" color={selected ? 'primary' : 'inherit'} />
                    </ListItemIcon>
                    {!collapsed && <ListItemText primary={item.label} />}
                  </ListItemButton>
                );
                return collapsed ? (
                  <Tooltip key={item.path} title={item.label} placement="right">
                    {button}
                  </Tooltip>
                ) : (
                  button
                );
              })}
            </List>
          ))}
        </Box>

        <Box sx={{ p: 1, borderTop: 1, borderColor: 'divider' }}>
          <Tooltip title={collapsed ? 'Déplier le menu' : 'Replier le menu'} placement="right">
            <IconButton
              onClick={toggle}
              size="small"
              aria-label={collapsed ? 'Déplier le menu' : 'Replier le menu'}
              sx={{ width: '100%', borderRadius: 1 }}
            >
              {collapsed ? (
                <ChevronRightIcon fontSize="small" />
              ) : (
                <ChevronLeftIcon fontSize="small" />
              )}
            </IconButton>
          </Tooltip>
        </Box>
      </Drawer>

      <Box component="main" sx={{ flexGrow: 1, minWidth: 0, p: 3, overflowX: 'hidden' }}>
        <Toolbar variant="dense" />
        <Outlet />
      </Box>

      <GlobalSearch open={searchOpen} onClose={() => setSearchOpen(false)} />
    </Box>
  );
}

export default AppLayout;
