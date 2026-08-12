import { useEffect, useRef } from 'react';
import type { CyberPalette } from './palette';

/**
 * Boîtes lat/lon des continents — plusieurs sous-boîtes par masse plutôt
 * qu'un seul grand rectangle par continent, pour que le semis de points
 * se lise comme un vrai planisphère (Amérique qui se rétrécit vers le
 * sud, Inde en pointe, Asie du Sud-Est...) plutôt que des blocs.
 */
const LANDMASSES: { lat: [number, number]; lon: [number, number]; density: number }[] = [
  // Amérique du Nord
  { lat: [55, 72], lon: [-168, -95], density: 30 },
  { lat: [24, 55], lon: [-125, -66], density: 85 },
  { lat: [8, 24], lon: [-105, -83], density: 20 },
  // Amérique du Sud
  { lat: [-5, 12], lon: [-82, -35], density: 45 },
  { lat: [-20, -5], lon: [-75, -35], density: 45 },
  { lat: [-56, -20], lon: [-73, -53], density: 25 },
  // Europe
  { lat: [36, 71], lon: [-10, 40], density: 55 },
  // Afrique
  { lat: [15, 37], lon: [-18, 38], density: 55 },
  { lat: [-5, 15], lon: [-18, 45], density: 55 },
  { lat: [-35, -5], lon: [10, 41], density: 40 },
  // Asie
  { lat: [12, 55], lon: [25, 60], density: 35 },
  { lat: [45, 72], lon: [40, 150], density: 70 },
  { lat: [18, 50], lon: [95, 145], density: 65 },
  { lat: [8, 30], lon: [68, 90], density: 35 },
  { lat: [-10, 20], lon: [92, 140], density: 35 },
  // Océanie
  { lat: [-44, -10], lon: [112, 154], density: 30 },
];

