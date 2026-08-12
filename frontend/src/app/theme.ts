import { createTheme, type PaletteMode, type Theme } from '@mui/material/styles';

/**
 * Couleurs de sévérité SOC — utilisées partout (badges, graphes, timeline)
 * pour que la lecture visuelle soit constante d'un module à l'autre.
 * Volontairement IDENTIQUES en mode clair et sombre : ce sont des couleurs
 * de statut sémantique (comme dans Sentinel/Defender), pas des couleurs de
 * surface — changer de thème ne doit jamais changer ce qu'une sévérité
 * signifie visuellement.
 */
export const severityColors = {
  critical: '#f85149',
  high: '#f0883e',
  medium: '#d29922',
  low: '#3fb950',
  info: '#58a6ff',
} as const;

export type Severity = keyof typeof severityColors;

const SURFACES = {
  dark: {
    background: { default: '#0d1117', paper: '#161b22' },
    divider: '#21262d',
    text: { primary: '#e6edf3', secondary: '#8b949e' },
    appBar: '#161b22',
    drawer: '#0d1117',
  },
  light: {
    background: { default: '#f6f8fa', paper: '#ffffff' },
    divider: '#d0d7de',
    text: { primary: '#1f2328', secondary: '#57606a' },
    appBar: '#ffffff',
    drawer: '#ffffff',
  },
} as const;

/**
 * Construit le thème pour un mode donné — même identité visuelle SmartSOC
 * (accent bleu/cyan, coins de 8px, sévérités inchangées), seules les
 * surfaces et le texte s'inversent entre clair et sombre.
 */
export function buildTheme(mode: PaletteMode): Theme {
  const s = SURFACES[mode];
  return createTheme({
    palette: {
      mode,
      primary: { main: '#2f81f7' },
      secondary: { main: '#39c5cf' },
      error: { main: severityColors.critical },
      warning: { main: severityColors.high },
      success: { main: severityColors.low },
      info: { main: severityColors.info },
      background: s.background,
      divider: s.divider,
      text: s.text,
    },
    typography: {
      fontFamily: '"Roboto", "Segoe UI", system-ui, sans-serif',
      h6: { fontWeight: 600 },
    },
    shape: { borderRadius: 8 },
    components: {
      MuiAppBar: {
        styleOverrides: {
          root: {
            backgroundColor: s.appBar,
            backgroundImage: 'none',
            borderBottom: `1px solid ${s.divider}`,
          },
        },
      },
      MuiDrawer: {
        styleOverrides: {
          // Les tiroirs temporaires (détail d'alerte, d'actif...) ne
          // doivent jamais passer SOUS la topbar fixe (z-index volontairement
          // au-dessus du tiroir permanent, voir AppLayout) : on les fait
          // commencer sous elle plutôt que de se battre sur le z-index.
          paper: ({ ownerState }) => ({
            backgroundColor: s.drawer,
            borderRight: `1px solid ${s.divider}`,
            // Le thumb de scrollbar par défaut est quasi invisible sur un
            // fond très sombre (#0d1117) : on le rend explicitement visible
            // plutôt que de compter sur le rendu par défaut du navigateur.
            scrollbarColor:
              mode === 'dark' ? 'rgba(255,255,255,0.28) transparent' : undefined,
            '&::-webkit-scrollbar-thumb':
              mode === 'dark'
                ? { backgroundColor: 'rgba(255,255,255,0.28)', borderRadius: 8 }
                : undefined,
            ...(ownerState.variant === 'temporary'
              ? { top: 49, height: 'calc(100% - 49px)' }
              : {}),
          }),
        },
      },
      MuiListItemButton: {
        styleOverrides: {
          root: {
            borderRadius: 8,
            margin: '2px 8px',
            '&.Mui-selected': {
              backgroundColor: 'rgba(47, 129, 247, 0.15)',
            },
          },
        },
      },
    },
  });
}

/**
 * Thème sombre par défaut — export historique conservé tel quel : c'est
 * celui que tous les tests de rendu utilisent (`<ThemeProvider theme=
 * {theme}>`), aucun n'a besoin de connaître le mode courant. L'app réelle
 * n'utilise plus cet export directement, voir `ThemeModeProvider`.
 */
export const theme = buildTheme('dark');
