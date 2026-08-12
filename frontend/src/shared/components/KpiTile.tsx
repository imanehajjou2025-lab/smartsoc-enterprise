import type { ReactNode } from 'react';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import { alpha } from '@mui/material/styles';

/**
 * Tuile KPI partagée (icône ronde teintée + valeur + libellé) — extraite
 * du Dashboard pour que toute page qui en a besoin (Alertes...) obtienne
 * exactement le même rendu, jamais une resemblance approximative.
 */
function KpiTile({
  label,
  value,
  hint,
  color,
  icon,
  onClick,
}: {
  label: string;
  value: string;
  hint: string;
  color: string;
  icon: ReactNode;
  onClick?: () => void;
}) {
  return (
    <Paper
      variant="outlined"
      onClick={onClick}
      sx={{
        p: 2,
        minHeight: 108,
        display: 'flex',
        gap: 1.75,
        alignItems: 'center',
        flex: 1,
        borderRadius: 3,
        cursor: onClick ? 'pointer' : 'default',
        borderColor: alpha(color, 0.25),
        background: (t) =>
          `linear-gradient(135deg, ${alpha(color, t.palette.mode === 'dark' ? 0.18 : 0.12)} 0%, ${t.palette.background.paper} 70%)`,
        transition: 'transform 150ms ease, border-color 150ms ease',
        '&:hover': onClick ? { borderColor: color, transform: 'translateY(-2px)' } : undefined,
      }}
    >
      <Box
        sx={{
          width: 46,
          height: 46,
          borderRadius: '50%',
          flexShrink: 0,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color,
          backgroundColor: alpha(color, 0.16),
          boxShadow: `0 0 16px 3px ${alpha(color, 0.45)}`,
        }}
      >
        {icon}
      </Box>
      <Box sx={{ minWidth: 0 }}>
        <Typography
          variant="caption"
          color="text.secondary"
          sx={{ textTransform: 'uppercase', letterSpacing: 0.4, fontWeight: 700 }}
        >
          {label}
        </Typography>
        <Typography variant="h4" sx={{ fontWeight: 800, lineHeight: 1.1, color }}>
          {value}
        </Typography>
        <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block' }}>
          {hint}
        </Typography>
      </Box>
    </Paper>
  );
}

export default KpiTile;
