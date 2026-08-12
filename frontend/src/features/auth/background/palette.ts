export interface CyberPalette {
  bgFrom: string;
  bgTo: string;
  blue: string;
  cyan: string;
  lightBlue: string;
  red: string;
  redSecondary: string;
  textPrimary: string;
  textSecondary: string;
  cardBg: string;
  cardBorder: string;
}

/** Palette « cybersécurité premium » dédiée à la page de connexion — distincte du thème applicatif, voir demande produit. */
export const CYBER_PALETTE: Record<'dark' | 'light', CyberPalette> = {
  dark: {
    bgFrom: '#050816',
    bgTo: '#030712',
    blue: '#2563EB',
    cyan: '#06B6D4',
    lightBlue: '#38BDF8',
    red: '#EF4444',
    redSecondary: '#F43F5E',
    textPrimary: '#F8FAFC',
    textSecondary: '#94A3B8',
    cardBg: 'rgba(8,13,28,0.62)',
    cardBorder: 'rgba(56,189,248,0.22)',
  },
  light: {
    bgFrom: '#F8FAFC',
    bgTo: '#EEF5FF',
    blue: '#2563EB',
    cyan: '#06B6D4',
    lightBlue: '#38BDF8',
    red: '#EF4444',
    redSecondary: '#F43F5E',
    textPrimary: '#0F172A',
    textSecondary: '#64748B',
    cardBg: 'rgba(255,255,255,0.70)',
    cardBorder: 'rgba(37,99,235,0.16)',
  },
};
