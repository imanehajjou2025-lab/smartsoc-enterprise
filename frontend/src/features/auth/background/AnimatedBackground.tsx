import { useEffect, useState } from 'react';
import Box from '@mui/material/Box';
import { keyframes } from '@mui/material/styles';
import Globe from './Globe';
import { CYBER_PALETTE, type CyberPalette } from './palette';

const flow = keyframes`
  to { stroke-dashoffset: -800; }
`;

/** Grands arcs qui balaient l'écran depuis les coins — bleu à gauche, rouge à droite. */
const SWEEP_ARCS: { d: string; side: 'blue' | 'red' }[] = [
  { d: 'M -60 -40 C 220 40, 340 180, 560 260', side: 'blue' },
  { d: 'M -80 140 C 160 220, 300 320, 520 420', side: 'blue' },
  { d: 'M 1340 -40 C 1060 40, 940 180, 720 260', side: 'red' },
  { d: 'M 1360 140 C 1120 220, 980 320, 760 420', side: 'red' },
];

function usePrefersReducedMotion(): boolean {
  const [reduced, setReduced] = useState(
    () => window.matchMedia('(prefers-reduced-motion: reduce)').matches,
  );
  useEffect(() => {
    const mql = window.matchMedia('(prefers-reduced-motion: reduce)');
    const onChange = () => setReduced(mql.matches);
    mql.addEventListener('change', onChange);
    return () => mql.removeEventListener('change', onChange);
  }, []);
  return reduced;
}

function useIsCompact(): boolean {
  const [compact, setCompact] = useState(() => window.innerWidth < 700);
  useEffect(() => {
    const onResize = () => setCompact(window.innerWidth < 700);
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);
  return compact;
}

function SweepArcs({ palette, reduceMotionSx }: { palette: CyberPalette; reduceMotionSx: object }) {
  return (
    <Box component="svg" viewBox="0 0 1280 800" preserveAspectRatio="none" sx={{ position: 'absolute', inset: 0, width: '100%', height: '100%' }}>
      <defs>
        <linearGradient id="sweepBlue" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stopColor={palette.cyan} stopOpacity={0.35} />
          <stop offset="100%" stopColor={palette.blue} stopOpacity={0} />
        </linearGradient>
        <linearGradient id="sweepRed" x1="1" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor={palette.redSecondary} stopOpacity={0.32} />
          <stop offset="100%" stopColor={palette.red} stopOpacity={0} />
        </linearGradient>
      </defs>
      {SWEEP_ARCS.map((arc, i) => (
        <Box
          key={i}
          component="path"
          d={arc.d}
          stroke={`url(#sweep${arc.side === 'blue' ? 'Blue' : 'Red'})`}
          strokeWidth={0.8}
          fill="none"
          strokeDasharray="4 18"
          sx={{ animation: `${flow} ${22 + i * 2}s linear infinite`, ...reduceMotionSx }}
        />
      ))}
    </Box>
  );
}

/**
 * Fond animé de la page de connexion, réduit au strict essentiel après
 * retour utilisateur : dégradé + convergence bleu/rouge, arcs de flux, et
 * le globe (Terre) — rien d'autre (pas de grille, particules ou badges,
 * jugés trop chargés).
 */
function AnimatedBackground({ mode }: { mode: 'dark' | 'light' }) {
  const palette = CYBER_PALETTE[mode];
  const reducedMotion = usePrefersReducedMotion();
  const compact = useIsCompact();

  const reduceMotionSx = { '@media (prefers-reduced-motion: reduce)': { animation: 'none !important' } };

  return (
    <Box
      aria-hidden
      sx={{
        position: 'fixed',
        inset: 0,
        zIndex: 0,
        overflow: 'hidden',
        pointerEvents: 'none',
        background: `radial-gradient(ellipse at 50% 0%, ${palette.bgFrom} 0%, ${palette.bgTo} 60%, ${palette.bgTo} 100%)`,
        transition: 'background 0.6s ease',
      }}
    >
      {/* Convergence des flux : bleu/cyan à gauche, rouge à droite */}
      <Box
        sx={{
          position: 'absolute',
          top: '-8%',
          left: '-10%',
          width: '55%',
          height: '70%',
          background: `radial-gradient(circle at 30% 30%, ${palette.blue}2e 0%, transparent 65%)`,
          filter: 'blur(20px)',
        }}
      />
      <Box
        sx={{
          position: 'absolute',
          top: '-6%',
          right: '-10%',
          width: '55%',
          height: '70%',
          background: `radial-gradient(circle at 70% 30%, ${palette.red}26 0%, transparent 65%)`,
          filter: 'blur(20px)',
        }}
      />

      {/* Flux : grands arcs de balayage */}
      <SweepArcs palette={palette} reduceMotionSx={reduceMotionSx} />

      {/* Terre — ancrée bas d'écran, partiellement hors cadre */}
      <Box
        sx={{
          position: 'absolute',
          left: 0,
          right: 0,
          bottom: 0,
          height: { xs: '55%', md: '68%' },
        }}
      >
        <Globe palette={palette} reducedMotion={reducedMotion} density={compact ? 1 : 2.6} />
      </Box>
    </Box>
  );
}

export default AnimatedBackground;
