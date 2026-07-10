import { createTheme } from '@mui/material/styles';

/**
 * Couleurs de sévérité SOC — utilisées partout (badges, graphes, timeline)
 * pour que la lecture visuelle soit constante d'un module à l'autre.
 */
export const severityColors = {
  critical: '#f85149',
  high: '#f0883e',
  medium: '#d29922',
  low: '#3fb950',
  info: '#58a6ff',
} as const;

export type Severity = keyof typeof severityColors;

/**
 * Thème sombre de la console SmartSOC, inspiré des consoles SOC modernes
 * (Sentinel, Elastic Security) : fond bleu-nuit, accent cyan, surfaces
 * discrètes pour laisser la couleur porter l'information de sévérité.
 */
export const theme = createTheme({
  palette: {
    mode: 'dark',
    primary: { main: '#2f81f7' },
    secondary: { main: '#39c5cf' },
    error: { main: severityColors.critical },
    warning: { main: severityColors.high },
    success: { main: severityColors.low },
    info: { main: severityColors.info },
    background: {
      default: '#0d1117',
      paper: '#161b22',
    },
    divider: '#21262d',
    text: {
      primary: '#e6edf3',
      secondary: '#8b949e',
    },
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
          backgroundColor: '#161b22',
          backgroundImage: 'none',
          borderBottom: '1px solid #21262d',
        },
      },
    },
    MuiDrawer: {
      styleOverrides: {
        paper: {
          backgroundColor: '#0d1117',
          borderRight: '1px solid #21262d',
        },
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
