import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import GppBadIcon from '@mui/icons-material/GppBad';
import Typography from '@mui/material/Typography';
import { Link } from 'react-router-dom';

/** Page 403 : le rôle de l'utilisateur ne permet pas d'accéder au module. */
function ForbiddenPage() {
  return (
    <Box sx={{ textAlign: 'center', mt: 8 }}>
      <GppBadIcon sx={{ fontSize: 64, color: 'warning.main' }} />
      <Typography variant="h5" sx={{ mt: 2 }}>
        Accès refusé
      </Typography>
      <Typography color="text.secondary" sx={{ mt: 1, mb: 3 }}>
        Votre rôle ne permet pas d'accéder à ce module. Contactez un administrateur si vous pensez
        qu'il s'agit d'une erreur.
      </Typography>
      <Button component={Link} to="/dashboard" variant="outlined">
        Retour au dashboard
      </Button>
    </Box>
  );
}

export default ForbiddenPage;
