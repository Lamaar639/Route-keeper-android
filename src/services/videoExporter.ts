import type { Coordinate, DeliveryStop } from '../types/route';
import { formatDistance } from './routing';

export type VideoAspectRatio = '16:9' | '9:16' | '1:1';
export type VideoCameraMode = 'dynamic-follow' | 'static-overview';

export interface VideoDisplaySettings {
  showVan: boolean;
  showTrail: boolean;
  showRoadTrack: boolean;
  showStopPins: boolean;
  showStopNames: boolean;
  showTimeWindows: boolean;
  showCockpitHeader: boolean;
  showSpeedometer: boolean;
  showProgressBar: boolean;
  showBranding: boolean;
}

export const DEFAULT_DISPLAY_SETTINGS: VideoDisplaySettings = {
  showVan: true,
  showTrail: true,
  showRoadTrack: true,
  showStopPins: true,
  showStopNames: true,
  showTimeWindows: true,
  showCockpitHeader: true,
  showSpeedometer: true,
  showProgressBar: true,
  showBranding: true,
};

export interface AnimationExportOptions {
  routeName: string;
  stops: DeliveryStop[];
  path: Coordinate[];
  durationSeconds?: number;
  aspectRatio?: VideoAspectRatio;
  cameraMode?: VideoCameraMode;
  displaySettings?: Partial<VideoDisplaySettings>;
  markerColor?: string;
  lineColor?: string;
  onProgress?: (progressPercent: number, stage: string) => void;
  signal?: AbortSignal;
}

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

