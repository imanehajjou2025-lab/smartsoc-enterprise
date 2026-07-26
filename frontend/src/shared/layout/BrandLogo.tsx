import Box from '@mui/material/Box';

interface Props {
  /** 'mark' = bouclier seul (sidebar repliée, favicon-like) ; 'full' = lockup complet (login). */
  variant?: 'mark' | 'full';
  height?: number;
}

/** Identité visuelle ISIX (logo fourni, détouré en PNG/WebP — voir docs/brand). */
function BrandLogo({ variant = 'mark', height = 32 }: Props) {
  const src = variant === 'mark' ? '/isix-mark.webp' : '/isix-logo.webp';
  return (
    <Box component="img" src={src} alt="ISIX" sx={{ height, width: 'auto', display: 'block' }} />
  );
}

export default BrandLogo;