function mulberry32(seed: number) {
  let a = seed;
  return () => {
    a |= 0;
    a = (a + 0x6d2b79f5) | 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

interface LandDot {
  lat: number;
  lon: number;
}

/** Nuage de points continent, régénéré une seule fois (déterministe) — pas de Math.random par frame. */
function buildLandDots(density: number): LandDot[] {
  const rand = mulberry32(0xc0ffee);
  const dots: LandDot[] = [];
  for (const box of LANDMASSES) {
    const n = Math.round(box.density * density);
    const latMid = (box.lat[0] + box.lat[1]) / 2;
    const lonMid = (box.lon[0] + box.lon[1]) / 2;
    const latHalf = (box.lat[1] - box.lat[0]) / 2;
    const lonHalf = (box.lon[1] - box.lon[0]) / 2;
    for (let i = 0; i < n; i++) {
      // Rejet dans une ellipse inscrite pour un contour organique plutôt qu'un rectangle net.
      let dx = 0;
      let dy = 0;
      let tries = 0;
      do {
        dx = rand() * 2 - 1;
        dy = rand() * 2 - 1;
        tries++;
      } while (dx * dx + dy * dy > 1 && tries < 8);
      dots.push({ lat: latMid + dy * latHalf, lon: lonMid + dx * lonHalf });
    }
  }
  return dots;
}

/**
 * Sous-ensemble des points continent relié à ses plus proches voisins
 * (distance lat/lon approx) — un maillage façon « réseau mondial », pas
 * juste quelques arcs entre grandes villes. Calculé une seule fois : les
 * distances angulaires entre deux points ne changent pas avec la rotation.
 */
function buildMeshEdges(dots: LandDot[], everyN: number, neighbors: number): [number, number][] {
  const nodeIndices: number[] = [];
  for (let i = 0; i < dots.length; i += everyN) nodeIndices.push(i);

  const edgeSet = new Set<string>();
  const edges: [number, number][] = [];
  for (const i of nodeIndices) {
    const a = dots[i];
    const dists = nodeIndices
      .filter((j) => j !== i)
      .map((j) => {
        const b = dots[j];
        const dLat = a.lat - b.lat;
        const dLon = (a.lon - b.lon) * Math.cos((a.lat * Math.PI) / 180);
        return { j, d: dLat * dLat + dLon * dLon };
      })
      .sort((x, y) => x.d - y.d)
      .slice(0, neighbors);
    for (const { j } of dists) {
      const key = i < j ? `${i}-${j}` : `${j}-${i}`;
      if (!edgeSet.has(key)) {
        edgeSet.add(key);
        edges.push([i, j]);
      }
    }
  }
  return edges;
}

interface Projected {
  x: number;
  y: number;
  visible: number; // 0..1, facteur de profondeur (proche = 1, limbe = 0)
}

function project(
  latDeg: number,
  lonDeg: number,
  rotation: number,
  tilt: number,
  cx: number,
  cy: number,
  r: number,
): Projected {
  const lat = (latDeg * Math.PI) / 180;
  const lon = (lonDeg * Math.PI) / 180 + rotation;
  const x = Math.cos(lat) * Math.sin(lon);
  const y = Math.sin(lat);
  const z = Math.cos(lat) * Math.cos(lon);
  const yT = y * Math.cos(tilt) - z * Math.sin(tilt);
  const zT = y * Math.sin(tilt) + z * Math.cos(tilt);
  return { x: cx + x * r, y: cy - yT * r, visible: Math.max(0, zT) };
}

/** Étoiles fixes (déterministe), coordonnées normalisées 0..1 — ne sont dessinées que hors du disque du globe. */
function buildStars(count: number): { nx: number; ny: number; r: number; phase: number }[] {
  const rand = mulberry32(0x57a2);
  const stars: { nx: number; ny: number; r: number; phase: number }[] = [];
  for (let i = 0; i < count; i++) {
    stars.push({ nx: rand(), ny: rand() * 0.85, r: 0.5 + rand() * 1.2, phase: rand() * Math.PI * 2 });
  }
  return stars;
}

interface Props {
  palette: CyberPalette;
  reducedMotion: boolean;
  density: number; // 0..1, réduit sur mobile
}

/**
 * Globe numérique en Canvas 2D (pas de dépendance WebGL) : vue proche de
 * l'horizon (façon photo orbitale), continents en points via une vraie
 * projection orthographique (donc une sphère qui tourne, pas une image),
 * ciel étoilé, halo d'atmosphère sur le limbe, et un maillage de
 * connexions animées entre points du réseau. Une seule boucle rAF pour
 * tout l'écran plutôt que des dizaines de composants React animés
 * individuellement — mise en pause hors écran et sous
 * `prefers-reduced-motion`.
 */
function Globe({ palette, reducedMotion, density }: Props) {
  const canvasRef = useRef<HTMLCanvasElement>(null);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const dots = buildLandDots(density);
    const edges = buildMeshEdges(dots, density < 1 ? 4 : 3, 2);
    const pulseEdges = edges.filter((_, i) => i % 4 === 0);
    const stars = buildStars(density < 1 ? 80 : 150);

    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    let width = 0;
    let height = 0;
    let cx = 0;
    let cy = 0;
    let radius = 0;

    const resize = () => {
      const rect = canvas.parentElement!.getBoundingClientRect();
      width = rect.width;
      height = rect.height;
      canvas.width = width * dpr;
      canvas.height = height * dpr;
      canvas.style.width = `${width}px`;
      canvas.style.height = `${height}px`;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      // Zoom au-delà du simple « diamètre = largeur » demandé au tour précédent, et
      // sommet de la sphère toujours à une marge fixe du haut du cadre (calculée
      // depuis ce sommet plutôt que via un offset relatif au rayon) : évite la ligne
      // de coupure visible quand le sommet finit trop près — ou au-dessus — du bord
      // haut du canvas.
      radius = width * 0.72;
      const apexY = height * 0.32;
      cx = width / 2;
      cy = apexY + radius;
    };
    resize();
    const resizeObserver = new ResizeObserver(resize);
    resizeObserver.observe(canvas.parentElement!);

    const tilt = -0.22;
    const blueColor = palette.lightBlue;
    const redColor = palette.redSecondary;
    let rotation = 0;
    let raf = 0;
    let paused = document.hidden;
    let last = performance.now();

    const onVisibility = () => {
      paused = document.hidden;
      if (!paused && !reducedMotion) {
        last = performance.now();
        raf = requestAnimationFrame(tick);
      }
    };
    document.addEventListener('visibilitychange', onVisibility);

    const drawFrame = (rot: number, tSec: number) => {
      ctx.clearRect(0, 0, width, height);

      // Ciel étoilé — uniquement hors du disque du globe
      for (const s of stars) {
        const sx = s.nx * width;
        const sy = s.ny * height;
        if ((sx - cx) ** 2 + (sy - cy) ** 2 <= radius * radius) continue;
        const twinkle = reducedMotion ? 0.7 : 0.5 + 0.5 * Math.sin(tSec * 1.6 + s.phase);
        ctx.globalAlpha = 0.35 + twinkle * 0.55;
        ctx.fillStyle = '#ffffff';
        ctx.beginPath();
        ctx.arc(sx, sy, s.r, 0, Math.PI * 2);
        ctx.fill();
      }
      ctx.globalAlpha = 1;

      // Halo d'atmosphère (lueur large derrière le limbe)
      const halo = ctx.createRadialGradient(cx, cy, radius * 0.92, cx, cy, radius * 1.18);
      halo.addColorStop(0, `${palette.cyan}00`);
      halo.addColorStop(0.6, `${palette.cyan}33`);
      halo.addColorStop(1, `${palette.cyan}00`);
      ctx.fillStyle = halo;
      ctx.beginPath();
      ctx.arc(cx, cy, radius * 1.18, 0, Math.PI * 2);
      ctx.fill();

      // Sphère (fond)
      ctx.save();
      ctx.beginPath();
      ctx.arc(cx, cy, radius, 0, Math.PI * 2);
      ctx.clip();
      const surface = ctx.createRadialGradient(cx, cy - radius * 0.3, radius * 0.2, cx, cy, radius);
      surface.addColorStop(0, `${palette.blue}22`);
      surface.addColorStop(1, `${palette.bgTo}f5`);
      ctx.fillStyle = surface;
      ctx.fillRect(cx - radius, cy - radius, radius * 2, radius * 2);

      // Maillage de connexions
      for (const [i, j] of edges) {
        const a = dots[i];
        const b = dots[j];
        const pa = project(a.lat, a.lon, rot, tilt, cx, cy, radius);
        const pb = project(b.lat, b.lon, rot, tilt, cx, cy, radius);
        if (pa.visible <= 0.03 && pb.visible <= 0.03) continue;
        const av = Math.max(pa.visible, pb.visible);
        ctx.beginPath();
        ctx.moveTo(pa.x, pa.y);
        ctx.lineTo(pb.x, pb.y);
        ctx.strokeStyle = `${palette.lightBlue}${Math.round(av * 38)
          .toString(16)
          .padStart(2, '0')}`;
        ctx.lineWidth = 0.6;
        ctx.stroke();
      }

      // Connexions « en mouvement » : un point lumineux qui parcourt une partie des arêtes
      if (!reducedMotion) {
        for (let k = 0; k < pulseEdges.length; k++) {
          const [i, j] = pulseEdges[k];
          const a = dots[i];
          const b = dots[j];
          const pa = project(a.lat, a.lon, rot, tilt, cx, cy, radius);
          const pb = project(b.lat, b.lon, rot, tilt, cx, cy, radius);
          if (pa.visible <= 0.05 && pb.visible <= 0.05) continue;
          const ft = (tSec * 0.35 + k * 0.17) % 1;
          const qx = pa.x + (pb.x - pa.x) * ft;
          const qy = pa.y + (pb.y - pa.y) * ft;
          ctx.beginPath();
          ctx.arc(qx, qy, 1.4, 0, Math.PI * 2);
          ctx.fillStyle = palette.lightBlue;
          ctx.globalAlpha = 0.8;
          ctx.fill();
        }
        ctx.globalAlpha = 1;
      }

      // Continents (points) — halo peu coûteux (deux passes pleines plutôt que shadowBlur par point)
      for (const dot of dots) {
        const p = project(dot.lat, dot.lon, rot, tilt, cx, cy, radius);
        if (p.visible <= 0.03) continue;
        const color = dot.lon < 20 ? blueColor : redColor;
        const a = 0.4 + p.visible * 0.6;
        ctx.fillStyle = color;
        ctx.globalAlpha = a * 0.28;
        ctx.beginPath();
        ctx.arc(p.x, p.y, 3.6, 0, Math.PI * 2);
        ctx.fill();
        ctx.globalAlpha = a;
        ctx.beginPath();
        ctx.arc(p.x, p.y, 1.4, 0, Math.PI * 2);
        ctx.fill();
      }
      ctx.globalAlpha = 1;
      ctx.restore();

      // Liseré lumineux sur le limbe (rim light), flouté — un seul passage, pas cher
      ctx.save();
      ctx.filter = 'blur(3px)';
      ctx.beginPath();
      ctx.arc(cx, cy, radius, 0, Math.PI * 2);
      ctx.strokeStyle = `${palette.cyan}bb`;
      ctx.lineWidth = 2.5;
      ctx.stroke();
      ctx.restore();
    };

    const tick = (now: number) => {
      const dt = now - last;
      last = now;
      if (!reducedMotion) rotation += dt * 0.00012;
      drawFrame(rotation, now / 1000);
      if (!paused) raf = requestAnimationFrame(tick);
    };

    // Toujours peindre une première image tout de suite : `document.hidden` peut être
    // vrai dès le montage (onglet ouvert en arrière-plan, fenêtre pas encore focus) et on
    // ne veut jamais laisser le canvas vide en attendant un hypothétique `visibilitychange`.
    drawFrame(rotation, performance.now() / 1000);
    if (!reducedMotion && !paused) {
      raf = requestAnimationFrame(tick);
    }

    return () => {
      cancelAnimationFrame(raf);
      resizeObserver.disconnect();
      document.removeEventListener('visibilitychange', onVisibility);
    };
  }, [palette, reducedMotion, density]);

  return (
    <canvas
      ref={canvasRef}
      aria-hidden
      style={{ position: 'absolute', inset: 0, width: '100%', height: '100%', display: 'block' }}
    />
  );
}

export default Globe;