export async function exportRouteAnimationVideo({
  routeName,
  stops,
  path,
  durationSeconds = 25,
  aspectRatio = '16:9',
  cameraMode = 'dynamic-follow',
  displaySettings: partialSettings,
  markerColor = '#1E453B',
  lineColor = '#D5EA83',
  onProgress,
  signal,
}: AnimationExportOptions): Promise<{ blob: Blob; mimeType: string; filename: string }> {
  const displaySettings: VideoDisplaySettings = {
    ...DEFAULT_DISPLAY_SETTINGS,
    ...partialSettings,
  };

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

  // 1. Dimensions based on Aspect Ratio
  let width = 1280;
  let height = 720;
  if (aspectRatio === '9:16') {
    width = 720;
    height = 1280;
  } else if (aspectRatio === '1:1') {
    width = 720;
    height = 720;
  }

  const canvas = document.createElement('canvas');
  canvas.width = width;
  canvas.height = height;
  const ctx = canvas.getContext('2d');
  if (!ctx) throw new Error('Could not create offscreen video rendering canvas.');

  onProgress?.(5, 'Preparing route map area and free open-source tiles…');

  // 2. Calculate optimal base zoom and bounding box
  const padding = aspectRatio === '9:16' ? 90 : 130;
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

  const projectedPath = routePath.map((p) => mercator(p, selectedZoom));

  // Preload Map Tiles with margin
  const marginTiles = aspectRatio === '9:16' ? 4 : 3;
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

  // Precalculate cumulative distances
  const segDistances: number[] = [];
  let totalDist = 0;
  for (let i = 0; i < routePath.length - 1; i++) {
    const d = haversineDist(routePath[i], routePath[i + 1]);
    segDistances.push(d);
    totalDist += d;
  }

  // 3. MediaRecorder Setup
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

  const cleanDuration = Math.max(5, Math.min(60, Math.round(durationSeconds)));
  const recordingPromise = new Promise<{ blob: Blob; mimeType: string; filename: string }>((resolve, reject) => {
    recorder.onstop = () => {
      const blob = new Blob(recordedChunks, { type: mimeType });
      const safeTitle = (routeName || 'delivery-route')
        .toLowerCase()
        .replace(/[^a-z0-9]+/g, '-')
        .replace(/^-+|-+$/g, '');
      const aspectTag = aspectRatio.replace(':', 'x');
      const filename = `routekeeper-${cleanDuration}s-${aspectTag}-${safeTitle}-${new Date().toISOString().slice(0, 10)}.${fileExt}`;
      resolve({ blob, mimeType, filename });
    };
    recorder.onerror = (e) => reject(e);
  });

  recorder.start();

  onProgress?.(10, `Recording ${cleanDuration}-second video (${aspectRatio} ${cameraMode === 'dynamic-follow' ? 'Follow Cam' : 'Overview'})…`);

  // 4. Configurable Frame Count (30 FPS)
  const totalFrames = Math.max(150, cleanDuration * 30);
  const followZoomMultiplier = aspectRatio === '9:16' ? 2.1 : 2.4;

  const startPixel = projectedPath[0];
  const endPixel = projectedPath[projectedPath.length - 1];

  // Phase distribution:
  // Phase 1 (Overview): 12%
  // Phase 2 (Zoom to start): 10%
  // Phase 3 (Driving follow): 64%
  // Phase 4 (Zoom out overview): 14%
  const phase1Frames = Math.round(totalFrames * 0.12);
  const phase2Frames = Math.round(totalFrames * 0.10);
  const phase4Frames = Math.round(totalFrames * 0.14);
  const phase3Frames = totalFrames - phase1Frames - phase2Frames - phase4Frames;

  const p1End = phase1Frames;
  const p2End = p1End + phase2Frames;
  const p3End = p2End + phase3Frames;

  for (let frame = 0; frame <= totalFrames; frame++) {
    if (signal?.aborted) {
      recorder.stop();
      throw new Error('Video export was cancelled by user.');
    }

    let camX = overviewCenter.x;
    let camY = overviewCenter.y;
    let camZoomFactor = 1.0;
    let currentPhase: 'overview' | 'zooming-in' | 'driving' | 'zooming-out' = 'overview';
    let drivingProgress = 0;

    if (cameraMode === 'static-overview') {
      // Entire route is always visible, vehicle traverses smoothly
      camX = overviewCenter.x;
      camY = overviewCenter.y;
      camZoomFactor = 1.0;
      drivingProgress = Math.max(0, Math.min(1, frame / totalFrames));
      currentPhase = 'driving';
    } else {
      // Dynamic Cinematic Follow
      if (frame < p1End) {
        currentPhase = 'overview';
        camX = overviewCenter.x;
        camY = overviewCenter.y;
        camZoomFactor = 1.0;
        drivingProgress = 0;
      } else if (frame < p2End) {
        currentPhase = 'zooming-in';
        const u = (frame - p1End) / Math.max(1, phase2Frames);
        const ease = cubicEaseInOut(u);
        camX = overviewCenter.x + (startPixel.x - overviewCenter.x) * ease;
        camY = overviewCenter.y + (startPixel.y - overviewCenter.y) * ease;
        camZoomFactor = 1.0 + (followZoomMultiplier - 1.0) * ease;
        drivingProgress = 0;
      } else if (frame <= p3End) {
        currentPhase = 'driving';
        drivingProgress = (frame - p2End) / Math.max(1, phase3Frames);
        camZoomFactor = followZoomMultiplier;
      } else {
        currentPhase = 'zooming-out';
        const u = (frame - p3End) / Math.max(1, phase4Frames);
        const ease = cubicEaseInOut(u);
        camX = endPixel.x + (overviewCenter.x - endPixel.x) * ease;
        camY = endPixel.y + (overviewCenter.y - endPixel.y) * ease;
        camZoomFactor = followZoomMultiplier + (1.0 - followZoomMultiplier) * ease;
        drivingProgress = 1.0;
      }
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

    if (cameraMode === 'dynamic-follow' && currentPhase === 'driving') {
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
    if (displaySettings.showRoadTrack) {
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
    }

    // 3. Draw Travelled Trail (Bright Vibrant Green)
    if (displaySettings.showTrail && drivingProgress > 0) {
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
    if (displaySettings.showStopPins) {
      locatedStops.forEach((stop, idx) => {
        const stopPixel = projectedStops[idx];
        const pos = worldToScreen(stopPixel.x, stopPixel.y);

        const stopDist = haversineDist(routePath[0], stop);
        const isReached = progressDist >= stopDist - 120 || drivingProgress >= 0.99;

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
        if (displaySettings.showStopNames) {
          ctx.fillStyle = '#1A2E27';
          ctx.font = `bold ${aspectRatio === '9:16' ? 14 : 12}px system-ui, sans-serif`;
          ctx.fillText(stop.name, pos.x, pos.y + pinRadius + 14);

          // Time Slot Badge
          if (displaySettings.showTimeWindows && stop.timeWindow) {
            const slotText = stop.timeWindow.label || `${stop.timeWindow.startTime}–${stop.timeWindow.endTime}`;
            ctx.font = 'bold 10px system-ui, sans-serif';
            const badgeW = ctx.measureText(slotText).width + 12;
            const badgeH = 18;
            const badgeY = pos.y + pinRadius + 24;

            ctx.fillStyle = '#4338CA';
            ctx.beginPath();
            ctx.roundRect(pos.x - badgeW / 2, badgeY, badgeW, badgeH, 6);
            ctx.fill();

            ctx.fillStyle = '#FFFFFF';
            ctx.fillText(`🕒 ${slotText}`, pos.x, badgeY + badgeH / 2 + 1);
          }
        }
      });
    }

    // 5. Draw Animated Delivery Van with Headlights and Road Heading
    if (displaySettings.showVan) {
      const vanScreen = worldToScreen(curVanPixel.x, curVanPixel.y);

      ctx.save();
      ctx.translate(vanScreen.x, vanScreen.y);
      ctx.rotate((currentBearing * Math.PI) / 180);

      // Headlight beam
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
    }

    // 6. Navigation Cockpit HUD & Telemetry Overlays
    const isVertical = aspectRatio === '9:16';
    const isSquare = aspectRatio === '1:1';

    // Top Cockpit Banner
    if (displaySettings.showCockpitHeader) {
      const bannerX = 20;
      const bannerY = isVertical ? 32 : 20;
      const bannerW = isVertical ? width - 40 : isSquare ? width - 150 : 440;
      const bannerH = isVertical ? 92 : 84;

      ctx.fillStyle = 'rgba(26, 46, 39, 0.94)';
      ctx.beginPath();
      ctx.roundRect(bannerX, bannerY, bannerW, bannerH, 16);
      ctx.fill();
      ctx.strokeStyle = '#365349';
      ctx.lineWidth = 1;
      ctx.stroke();

      ctx.fillStyle = '#D5EA83';
      ctx.font = 'bold 11px system-ui, sans-serif';
      ctx.textAlign = 'left';
      ctx.textBaseline = 'top';

      const phaseLabel =
        cameraMode === 'static-overview'
          ? 'TERRITORY DISPATCH PREVIEW'
          : currentPhase === 'overview'
          ? 'PHASE 1/4 · ROUTE OVERVIEW'
          : currentPhase === 'zooming-in'
          ? 'PHASE 2/4 · ZOOMING TO START'
          : currentPhase === 'driving'
          ? 'PHASE 3/4 · ACTIVE NAVIGATION'
          : 'PHASE 4/4 · FINAL ROUTE SUMMARY';

      const brandTitle = displaySettings.showBranding ? `ROUTEKEEPER · ${phaseLabel}` : phaseLabel;
      ctx.fillText(brandTitle, bannerX + 16, bannerY + 14);

      ctx.fillStyle = '#FFFFFF';
      ctx.font = 'bold 18px system-ui, sans-serif';
      ctx.fillText(routeName || 'Delivery Run', bannerX + 16, bannerY + 30);

      ctx.fillStyle = '#E7ECE2';
      ctx.font = '12px system-ui, sans-serif';
      const completedCount = locatedStops.filter((s) => {
        const stopDist = haversineDist(routePath[0], s);
        return progressDist >= stopDist - 120 || drivingProgress >= 0.99;
      }).length;
      ctx.fillText(
        `${completedCount}/${locatedStops.length} stops reached · ${formatDistance(progressDist)} / ${formatDistance(totalDist)}`,
        bannerX + 16,
        bannerY + 56,
      );
    }

    // Top Right Speedometer HUD
    if (displaySettings.showSpeedometer) {
      const speedX = isVertical ? width - 140 : isSquare ? width - 116 : width - 160;
      const speedY = isVertical ? 134 : 20;
      const speedW = isVertical ? 120 : isSquare ? 96 : 136;
      const speedH = isVertical ? 74 : 84;

      ctx.fillStyle = 'rgba(26, 46, 39, 0.94)';
      ctx.beginPath();
      ctx.roundRect(speedX, speedY, speedW, speedH, 16);
      ctx.fill();
      ctx.strokeStyle = '#365349';
      ctx.lineWidth = 1;
      ctx.stroke();

      const simulatedSpeedMph = currentPhase === 'driving' ? 32 : currentPhase === 'zooming-in' ? 16 : 0;
      ctx.fillStyle = '#D5EA83';
      ctx.font = `bold ${isSquare ? '22px' : '28px'} system-ui, sans-serif`;
      ctx.textAlign = 'center';
      ctx.fillText(String(simulatedSpeedMph), speedX + speedW / 2, speedY + (isSquare ? 12 : 16));

      ctx.fillStyle = '#94A3B8';
      ctx.font = 'bold 10px system-ui, sans-serif';
      ctx.fillText('MPH SPEED', speedX + speedW / 2, speedY + (isSquare ? 48 : 52));
    }

    // Bottom Progress & Timestamp Strip
    if (displaySettings.showProgressBar) {
      const bottomY = isVertical ? height - 76 : height - 60;
      const bottomH = isVertical ? 50 : 44;
      ctx.fillStyle = 'rgba(26, 46, 39, 0.94)';
      ctx.beginPath();
      ctx.roundRect(20, bottomY, width - 40, bottomH, 12);
      ctx.fill();

      // Progress bar fill
      const barX = 36;
      const timeWidth = 140;
      const barWidth = width - 40 - timeWidth - 36;
      ctx.fillStyle = '#365349';
      ctx.beginPath();
      ctx.roundRect(barX, bottomY + 18, barWidth, 8, 4);
      ctx.fill();

      ctx.fillStyle = '#10B981';
      ctx.beginPath();
      ctx.roundRect(barX, bottomY + 18, Math.max(8, barWidth * (frame / totalFrames)), 8, 4);
      ctx.fill();

      // Time ticker
      const elapsedSecs = Math.floor(frame / 30);
      const totalSecs = Math.floor(totalFrames / 30);
      const timeStr = `00:${String(elapsedSecs).padStart(2, '0')} / 00:${String(totalSecs).padStart(2, '0')}`;
      ctx.fillStyle = '#FFFFFF';
      ctx.font = 'bold 13px system-ui, sans-serif';
      ctx.textAlign = 'right';
      ctx.fillText(timeStr, width - 36, bottomY + 14);
    }

    // Progress callback
    const percentDone = Math.round((frame / totalFrames) * 100);
    if (frame % 25 === 0) {
      onProgress?.(
        percentDone,
        `Rendering ${cleanDuration}s video (${percentDone}% · Frame ${frame}/${totalFrames})…`,
      );
    }

    // Yield to let video capture stream record smoothly
    await new Promise((r) => setTimeout(r, 26));
  }

  // Hold final frame for a moment
  for (let endF = 0; endF < 12; endF++) {
    if (signal?.aborted) break;
    await new Promise((r) => setTimeout(r, 30));
  }

  onProgress?.(96, `Finalizing ${cleanDuration}-second MP4/WebM video stream…`);
  recorder.stop();

  const result = await recordingPromise;
  onProgress?.(100, 'Video export ready!');
  return result;
}
