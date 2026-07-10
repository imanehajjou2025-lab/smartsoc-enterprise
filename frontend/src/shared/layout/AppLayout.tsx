import AppBar from '@mui/material/AppBar';
import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Drawer from '@mui/material/Drawer';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import ListItemText from '@mui/material/ListItemText';
import ListSubheader from '@mui/material/ListSubheader';
import ShieldIcon from '@mui/icons-material/Shield';
import Toolbar from '@mui/material/Toolbar';
import Typography from '@mui/material/Typography';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { navigation } from './navigation';

const DRAWER_WIDTH = 248;

/**
 * Squelette de la console : topbar fixe, sidebar de navigation par
 * sections, zone de contenu routée. Le chip utilisateur est un placeholder
 * jusqu'au jalon F3 (authentification).
 */
function AppLayout() {
  const location = useLocation();

  return (
    <Box sx={{ display: 'flex', minHeight: '100vh' }}>
      <AppBar position="fixed" sx={{ zIndex: (t) => t.zIndex.drawer + 1 }}>
        <Toolbar variant="dense">
          <ShieldIcon color="primary" sx={{ mr: 1.5 }} />
          <Typography variant="h6" component="h1" sx={{ flexGrow: 1 }}>
            SmartSOC{' '}
            <Box component="span" sx={{ color: 'text.secondary', fontWeight: 400 }}>
              Enterprise
            </Box>
          </Typography>
          <Chip label="Non connecté" size="small" variant="outlined" />
        </Toolbar>
      </AppBar>

      <Drawer
        variant="permanent"
        sx={{
          width: DRAWER_WIDTH,
          flexShrink: 0,
          '& .MuiDrawer-paper': { width: DRAWER_WIDTH, boxSizing: 'border-box' },
        }}
      >
        <Toolbar variant="dense" />
        <Box component="nav" aria-label="Navigation principale" sx={{ overflowY: 'auto', pt: 1 }}>
          {navigation.map((section) => (
            <List
              key={section.title}
              dense
              subheader={
                <ListSubheader sx={{ bgcolor: 'transparent', lineHeight: '32px' }}>
                  {section.title}
                </ListSubheader>
              }
            >
              {section.items.map((item) => {
                const Icon = item.icon;
                const selected = location.pathname.startsWith(item.path);
                return (
                  <ListItemButton
                    key={item.path}
                    component={NavLink}
                    to={item.path}
                    selected={selected}
                  >
                    <ListItemIcon sx={{ minWidth: 36 }}>
                      <Icon fontSize="small" color={selected ? 'primary' : 'inherit'} />
                    </ListItemIcon>
                    <ListItemText primary={item.label} />
                  </ListItemButton>
                );
              })}
            </List>
          ))}
        </Box>
      </Drawer>

      <Box component="main" sx={{ flexGrow: 1, p: 3 }}>
        <Toolbar variant="dense" />
        <Outlet />
      </Box>
    </Box>
  );
}

export default AppLayout;
