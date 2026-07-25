import Chip from '@mui/material/Chip';

/** Identifiant ATT&CK en monospace — la clé de corrélation, mise en avant. */
export function TechniqueIdChip({ attackId }: { attackId: string }) {
  return <Chip label={attackId} size="small" sx={{ fontFamily: 'monospace', fontWeight: 600 }} />;
}

/** Tactique ATT&CK — discrète (contour) : c'est la technique qui porte l'attention. */
export function TacticChip({ name }: { name: string }) {
  return <Chip label={name} size="small" variant="outlined" />;
}

/** Technique retirée de la matrice par ATT&CK — lisible mais éteinte. */
export function DeprecatedChip() {
  return <Chip label="Déprécié" size="small" variant="outlined" color="warning" />;
}

/**
 * Couleur de fond d'une case de heatmap : du transparent (aucune alerte) au
 * rouge d'accent (couverture maximale). L'intensité est RELATIVE au max
 * observé, pour que l'échelle s'adapte au jeu de données courant (même
 * rouge d'accent que la sévérité critique de la palette partagée).
 */
export function coverageColor(count: number, max: number): string {
  if (count <= 0 || max <= 0) return 'transparent';
  const intensity = count / max;
  const alpha = 0.18 + 0.62 * intensity;
  return `rgba(248, 81, 73, ${alpha.toFixed(2)})`;
}
