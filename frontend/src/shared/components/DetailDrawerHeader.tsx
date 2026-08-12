import type { ReactNode } from 'react';
import Box from '@mui/material/Box';
import IconButton from '@mui/material/IconButton';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import CloseIcon from '@mui/icons-material/Close';
import { alpha, useTheme } from '@mui/material/styles';

/**
 * Bandeau d'en-tête « premium » d'un tiroir de détail : fermeture en haut,
 * icône ronde + titre en dessous — bord à bord (marges négatives compensant
 * le padding du tiroir). Les métadonnées (sévérité, horodatage, source...)
 * restent dans le corps du tiroir, déjà couvertes par les sections
 * détaillées : pas de doublon ici.
 */
function DetailDrawerHeader({
  color,
  icon,
  title,
  onClose,
}: {
  color: string;
  icon: ReactNode;
  title: string;
  onClose: () => void;
}) {
  const theme = useTheme();
  const dark = theme.palette.mode === 'dark';
  return (
    <Box
      sx={{
        position: 'relative',
        mx: -3,
        mt: -3,
        mb: 2.5,
        px: 3,
        py: 3,
        bgcolor: alpha(color, dark ? 0.16 : 0.08),
        borderBottom: '2px solid',
        borderColor: alpha(color, 0.5),
      }}
    >
      <IconButton
        size="small"
        onClick={onClose}
        aria-label="Fermer"
        sx={{ position: 'absolute', top: 12, right: 12 }}
      >
        <CloseIcon fontSize="small" />
      </IconButton>
      <Stack direction="row" spacing={2} sx={{ alignItems: 'center', justifyContent: 'center', px: 5 }}>
        <Box
          sx={{
            width: 52,
            height: 52,
            borderRadius: '50%',
            flexShrink: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color,
            bgcolor: alpha(color, 0.18),
            boxShadow: `0 0 18px 2px ${alpha(color, 0.4)}`,
          }}
        >
          {icon}
        </Box>
        <Typography variant="h6" sx={{ fontWeight: 800, lineHeight: 1.3, textAlign: 'center' }}>
          {title}
        </Typography>
      </Stack>
    </Box>
  );
}

export default DetailDrawerHeader;
