import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import Button from '@mui/material/Button';
import CircularProgress from '@mui/material/CircularProgress';
import PrecisionManufacturingOutlinedIcon from '@mui/icons-material/PrecisionManufacturingOutlined';
import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import SettingsCard, { SettingsRow } from '../../../shared/components/SettingsCard';
import { severityColors } from '../../../app/theme';
import { problemDetail } from '../../../shared/api/client';
import { listPlaybooks } from '../../soar/soarApi';

const PAGE_SIZE = 200;

function SoarSection() {
  const navigate = useNavigate();
  const { data, isLoading, error } = useQuery({
    queryKey: ['settings-soar-summary'],
    queryFn: () => listPlaybooks('', true, 0, PAGE_SIZE),
  });

  if (isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
        <CircularProgress size={28} />
      </Box>
    );
  }
  if (error || !data) {
    return <Alert severity="error">{problemDetail(error, 'Catalogue SOAR indisponible')}</Alert>;
  }

  const archivedCount = data.items.filter((p) => p.archived).length;

  return (
    <SettingsCard
      title="Catalogue de playbooks"
      icon={<PrecisionManufacturingOutlinedIcon />}
      color={severityColors.high}
      description="La plateforme documente et suit les procédures de réponse (Shuffle reste le moteur d'automatisation réel côté SOC — ADR-012)."
      actions={
        <Button
          size="small"
          startIcon={<PrecisionManufacturingOutlinedIcon fontSize="small" />}
          onClick={() => navigate('/soar')}
        >
          Ouvrir le module SOAR
        </Button>
      }
    >
      <SettingsRow label="Playbooks actifs" value={data.totalElements - archivedCount} />
      <SettingsRow label="Playbooks archivés" value={archivedCount} />
      {data.totalElements > PAGE_SIZE && (
        <Alert severity="info" variant="outlined" sx={{ mt: 1.5 }}>
          Répartition calculée sur les {PAGE_SIZE} premiers playbooks ({data.totalElements} au
          total) — voir le module SOAR pour le décompte exact.
        </Alert>
      )}
    </SettingsCard>
  );
}

export default SoarSection;
