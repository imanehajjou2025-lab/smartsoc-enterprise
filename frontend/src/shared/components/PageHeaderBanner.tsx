import type { ReactNode } from 'react';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import { alpha, keyframes, useTheme } from '@mui/material/styles';

const pulse = keyframes`
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.35; transform: scale(1.5); }
`;

export interface PageHeaderBadge {
  icon: ReactNode;
  label: string;
  /** Piloté par un état réel (connexion temps réel, disponibilité d'une source...) — jamais une valeur figée. */
  active: boolean;
  activeColor: string;
  /** Couleur quand `active` est faux — reprend `text.secondary` par défaut. */
  inactiveColor?: string;
  /** Anime un point pulsé (comme la connexion temps réel) plutôt qu'une icône statique. */
  pulseDot?: boolean;
}

export interface PageHeaderAction {
  label: string;
  icon?: ReactNode;
  onClick: () => void;
}

/**
 * Bannière d'en-tête « Enterprise » partagée entre les pages qui en ont
 * besoin (Dashboard, Alertes...) — extraite du Dashboard pour garantir
 * une identité visuelle IDENTIQUE plutôt qu'une resemblance approximative
 * recopiée à la main. Le fil d'Ariane / recherche / notifications /
 * profil vivent déjà globalement dans {@link AppLayout} ; cette bannière
 * ne porte que ce qui est spécifique à CETTE page (icône, titre, sous-titre,
 * badges d'état réels, action principale).
 */
function PageHeaderBanner({
  icon,
  title,
  subtitle,
  badges = [],
  action,
}: {
  icon: ReactNode;
  title: string;
  subtitle?: ReactNode;
  badges?: PageHeaderBadge[];
  action?: PageHeaderAction;
}) {
  const theme = useTheme();

  return (
    <Paper
      elevation={0}
      sx={{
        display: 'flex',
        alignItems: 'center',
        gap: 2.5,
        px: 2.5,
        py: 2,
        mb: 3,
        borderRadius: 4,
        flexWrap: 'wrap',
        border: '1px solid',
        borderColor: alpha(theme.palette.primary.main, theme.palette.mode === 'dark' ? 0.45 : 0.18),
        background: `linear-gradient(135deg, ${alpha(theme.palette.primary.main, theme.palette.mode === 'dark' ? 0.22 : 0.1)} 0%, ${theme.palette.background.paper} 80%)`,
      }}
    >
      <Box
        sx={{
          width: 56,
          height: 56,
          borderRadius: 2,
          flexShrink: 0,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          bgcolor: alpha(theme.palette.primary.main, 0.14),
          border: '1px solid',
          borderColor: alpha(theme.palette.primary.main, 0.35),
        }}
      >
        {icon}
      </Box>

      <Box sx={{ minWidth: 0 }}>
        <Typography variant="h5" component="h2" sx={{ fontWeight: 800 }}>
          {title}
        </Typography>
        {subtitle && (
          <Typography variant="body2" color="text.secondary">
            {subtitle}
          </Typography>
        )}
      </Box>

      {badges.length > 0 && (
        <Box
          sx={{
            width: '1px',
            alignSelf: 'stretch',
            bgcolor: alpha(theme.palette.primary.main, theme.palette.mode === 'dark' ? 0.4 : 0.25),
          }}
        />
      )}

      {badges.map((badge, index) => {
        const color = badge.active ? badge.activeColor : (badge.inactiveColor ?? theme.palette.text.secondary);
        return (
          <Box
            key={index}
            sx={{
              display: 'flex',
              alignItems: 'center',
              gap: 0.75,
              px: 1.5,
              py: 0.6,
              borderRadius: 999,
              border: '1px solid',
              borderColor: alpha(color, 0.4),
              bgcolor: alpha(color, 0.12),
            }}
          >
            {badge.pulseDot ? (
              <Box
                sx={{
                  width: 8,
                  height: 8,
                  borderRadius: '50%',
                  bgcolor: color,
                  boxShadow: badge.active ? `0 0 6px 2px ${alpha(color, 0.7)}` : 'none',
                  animation: badge.active ? `${pulse} 1.6s ease-in-out infinite` : 'none',
                }}
              />
            ) : (
              <Box sx={{ display: 'flex', color, fontSize: 18, '& svg': { fontSize: 18 } }}>
                {badge.icon}
              </Box>
            )}
            <Typography variant="body2" sx={{ fontWeight: 700, color }}>
              {badge.label}
            </Typography>
          </Box>
        );
      })}

      <Box sx={{ flexGrow: 1 }} />

      {action && (
        <Button
          variant="contained"
          disableElevation
          startIcon={action.icon}
          onClick={action.onClick}
          sx={{
            bgcolor: '#2f81f7',
            borderRadius: 2,
            px: 2.5,
            fontWeight: 700,
            boxShadow: '0 0 20px rgba(47,129,247,0.5)',
            '&:hover': { bgcolor: '#1f6feb', boxShadow: '0 0 24px rgba(47,129,247,0.65)' },
          }}
        >
          {action.label}
        </Button>
      )}
    </Paper>
  );
}

export default PageHeaderBanner;
