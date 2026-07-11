import { useState, type MouseEvent } from 'react';
import Avatar from '@mui/material/Avatar';
import Box from '@mui/material/Box';
import Divider from '@mui/material/Divider';
import IconButton from '@mui/material/IconButton';
import ListItemIcon from '@mui/material/ListItemIcon';
import LogoutIcon from '@mui/icons-material/Logout';
import Menu from '@mui/material/Menu';
import MenuItem from '@mui/material/MenuItem';
import Typography from '@mui/material/Typography';
import { useNavigate } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from '../../app/hooks';
import { logout } from './authSlice';
import { ROLE_LABELS } from './roles';

function UserMenu() {
  const user = useAppSelector((state) => state.auth.user);
  const dispatch = useAppDispatch();
  const navigate = useNavigate();
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);

  if (!user) {
    return null;
  }

  const handleOpen = (event: MouseEvent<HTMLElement>) => setAnchorEl(event.currentTarget);
  const handleClose = () => setAnchorEl(null);

  const handleLogout = async () => {
    handleClose();
    await dispatch(logout());
    navigate('/login', { replace: true });
  };

  return (
    <>
      <IconButton onClick={handleOpen} size="small" aria-label="Menu utilisateur">
        <Avatar sx={{ width: 30, height: 30, bgcolor: 'primary.main', fontSize: 14 }}>
          {user.username.slice(0, 2).toUpperCase()}
        </Avatar>
      </IconButton>
      <Menu anchorEl={anchorEl} open={Boolean(anchorEl)} onClose={handleClose}>
        <Box sx={{ px: 2, py: 1 }}>
          <Typography variant="subtitle2">{user.username}</Typography>
          <Typography variant="caption" color="text.secondary">
            {ROLE_LABELS[user.role] ?? user.role}
          </Typography>
        </Box>
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
