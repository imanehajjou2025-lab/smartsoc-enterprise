import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import Paper from '@mui/material/Paper';
import Typography from '@mui/material/Typography';

interface PageStubProps {
  title: string;
  description: string;
  milestone: string;
}

/** Page provisoire d'un module : titre réel, contenu à venir au jalon indiqué. */
function PageStub({ title, description, milestone }: PageStubProps) {
  return (
    <Box>
      <Typography variant="h5" component="h2" gutterBottom>
        {title}
      </Typography>
      <Paper variant="outlined" sx={{ p: 3, mt: 2 }}>
        <Typography color="text.secondary" sx={{ mb: 2 }}>
          {description}
        </Typography>
        <Chip
          label={`En construction — ${milestone}`}
          color="primary"
          variant="outlined"
          size="small"
        />
      </Paper>
    </Box>
  );
}

export default PageStub;
