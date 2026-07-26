import { useState, type MouseEvent } from 'react';
import Badge from '@mui/material/Badge';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Divider from '@mui/material/Divider';
import IconButton from '@mui/material/IconButton';
import NotificationsOutlinedIcon from '@mui/icons-material/NotificationsOutlined';
import Popover from '@mui/material/Popover';
import Typography from '@mui/material/Typography';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { getAlertStats } from '../../features/dashboard/dashboardApi';

/**
 * Alertes nouvelles (statut NEW), en temps réel — même requête et même clé
 * de cache que le Dashboard (`useAlertsRealtime`, monté dans AppLayout,
 * invalide `['alerts', ...]` à chaque événement WebSocket). Un seul chiffre
 * affiché : `/alerts/stats` ne croise pas sévérité et statut, donc pas de
 * répartition par sévérité ici — inventer ce croisement serait une donnée
 * fausse.
 */
function NotificationsBell() {
  const navigate = useNavigate();
  const [anchorEl, setAnchorEl] = useState<HTMLElement | null>(null);
  const { data } = useQuery({ queryKey: ['alerts', 'stats'], queryFn: getAlertStats });

  const newCount = data?.byStatus.NEW ?? 0;

  const open = (e: MouseEvent<HTMLElement>) => setAnchorEl(e.currentTarget);
  const close = () => setAnchorEl(null);
  const goToNew = () => {
    close();
    navigate('/alerts?status=NEW');
  };

  return (
    <>
      <IconButton onClick={open} aria-label={`Notifications (${newCount} nouvelles alertes)`}>
        <Badge badgeContent={newCount} color="error" max={99}>
          <NotificationsOutlinedIcon />
        </Badge>
      </IconButton>
      <Popover
        open={Boolean(anchorEl)}
        anchorEl={anchorEl}
        onClose={close}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}
        transformOrigin={{ vertical: 'top', horizontal: 'right' }}
      >
        <Box sx={{ width: 260, p: 2 }}>
          <Typography variant="subtitle2" sx={{ mb: 1 }}>
            Alertes nouvelles
          </Typography>
          <Divider sx={{ mb: 1.5 }} />
          {newCount === 0 ? (
            <Typography variant="body2" color="text.secondary">
              Aucune alerte nouvelle.
            </Typography>
          ) : (
            <>
              <Typography variant="h4" sx={{ fontWeight: 800, mb: 0.5 }}>
                {newCount}
              </Typography>
              <Typography
                variant="caption"
                color="text.secondary"
                sx={{ display: 'block', mb: 1.5 }}
              >
                en attente de triage
              </Typography>
              <Button size="small" fullWidth variant="outlined" onClick={goToNew}>
                Voir les alertes nouvelles
              </Button>
            </>
          )}
        </Box>
      </Popover>
    </>
  );
}

export default NotificationsBell;
