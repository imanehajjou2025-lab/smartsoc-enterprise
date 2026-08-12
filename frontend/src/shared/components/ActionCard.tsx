import type { ReactNode } from 'react';
import ButtonBase from '@mui/material/ButtonBase';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { alpha, useTheme } from '@mui/material/styles';

/**
 * Bouton d'action « carte » : icône + titre en gras sur une ligne,
 * description atténuée en dessous — remplace le simple `Button` pour les
 * actions principales d'un tiroir de détail (escalade, assistant, triage...),
 * plus lisible qu'un libellé seul.
 */
function ActionCard({
  icon,
  title,
  description,
  color,
  filled = false,
  onClick,
  disabled = false,
}: {
  icon: ReactNode;
  title: string;
  description: string;
  color: string;
  /** Fond plein teinté (action principale) plutôt que juste bordée. */
  filled?: boolean;
  onClick: () => void;
  disabled?: boolean;
}) {
  const theme = useTheme();
  return (
    <ButtonBase
      onClick={onClick}
      disabled={disabled}
      sx={{
        display: 'block',
        textAlign: 'left',
        p: 2,
        borderRadius: 2,
        border: '1px solid',
        borderColor: alpha(color, filled ? 0.7 : 0.45),
        bgcolor: filled ? alpha(color, theme.palette.mode === 'dark' ? 0.9 : 0.85) : alpha(color, 0.08),
        transition: 'transform 120ms ease, border-color 120ms ease',
        '&:hover': disabled ? undefined : { borderColor: color, transform: 'translateY(-1px)' },
        '&.Mui-disabled': { opacity: 0.6 },
      }}
    >
      <Stack direction="row" spacing={1.25} sx={{ alignItems: 'center', mb: 0.5 }}>
        <Stack sx={{ color: filled ? '#fff' : color, '& svg': { fontSize: 20 } }}>{icon}</Stack>
        <Typography sx={{ fontWeight: 800, color: filled ? '#fff' : color, lineHeight: 1.2 }}>
          {title}
        </Typography>
      </Stack>
      <Typography
        variant="caption"
        sx={{ display: 'block', color: filled ? alpha('#fff', 0.85) : 'text.secondary' }}
      >
        {description}
      </Typography>
    </ButtonBase>
  );
}

export default ActionCard;
