import { useEffect, useRef } from 'react';
import * as echarts from 'echarts';

interface Props {
  option: echarts.EChartsOption;
  height?: number;
}

/**
 * Wrapper ECharts minimal : init/dispose liés au cycle de vie React,
 * redimensionnement automatique. Fond transparent — c'est le Paper MUI
 * qui porte la surface, le thème reste cohérent.
 */
function EChart({ option, height = 300 }: Props) {
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;
    const chart = echarts.init(container, 'dark');
    chart.setOption({ backgroundColor: 'transparent', ...option });
    const onResize = () => chart.resize();
    window.addEventListener('resize', onResize);
    return () => {
      window.removeEventListener('resize', onResize);
      chart.dispose();
    };
  }, [option]);

  return <div ref={containerRef} style={{ height, width: '100%' }} />;
}

export default EChart;
