import Box from '@mui/material/Box';
import Chip from '@mui/material/Chip';
import LinearProgress from '@mui/material/LinearProgress';
import Typography from '@mui/material/Typography';
import { severityColors } from '../../app/theme';
import type { IndicatorStatus, IndicatorType, ReputationVerdict, TlpMarking } from './intelligenceApi';

export const IOC_TYPE_LABELS: Record<IndicatorType, string> = {
  IPV4: 'IPv4',
  IPV6: 'IPv6',
  DOMAIN: 'Domaine',
  URL: 'URL',
  MD5: 'MD5',
  SHA1: 'SHA-1',
  SHA256: 'SHA-256',
  EMAIL: 'E-mail',
};

export const IOC_STATUS_LABELS: Record<IndicatorStatus, string> = {
  ACTIVE: 'Actif',
  EXPIRED: 'Expiré',
  REVOKED: 'Révoqué',
};

export const TLP_LABELS: Record<TlpMarking, string> = {
  CLEAR: 'TLP:CLEAR',
  GREEN: 'TLP:GREEN',
  AMBER: 'TLP:AMBER',
  RED: 'TLP:RED',
};

/** Le type d'indicateur. Discret : c'est la valeur qui porte l'attention. */
export function IocTypeChip({ type }: { type: IndicatorType }) {
  return <Chip label={IOC_TYPE_LABELS[type]} size="small" variant="outlined" />;
}

/**
 * Statut d'un IOC — reçu du serveur, jamais recalculé ici. Seul ACTIVE
 * enrichit encore les alertes : il est mis en avant (plein vert), tandis
 * qu'expiré (gris) et révoqué (rouge) restent lisibles mais éteints.
 */
export function IocStatusChip({ status }: { status: IndicatorStatus }) {
  if (status === 'ACTIVE') {
    return <Chip label={IOC_STATUS_LABELS.ACTIVE} size="small" color="success" />;
  }
  if (status === 'REVOKED') {
    return <Chip label={IOC_STATUS_LABELS.REVOKED} size="small" color="error" variant="outlined" />;
  }
  return <Chip label={IOC_STATUS_LABELS.EXPIRED} size="small" variant="outlined" />;
}

/**
 * Marquage TLP — convention de couleurs FIRST, alignée sur la palette
 * partagée. C'est une contrainte de diffusion : elle voyage avec l'IOC
 * pour que les futurs exports (Rapports) puissent la respecter.
 */
const TLP_STYLE: Record<TlpMarking, { color: string; bg: string }> = {
  CLEAR: { color: '#e6edf3', bg: 'rgba(139, 148, 158, 0.2)' },
  GREEN: { color: severityColors.low, bg: 'rgba(63, 185, 80, 0.15)' },
  AMBER: { color: severityColors.medium, bg: 'rgba(210, 153, 34, 0.15)' },
  RED: { color: severityColors.critical, bg: 'rgba(248, 81, 73, 0.15)' },
};

export function TlpChip({ tlp }: { tlp: TlpMarking }) {
  const style = TLP_STYLE[tlp];
  return (
    <Chip
      label={TLP_LABELS[tlp]}
      size="small"
      sx={{
        color: style.color,
        backgroundColor: style.bg,
        fontWeight: 600,
        fontFamily: 'monospace',
      }}
    />
  );
}

export const REPUTATION_VERDICT_LABELS: Record<ReputationVerdict, string> = {
  MALICIOUS: 'Malveillant',
  SUSPICIOUS: 'Suspect',
  HARMLESS: 'Inoffensif',
  UNDETECTED: 'Non détecté',
};

/**
 * Verdict VirusTotal — DÉRIVÉ côté serveur des compteurs réels (« pire cas
 * gagne »), jamais recalculé ici. Non détecté reste neutre : l'absence de
 * détection n'est pas une preuve d'innocuité.
 */
export function ReputationVerdictChip({ verdict }: { verdict: ReputationVerdict }) {
  if (verdict === 'MALICIOUS') {
    return <Chip label={REPUTATION_VERDICT_LABELS.MALICIOUS} size="small" color="error" />;
  }
  if (verdict === 'SUSPICIOUS') {
    return <Chip label={REPUTATION_VERDICT_LABELS.SUSPICIOUS} size="small" color="warning" />;
  }
  if (verdict === 'HARMLESS') {
    return <Chip label={REPUTATION_VERDICT_LABELS.HARMLESS} size="small" color="success" />;
  }
  return <Chip label={REPUTATION_VERDICT_LABELS.UNDETECTED} size="small" variant="outlined" />;
}

/**
 * Confiance 0-100 : petite barre teintée par niveau (les seuils suivent
 * les usages CTI — haute ≥ 75, moyenne ≥ 50, faible en dessous). Le
 * chiffre reste affiché, la barre ne fait que le rendre lisible d'un
 * coup d'œil.
 */
export function ConfidenceBar({ confidence }: { confidence: number }) {
  const color =
    confidence >= 75
      ? severityColors.low
      : confidence >= 50
        ? severityColors.medium
        : severityColors.high;
  return (
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, minWidth: 90 }}>
      <LinearProgress
        variant="determinate"
        value={confidence}
        sx={{
          flexGrow: 1,
          height: 6,
          borderRadius: 3,
          backgroundColor: 'rgba(139, 148, 158, 0.2)',
          '& .MuiLinearProgress-bar': { backgroundColor: color },
        }}
      />
      <Typography variant="caption" sx={{ color: 'text.secondary', minWidth: 24 }}>
        {confidence}
      </Typography>
    </Box>
  );
}
