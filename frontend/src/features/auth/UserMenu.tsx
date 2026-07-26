import { useState, type MouseEvent } from 'react';
import Avatar from '@mui/material/Avatar';
import Box from '@mui/material/Box';
import ButtonBase from '@mui/material/ButtonBase';
import Divider from '@mui/material/Divider';
import ListItemIcon from '@mui/material/ListItemIcon';
import LightModeOutlinedIcon from '@mui/icons-material/LightModeOutlined';
import DarkModeOutlinedIcon from '@mui/icons-material/DarkModeOutlined';
import LogoutIcon from '@mui/icons-material/Logout';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import SettingsOutlinedIcon from '@mui/icons-material/SettingsOutlined';
import Switch from '@mui/material/Switch';
import Tooltip from '@mui/material/Tooltip';
import Typography from '@mui/material/Typography';
import { useNavigate } from 'react-router-dom';
import { useThemeMode } from '../../app/ThemeModeProvider';
import { useAppDispatch, useAppSelector } from '../../app/hooks';
import { logout } from './authSlice';
import { ROLE_LABELS } from './roles';

interface Props {
  /** Sidebar repliée : avatar seul, pas de nom/rôle (comme les items de nav). */
  collapsed?: boolean;
}

/** Bloc profil en pied de sidebar : identité, rôle, mode clair/sombre, déconnexion. */
function UserMenu({ collapsed = false }: Props) {
  const user = useAppSelector((state) => state.auth.user);
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const { mode, toggle } = useThemeMode();
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);

  if (!user) {
    return null;
  }

  const displayName = user.fullName ?? user.username;
  const initials = displayName
    .split(/\s+/)
    .map((part) => part[0])
    .slice(0, 2)
    .join('')
    .toUpperCase();

  const handleOpen = (event: MouseEvent<HTMLElement>) => setAnchorEl(event.currentTarget);
  const handleClose = () => setAnchorEl(null);

  const handleLogout = async () => {
    handleClose();
    await dispatch(logout());
    navigate('/login', { replace: true });
  };

  const avatar = (
    <Avatar sx={{ width: 32, height: 32, bgcolor: 'primary.main', fontSize: 13, flexShrink: 0 }}>
      {initials}
    </Avatar>
  );

  return (
    <>
      {collapsed ? (
        <Tooltip title={displayName} placement="right">
          <ButtonBase
            onClick={handleOpen}
            aria-label={`Menu utilisateur — ${displayName}`}
            sx={{ borderRadius: 1, p: 0.5 }}
          >
            {avatar}
          </ButtonBase>
        </Tooltip>
      ) : (
        <ButtonBase
          onClick={handleOpen}
          aria-label={`Menu utilisateur — ${displayName}`}
          sx={{
            width: '100%',
            display: 'flex',
            alignItems: 'center',
            gap: 1.25,
            p: 1,
            borderRadius: 1,
            justifyContent: 'flex-start',
          }}
        >
          {avatar}
          <Box sx={{ minWidth: 0, textAlign: 'left' }}>
            <Typography variant="body2" noWrap sx={{ fontWeight: 600 }}>
              {displayName}
            </Typography>
            <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block' }}>
              {ROLE_LABELS[user.role] ?? user.role}
            </Typography>
          </Box>
        </ButtonBase>
      )}

      <Menu
        anchorEl={anchorEl}
        open={Boolean(anchorEl)}
        onClose={handleClose}
        anchorOrigin={{ vertical: 'top', horizontal: 'right' }}
        transformOrigin={{ vertical: 'bottom', horizontal: 'left' }}
      >
        <Box sx={{ px: 2, py: 1 }}>
          <Typography variant="subtitle2">{displayName}</Typography>
          <Typography variant="caption" color="text.secondary">
            {ROLE_LABELS[user.role] ?? user.role}
          </Typography>
        </Box>
        <Divider />
        <MenuItem onClick={toggle}>
          <ListItemIcon>
            {mode === 'dark' ? (
              <DarkModeOutlinedIcon fontSize="small" />
            ) : (
              <LightModeOutlinedIcon fontSize="small" />
            )}
          </ListItemIcon>
          Mode {mode === 'dark' ? 'sombre' : 'clair'}
          <Switch
            checked={mode === 'light'}
            size="small"
            sx={{ ml: 'auto' }}
            onClick={(e) => e.stopPropagation()}
            onChange={toggle}
            slotProps={{ input: { 'aria-label': 'Basculer le mode clair/sombre' } }}
          />
        </MenuItem>
        <MenuItem
          onClick={() => {
            handleClose();
            navigate('/settings');
          }}
        >
          <ListItemIcon>
            <SettingsOutlinedIcon fontSize="small" />
          </ListItemIcon>
          Paramètres
        </MenuItem>
        <Divider />
        <MenuItem onClick={handleLogout}>
          <ListItemIcon>
            <LogoutIcon fontSize="small" />
          </ListItemIcon>
          Se déconnecter
        </MenuItem>
      </Menu>
    </>
  );
}

export default UserMenu;
