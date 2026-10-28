import type { Coordinate, DeliveryStop } from '../types/route';
import { formatDistance } from './routing';

function mercator(point: Coordinate, zoom: number) {
  const latitude = Math.max(-85.05112878, Math.min(85.05112878, point.latitude));
  const scale = 256 * Math.pow(2, zoom);
  const sin = Math.sin((latitude * Math.PI) / 180);
  return {
    x: ((point.longitude + 180) / 360) * scale,
    y: (0.5 - Math.log((1 + sin) / (1 - sin)) / (4 * Math.PI)) * scale,
  };
}

function haversineDist(c1: Coordinate, c2: Coordinate): number {
  const R = 6371000;
  const dLat = ((c2.latitude - c1.latitude) * Math.PI) / 180;
  const dLon = ((c2.longitude - c1.longitude) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos((c1.latitude * Math.PI) / 180) *
      Math.cos((c2.latitude * Math.PI) / 180) *
      Math.sin(dLon / 2) *
      Math.sin(dLon / 2);
  return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function calculateBearing(c1: Coordinate, c2: Coordinate): number {
  const lat1 = (c1.latitude * Math.PI) / 180;
  const lat2 = (c2.latitude * Math.PI) / 180;
  const dLon = ((c2.longitude - c1.longitude) * Math.PI) / 180;
  const y = Math.sin(dLon) * Math.cos(lat2);
  const x = Math.cos(lat1) * Math.sin(lat2) - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
  const radians = Math.atan2(y, x);
  return ((radians * 180) / Math.PI + 360) % 360;
}

function cubicEaseInOut(t: number): number {
  return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
}

function tileImage(x: number, y: number, zoom: number): Promise<{ image: HTMLImageElement; x: number; y: number }> {
  return new Promise((resolve) => {
    const image = new Image();
    image.crossOrigin = 'anonymous';
    image.onload = () => resolve({ image, x, y });
    image.onerror = () => {
      // Clean fallback canvas tile if network tile fails or is cached
      const fallback = document.createElement('canvas');
      fallback.width = 256;
      fallback.height = 256;
      const fCtx = fallback.getContext('2d');
      if (fCtx) {
        fCtx.fillStyle = '#f0f3eb';
        fCtx.fillRect(0, 0, 256, 256);
        fCtx.strokeStyle = '#e2e7dc';
        fCtx.strokeRect(0, 0, 256, 256);
      }
      const dummy = new Image();
      dummy.src = fallback.toDataURL();
      resolve({ image: dummy, x, y });
    };
    image.src = `https://tile.openstreetmap.org/${zoom}/${x}/${y}.png`;
  });
}

export interface AnimationExportOptions {
  routeName: string;
  stops: DeliveryStop[];
  path: Coordinate[];
  markerColor?: string;
  lineColor?: string;
  onProgress?: (progressPercent: number, stage: string) => void;
}

export async function exportRouteAnimationVideo({
  routeName,
  stops,
  path,
  markerColor = '#1E453B',
  lineColor = '#D5EA83',
  onProgress,
}: AnimationExportOptions): Promise<{ blob: Blob; mimeType: string; filename: string }> {
  const locatedStops = stops.filter(
    (s): s is DeliveryStop & { latitude: number; longitude: number } =>
      s.latitude !== null && s.longitude !== null,
  );

  const routePath =
    path && path.length > 1
      ? path
      : locatedStops.map((s) => ({ latitude: s.latitude, longitude: s.longitude }));

  if (routePath.length < 2) {
    throw new Error('At least 2 points are required to animate and export a route video.');
  }

  // 1. Setup Canvas for Video Export (1280x720 HD)
  const width = 1280;
  const height = 720;
  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext('2d');
  if (!ctx) throw new Error('Could not create offscreen video rendering canvas.');

  onProgress?.(5, 'Preparing route map area and free open-source tiles…');

  // 2. Calculate optimal base zoom and bounding box
  const padding = 130;
  let selectedZoom = 16;
  let projectedStops: Array<{ x: number; y: number }> = [];
  let baseScale = 1;

  for (let zoom = 16; zoom >= 4; zoom--) {
    const candidates = locatedStops.map((s) => mercator(s, zoom));
    const minX = Math.min(...candidates.map((p) => p.x));
    const maxX = Math.max(...candidates.map((p) => p.x));
    const minY = Math.min(...candidates.map((p) => p.y));
    const maxY = Math.max(...candidates.map((p) => p.y));
    const spanX = Math.max(256, maxX - minX);
    const spanY = Math.max(256, maxY - minY);
    const candidateScale = Math.min((width - padding * 2) / spanX, (height - padding * 2) / spanY);
    if (candidateScale >= 1 || zoom === 4) {
      selectedZoom = zoom;
      projectedStops = candidates;
      baseScale = candidateScale;
      break;
    }
  }

  const minX = Math.min(...projectedStops.map((p) => p.x));
  const maxX = Math.max(...projectedStops.map((p) => p.x));
  const minY = Math.min(...projectedStops.map((p) => p.y));
  const maxY = Math.max(...projectedStops.map((p) => p.y));

  const overviewCenter = {
    x: (minX + maxX) / 2,
    y: (minY + maxY) / 2,
  };

  // Pre-project all path coordinates
  const projectedPath = routePath.map((p) => mercator(p, selectedZoom));

  // Preload Map Tiles with generous margin for panning & zooming
  const marginTiles = 3;
  const tileStartX = Math.max(0, Math.floor(minX / 256) - marginTiles);
  const tileEndX = Math.min(Math.pow(2, selectedZoom) - 1, Math.ceil(maxX / 256) + marginTiles);
  const tileStartY = Math.max(0, Math.floor(minY / 256) - marginTiles);
  const tileEndY = Math.min(Math.pow(2, selectedZoom) - 1, Math.ceil(maxY / 256) + marginTiles);

  const tileJobs: Promise<{ image: HTMLImageElement; x: number; y: number }>[] = [];
  for (let tx = tileStartX; tx <= tileEndX; tx++) {
    for (let ty = tileStartY; ty <= tileEndY; ty++) {
      tileJobs.push(tileImage(tx, ty, selectedZoom));
    }
  }

  const tileResults = await Promise.all(tileJobs);
  const tileMap = new Map<string, HTMLImageElement>();
  tileResults.forEach((t) => {
    tileMap.set(`${t.x},${t.y}`, t.image);
  });

  // Precalculate cumulative distances
  const segDistances: number[] = [];
  let totalDist = 0;
  for (let i = 0; i < routePath.length - 1; i++) {
    const d = haversineDist(routePath[i], routePath[i + 1]);
    segDistances.push(d);
    totalDist += d;
  }

  // 3. MediaRecorder Setup (MP4 preferred, WebM fallback)
  let mimeType = 'video/mp4';
  let fileExt = 'mp4';
  if (!MediaRecorder.isTypeSupported('video/mp4')) {
    if (MediaRecorder.isTypeSupported('video/mp4;codecs=avc1')) {
      mimeType = 'video/mp4;codecs=avc1';
      fileExt = 'mp4';
    } else if (MediaRecorder.isTypeSupported('video/webm;codecs=vp9')) {
      mimeType = 'video/webm;codecs=vp9';
      fileExt = 'webm';
    } else if (MediaRecorder.isTypeSupported('video/webm')) {
      mimeType = 'video/webm';
      fileExt = 'webm';
    }
  }

  const stream = canvas.captureStream(30);
  const recorder = new MediaRecorder(stream, {
    mimeType,
    videoBitsPerSecond: 4_000_000,
  });

  const recordedChunks: Blob[] = [];
  recorder.ondataavailable = (event) => {
    if (event.data && event.data.size > 0) {
      recordedChunks.push(event.data);
    }
  };

  const recordingPromise = new Promise<{ blob: Blob; mimeType: string; filename: string }>((resolve, reject) => {
    recorder.onstop = () => {
      const blob = new Blob(recordedChunks, { type: mimeType });
      const safeTitle = (routeName || 'delivery-route')
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, '-')
        .replace(/^-+|-+$/g, '');
      const filename = `routekeeper-25s-${safeTitle}-${new Date().toISOString().slice(0, 10)}.${fileExt}`;
      resolve({ blob, mimeType, filename });
    };
    recorder.onerror = (e) => reject(e);
  });

  recorder.start();

  onProgress?.(10, 'Recording 25-second cinematic route video…');

  // 4. 25-Second Animation (750 frames at 30 FPS)
  // Phase 1 (0.0s – 3.0s, frames 0–90): Whole Route Overview
  // Phase 2 (3.0s – 5.5s, frames 90–165): Smooth Zoom-In to Vehicle Start
  // Phase 3 (5.5s – 21.5s, frames 165–645): Dynamic Vehicle Follow Mode
  // Phase 4 (21.5s – 25.0s, frames 645–750): Smooth Zoom-Out to Complete Route
  const totalFrames = 750;
  const followZoomMultiplier = 2.4;

  const startPixel = projectedPath[0];
  const endPixel = projectedPath[projectedPath.length - 1];

  for (let frame = 0; frame <= totalFrames; frame++) {
    // Current camera position and zoom factor
    let camX = overviewCenter.x;
    let camY = overviewCenter.y;
    let camZoomFactor = 1.0;
    let currentPhase: 'overview' | 'zooming-in' | 'driving' | 'zooming-out' = 'overview';
    let drivingProgress = 0;

    if (frame < 90) {
      // Phase 1: Static Route Overview
      currentPhase = 'overview';
      camX = overviewCenter.x;
      camY = overviewCenter.y;
      camZoomFactor = 1.0;
      drivingProgress = 0;
    } else if (frame < 165) {
      // Phase 2: Smooth Zoom In to Start
      currentPhase = 'zooming-in';
      const u = (frame - 90) / 75;
      const ease = cubicEaseInOut(u);
      camX = overviewCenter.x + (startPixel.x - overviewCenter.x) * ease;
      camY = overviewCenter.y + (startPixel.y - overviewCenter.y) * ease;
      camZoomFactor = 1.0 + (followZoomMultiplier - 1.0) * ease;
      drivingProgress = 0;
    } else if (frame <= 645) {
      // Phase 3: Vehicle Follow Driving along route
      currentPhase = 'driving';
      drivingProgress = (frame - 165) / (645 - 165);
      camZoomFactor = followZoomMultiplier;
    } else {
      // Phase 4: Smooth Zoom Out to Whole Route Overview
      currentPhase = 'zooming-out';
      const u = (frame - 645) / 105;
      const ease = cubicEaseInOut(u);
      camX = endPixel.x + (overviewCenter.x - endPixel.x) * ease;
      camY = endPixel.y + (overviewCenter.y - endPixel.y) * ease;
      camZoomFactor = followZoomMultiplier + (1.0 - followZoomMultiplier) * ease;
      drivingProgress = 1.0;
    }

    // Determine current van position and heading
    const progressDist = totalDist * drivingProgress;
    let accumulated = 0;
    let segIdx = 0;
    while (segIdx < segDistances.length && accumulated + segDistances[segIdx] < progressDist) {
      accumulated += segDistances[segIdx];
      segIdx++;
    }

    const currentIdx = Math.min(segIdx, routePath.length - 2);
    const segRemainder = progressDist - accumulated;
    const segLen = segDistances[currentIdx] || 1;
    const ratio = Math.max(0, Math.min(1, segRemainder / segLen));
    const p1 = routePath[currentIdx];
    const p2 = routePath[currentIdx + 1];

    const currentLat = p1.latitude + (p2.latitude - p1.latitude) * ratio;
    const currentLng = p1.longitude + (p2.longitude - p1.longitude) * ratio;
    const currentBearing = calculateBearing(p1, p2);
    const curVanPixel = mercator({ latitude: currentLat, longitude: currentLng }, selectedZoom);

    if (currentPhase === 'driving') {
      camX = curVanPixel.x;
      camY = curVanPixel.y;
    }

    // World to Screen Projection
    const effectiveScale = baseScale * camZoomFactor;
    const worldToScreen = (wx: number, wy: number) => {
      return {
        x: width / 2 + (wx - camX) * effectiveScale,
        y: height / 2 + (wy - camY) * effectiveScale,
      };
    };

    // 1. Draw Map Background
    ctx.fillStyle = '#eef1e8';
    ctx.fillRect(0, 0, width, height);

    // Draw visible tiles
    tileResults.forEach((tile) => {
      const screenPos = worldToScreen(tile.x * 256, tile.y * 256);
      const tileSize = 256 * effectiveScale;
      if (
        screenPos.x + tileSize >= 0 &&
        screenPos.x <= width &&
        screenPos.y + tileSize >= 0 &&
        screenPos.y <= height
      ) {
        ctx.drawImage(tile.image, screenPos.x, screenPos.y, tileSize, tileSize);
      }
    });

    // 2. Draw Full Road Track Background (Casing & Center Line)
    ctx.beginPath();
    projectedPath.forEach((pt, idx) => {
      const pos = worldToScreen(pt.x, pt.y);
      if (idx === 0) ctx.moveTo(pos.x, pos.y);
      else ctx.lineTo(pos.x, pos.y);
    });
    ctx.strokeStyle = '#18382F';
    ctx.lineWidth = Math.max(6, 9 * Math.sqrt(camZoomFactor));
    ctx.lineCap = 'round';
    ctx.lineJoin = 'round';
    ctx.globalAlpha = 0.5;
    ctx.stroke();

    ctx.beginPath();
    projectedPath.forEach((pt, idx) => {
      const pos = worldToScreen(pt.x, pt.y);
      if (idx === 0) ctx.moveTo(pos.x, pos.y);
      else ctx.lineTo(pos.x, pos.y);
    });
    ctx.strokeStyle = '#FFFFFF';
    ctx.lineWidth = Math.max(3, 4.5 * Math.sqrt(camZoomFactor));
    ctx.globalAlpha = 0.85;
    ctx.stroke();
    ctx.globalAlpha = 1;

    // 3. Draw Travelled Trail (Bright Vibrant Green)
    if (drivingProgress > 0) {
      const passedPoints = projectedPath.slice(0, currentIdx + 1);
      passedPoints.push(curVanPixel);

      ctx.beginPath();
      passedPoints.forEach((pt, idx) => {
        const pos = worldToScreen(pt.x, pt.y);
        if (idx === 0) ctx.moveTo(pos.x, pos.y);
        else ctx.lineTo(pos.x, pos.y);
      });
      ctx.strokeStyle = '#10B981';
      ctx.lineWidth = Math.max(5, 7.5 * Math.sqrt(camZoomFactor));
      ctx.lineCap = 'round';
      ctx.lineJoin = 'round';
      ctx.globalAlpha = 0.95;
      ctx.stroke();
      ctx.globalAlpha = 1;
    }

    // 4. Draw Delivery Stop Pins
    locatedStops.forEach((stop, idx) => {
      const stopPixel = projectedStops[idx];
      const pos = worldToScreen(stopPixel.x, stopPixel.y);

      // Check if stop has been reached
      const stopDist = haversineDist(routePath[0], stop);
      const isReached = progressDist >= stopDist - 120 || (drivingProgress >= 0.99);

      const pinRadius = Math.max(14, 16 * Math.min(1.4, Math.sqrt(camZoomFactor)));
      ctx.beginPath();
      ctx.fillStyle = isReached ? '#10B981' : markerColor;
      ctx.arc(pos.x, pos.y, pinRadius, 0, Math.PI * 2);
      ctx.fill();
      ctx.lineWidth = 3;
      ctx.strokeStyle = '#FFFFFF';
      ctx.stroke();

      // Pin Number or Checkmark
      ctx.fillStyle = '#FFFFFF';
      ctx.font = `bold ${Math.round(pinRadius * 0.85)}px system-ui, sans-serif`;
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText(isReached ? '✓' : String(idx + 1), pos.x, pos.y + 1);

      // Stop Name Label
      ctx.fillStyle = '#1A2E27';
      ctx.font = `bold 12px system-ui, sans-serif`;
      ctx.fillText(stop.name, pos.x, pos.y + pinRadius + 14);
    });

    // 5. Draw Animated Delivery Van with Headlights and Road Heading
    const vanScreen = worldToScreen(curVanPixel.x, curVanPixel.y);

    ctx.save();
    ctx.translate(vanScreen.x, vanScreen.y);
    ctx.rotate((currentBearing * Math.PI) / 180);

    // Glowing headlight beam
    const headlightLen = 50 * Math.sqrt(camZoomFactor);
    const gradient = ctx.createRadialGradient(0, 0, 5, 0, -headlightLen, headlightLen * 0.7);
    gradient.addColorStop(0, 'rgba(213, 234, 131, 0.75)');
    gradient.addColorStop(1, 'rgba(213, 234, 131, 0)');
    ctx.fillStyle = gradient;
    ctx.beginPath();
    ctx.moveTo(0, 0);
    ctx.arc(0, 0, headlightLen, -Math.PI / 2 - 0.45, -Math.PI / 2 + 0.45);
    ctx.closePath();
    ctx.fill();

    // Van Body
    const vanScale = Math.max(1, Math.sqrt(camZoomFactor));
    ctx.fillStyle = '#1E453B';
    ctx.strokeStyle = '#D5EA83';
    ctx.lineWidth = 2.5;
    ctx.beginPath();
    ctx.roundRect(-14 * vanScale, -16 * vanScale, 28 * vanScale, 32 * vanScale, 6 * vanScale);
    ctx.fill();
    ctx.stroke();

    // Windshield
    ctx.fillStyle = '#D5EA83';
    ctx.beginPath();
    ctx.roundRect(-10 * vanScale, -13 * vanScale, 20 * vanScale, 7 * vanScale, 3 * vanScale);
    ctx.fill();

    ctx.restore();

    // 6. Navigation Cockpit HUD & Telemetry Overlay
    // Top Left Cockpit Banner
    ctx.fillStyle = 'rgba(26, 46, 39, 0.92)';
    ctx.beginPath();
    ctx.roundRect(24, 20, 440, 84, 16);
    ctx.fill();
    ctx.strokeStyle = '#365349';
    ctx.lineWidth = 1;
    ctx.stroke();

    ctx.fillStyle = '#D5EA83';
    ctx.font = 'bold 11px system-ui, sans-serif';
    ctx.textAlign = 'left';
    ctx.textBaseline = 'top';
    const phaseLabel =
      currentPhase === 'overview'
        ? 'PHASE 1/4 · ROUTE OVERVIEW'
        : currentPhase === 'zooming-in'
        ? 'PHASE 2/4 · ZOOMING TO START LOCATION'
        : currentPhase === 'driving'
        ? 'PHASE 3/4 · EN-ROUTE DELIVERY NAVIGATION'
        : 'PHASE 4/4 · FINAL ROUTE OVERVIEW';
    ctx.fillText(`ROUTEKEEPER · ${phaseLabel}`, 40, 32);

    ctx.fillStyle = '#FFFFFF';
    ctx.font = 'bold 18px system-ui, sans-serif';
    ctx.fillText(routeName || 'Delivery Run', 40, 48);

    ctx.fillStyle = '#E7ECE2';
    ctx.font = '12px system-ui, sans-serif';
    const completedCount = locatedStops.filter((s) => {
      const stopDist = haversineDist(routePath[0], s);
      return progressDist >= stopDist - 120 || drivingProgress >= 0.99;
    }).length;
    ctx.fillText(
      `${completedCount}/${locatedStops.length} stops delivered · ${formatDistance(progressDist)} of ${formatDistance(totalDist)}`,
      40,
      74,
    );

    // Top Right Waze-Style Speedometer HUD
    ctx.fillStyle = 'rgba(26, 46, 39, 0.92)';
    ctx.beginPath();
    ctx.roundRect(width - 160, 20, 136, 84, 16);
    ctx.fill();
    ctx.strokeStyle = '#365349';
    ctx.lineWidth = 1;
    ctx.stroke();

    const simulatedSpeedMph = currentPhase === 'driving' ? 32 : currentPhase === 'zooming-in' ? 15 : 0;
    ctx.fillStyle = '#D5EA83';
    ctx.font = 'bold 28px system-ui, sans-serif';
    ctx.textAlign = 'center';
    ctx.fillText(String(simulatedSpeedMph), width - 92, 48);
    ctx.fillStyle = '#94A3B8';
    ctx.font = 'bold 11px system-ui, sans-serif';
    ctx.fillText('MPH SPEED', width - 92, 78);

    // Bottom Progress & Timestamp Strip
    ctx.fillStyle = 'rgba(26, 46, 39, 0.92)';
    ctx.beginPath();
    ctx.roundRect(24, height - 60, width - 48, 44, 12);
    ctx.fill();

    // Progress bar fill
    const barWidth = width - 260;
    ctx.fillStyle = '#365349';
    ctx.beginPath();
    ctx.roundRect(40, height - 42, barWidth, 8, 4);
    ctx.fill();

    ctx.fillStyle = '#10B981';
    ctx.beginPath();
    ctx.roundRect(40, height - 42, Math.max(8, barWidth * (frame / totalFrames)), 8, 4);
    ctx.fill();

    // Time ticker
    const elapsedSecs = Math.floor(frame / 30);
    const timeStr = `00:${String(elapsedSecs).padStart(2, '0')} / 00:25`;
    ctx.fillStyle = '#FFFFFF';
    ctx.font = 'bold 13px system-ui, sans-serif';
    ctx.textAlign = 'right';
    ctx.fillText(timeStr, width - 44, height - 34);

    const percentDone = Math.round((frame / totalFrames) * 100);
    if (frame % 25 === 0) {
      onProgress?.(
        percentDone,
        `Rendering 25s cinematic video (${percentDone}% · Frame ${frame}/${totalFrames})…`,
      );
    }

    // Yield to let video capture stream record smoothly
    await new Promise((r) => setTimeout(r, 26));
  }

  // Hold final frame for 1 second
  for (let endF = 0; endF < 15; endF++) {
    await new Promise((r) => setTimeout(r, 30));
  }

  onProgress?.(96, 'Finalizing 25-second MP4/WebM video stream…');
  recorder.stop();

  const result = await recordingPromise;
  onProgress?.(100, 'Video export ready!');
  return result;
}
