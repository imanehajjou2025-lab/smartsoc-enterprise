import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';
import ScheduleOutlinedIcon from '@mui/icons-material/ScheduleOutlined';

/**
 * Section volontairement vide : aucune donnée réelle n'existe encore
 * derrière (décision explicite, plutôt que d'afficher un état inventé).
 */
function ComingSoonSection({ title, description }: { title: string; description: string }) {
  return (
    <Box>
      <Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>
        {title}
      </Typography>
      <Paper
        variant="outlined"
        sx={{
          p: 4,
          borderRadius: 3,
          borderStyle: 'dashed',
          textAlign: 'center',
          color: 'text.secondary',
        }}
      >
        <ScheduleOutlinedIcon sx={{ fontSize: 36, mb: 1, opacity: 0.6 }} />
        <Typography variant="body2" sx={{ maxWidth: 480, mx: 'auto', mb: 2 }}>
          {description}
        </Typography>
        <Chip label="À venir" size="small" variant="outlined" />
      </Paper>
    </Box>
  );
}

export default ComingSoonSection;
