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
 */
function EChart({ option, height = 300 }: Props) {
  const containerRef = useRef<HTMLDivElement>(null);
  const { mode } = useThemeMode();

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;
    const chart = echarts.init(container, mode === 'dark' ? 'dark' : undefined);
    chart.setOption({ backgroundColor: 'transparent', ...option });
    const onResize = () => chart.resize();
    window.addEventListener('resize', onResize);
    return () => {
      window.removeEventListener('resize', onResize);
      chart.dispose();
    };
  }, [option, mode]);

  return <div ref={containerRef} style={{ height, width: '100%' }} />;
}

export default EChart;
