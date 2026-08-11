import { useEffect, useRef } from 'react';
import * as echarts from 'echarts';
import { useThemeMode } from '../../app/ThemeModeProvider';

interface Props {
  option: echarts.EChartsOption;
  height?: number;
}

/**
 * Wrapper ECharts minimal : init/dispose liés au cycle de vie React,
 * redimensionnement automatique. Fond transparent — c'est le Paper MUI
 * qui porte la surface, le thème reste cohérent. Le thème ECharts nommé
 * ('dark' ou aucun = clair par défaut) suit le mode courant de la
 * plateforme.
 *
 * <p>`ResizeObserver` sur le conteneur plutôt que l'événement `resize` de
 * `window` : un repli de la barre latérale (ou tout changement de grille
 * CSS) redimensionne le conteneur SANS que la fenêtre elle-même change de
 * taille — `window.resize` ne se déclenche jamais dans ce cas, le
 * graphique restait figé à son ancienne largeur.
 */
function EChart({ option, height = 300 }: Props) {
  const containerRef = useRef<HTMLDivElement>(null);
  const { mode } = useThemeMode();

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;
    const chart = echarts.init(container, mode === 'dark' ? 'dark' : undefined);
    chart.setOption({ backgroundColor: 'transparent', ...option });
    const resizeObserver = new ResizeObserver(() => chart.resize());
    resizeObserver.observe(container);
    return () => {
      resizeObserver.disconnect();
      chart.dispose();
    };
  }, [option, mode]);

  return <div ref={containerRef} style={{ height, width: '100%' }} />;
}

export default EChart;
