import React, { useState, useRef } from 'react';
import {
  Video,
  X,
  Play,
  Clock,
  Smartphone,
  Monitor,
  Square,
  Check,
  Eye,
  Sliders,
  Gauge,
  Navigation,
  Sparkles,
  AlertTriangle,
  Download,
  RotateCcw,
} from 'lucide-react';
import type { RouteDraft, SavedRoute } from '../types/route';
import {
  exportRouteAnimationVideo,
  type VideoAspectRatio,
  type VideoCameraMode,
  type VideoDisplaySettings,
  DEFAULT_DISPLAY_SETTINGS,
} from '../services/videoExporter';

interface VideoExportModalProps {
  isOpen: boolean;
  onClose: () => void;
  route: RouteDraft | SavedRoute | null;
  markerColor?: string;
  lineColor?: string;
  onSuccessToast?: (msg: string) => void;
}

export const VideoExportModal: React.FC<VideoExportModalProps> = ({
  isOpen,
  onClose,
  route,
  markerColor = '#1E453B',
  lineColor = '#D5EA83',
  onSuccessToast,
}) => {
  const [duration, setDuration] = useState<number>(25);
  const [aspectRatio, setAspectRatio] = useState<VideoAspectRatio>('9:16');
  const [cameraMode, setCameraMode] = useState<VideoCameraMode>('dynamic-follow');
  const [displaySettings, setDisplaySettings] = useState<VideoDisplaySettings>(DEFAULT_DISPLAY_SETTINGS);

  const [isExporting, setIsExporting] = useState(false);
  const [progress, setProgress] = useState(0);
  const [stage, setStage] = useState('');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const abortControllerRef = useRef<AbortController | null>(null);

  if (!isOpen || !route) return null;

  const locatedStops = route.stops.filter(
    (s) => s.latitude !== null && s.longitude !== null,
  );

  const handleToggleSetting = (key: keyof VideoDisplaySettings) => {
    setDisplaySettings((prev) => ({
      ...prev,
      [key]: !prev[key],
    }));
  };

  const handleStartExport = async () => {
    if (locatedStops.length < 2) {
      setErrorMessage('At least 2 located delivery stops are required to export a video.');
      return;
    }

    setErrorMessage(null);
    setIsExporting(true);
    setProgress(2);
    setStage('Initializing canvas and loading OpenStreetMap tiles…');

    const controller = new AbortController();
    abortControllerRef.current = controller;

    try {
      const result = await exportRouteAnimationVideo({
        routeName: route.name,
        stops: route.stops,
        path: route.path || [],
        durationSeconds: duration,
        aspectRatio,
        cameraMode,
        displaySettings,
        markerColor,
        lineColor,
        signal: controller.signal,
        onProgress: (pct, stageText) => {
          setProgress(pct);
          setStage(stageText);
        },
      });

      // Trigger automatic browser download
      const url = URL.createObjectURL(result.blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = result.filename;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      setTimeout(() => URL.revokeObjectURL(url), 6000);

      onSuccessToast?.(`Exported ${result.filename}!`);
      setIsExporting(false);
      onClose();
    } catch (err) {
      if (controller.signal.aborted) {
        setStage('Export cancelled');
      } else {
        const msg = err instanceof Error ? err.message : 'Video export failed';
        setErrorMessage(msg);
      }
      setIsExporting(false);
    } finally {
      abortControllerRef.current = null;
    }
  };

  const handleCancelExport = () => {
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
      abortControllerRef.current = null;
    }
    setIsExporting(false);
  };

  return (
    <div className="fixed inset-0 bg-black/65 backdrop-blur-xs z-50 flex items-center justify-center p-3 sm:p-4 animate-in fade-in duration-200">
      <div className="bg-white w-full max-w-lg rounded-3xl shadow-2xl flex flex-col max-h-[92dvh] overflow-hidden border border-[#DDE3D7]">
        {/* Header */}
        <div className="p-4 sm:p-5 border-b border-[#DDE3D7] flex items-center justify-between shrink-0 bg-[#FAFBF7]">
          <div className="flex items-center gap-2.5 min-w-0">
            <div className="w-10 h-10 rounded-2xl bg-[#1E453B] text-[#D5EA83] flex items-center justify-center shrink-0 shadow-xs">
              <Video className="w-5 h-5 stroke-[2.2]" />
            </div>
            <div className="min-w-0">
              <h2 className="text-base sm:text-lg font-black text-[#1A2E27] tracking-tight truncate">
                Export Route Video
              </h2>
              <p className="text-xs text-[#748078] truncate">
                {route.name} · {locatedStops.length} mapped stops
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={isExporting ? handleCancelExport : onClose}
            className="w-8 h-8 rounded-full hover:bg-[#E7ECE2] text-[#748078] flex items-center justify-center cursor-pointer transition shrink-0"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        {/* Scrollable Content Body */}
        <div className="p-4 sm:p-5 overflow-y-auto flex flex-col gap-5 modal-scroll-container">
          {errorMessage && (
            <div className="p-3 bg-red-50 border border-red-200 rounded-xl text-xs text-red-800 flex items-start gap-2 animate-in fade-in">
              <AlertTriangle className="w-4 h-4 text-red-600 shrink-0 mt-0.5" />
              <span>{errorMessage}</span>
            </div>
          )}

          {/* Export in Progress Overlay Card */}
          {isExporting ? (
            <div className="p-5 rounded-2xl bg-[#FAFBF7] border border-[#1E453B]/20 flex flex-col gap-4 text-center items-center py-8">
              <div className="relative">
                <div className="w-16 h-16 rounded-full border-4 border-[#1E453B]/20 border-t-[#1E453B] animate-spin flex items-center justify-center" />
                <div className="absolute inset-0 flex items-center justify-center text-xs font-black text-[#1E453B]">
                  {progress}%
                </div>
              </div>
              <div className="flex flex-col gap-1 max-w-sm">
                <span className="text-sm font-black text-[#1A2E27]">
                  Rendering Cinematic Route Animation
                </span>
                <span className="text-xs text-[#748078] leading-relaxed">
                  {stage}
                </span>
              </div>
              {/* Progress bar */}
              <div className="w-full bg-[#E7ECE2] h-2.5 rounded-full overflow-hidden">
                <div
                  className="bg-[#10B981] h-full rounded-full transition-all duration-200"
                  style={{ width: `${progress}%` }}
                />
              </div>
              <p className="text-[11px] text-[#748078]">
                Capturing 30 frames per second at full resolution. Please keep this tab active.
              </p>
              <button
                type="button"
                onClick={handleCancelExport}
                className="mt-2 px-4 py-2 rounded-xl border border-red-200 text-red-700 bg-red-50 hover:bg-red-100 text-xs font-bold transition cursor-pointer"
              >
                Cancel Export
              </button>
            </div>
          ) : (
            <>
              {/* 1. Video Duration Selection */}
              <div className="flex flex-col gap-2">
                <div className="flex items-center justify-between">
                  <label className="text-xs font-black tracking-wider text-[#1A2E27] uppercase flex items-center gap-1.5">
                    <Clock className="w-3.5 h-3.5 text-[#1E453B]" />
                    <span>Duration & Playback Speed</span>
                  </label>
                  <span className="text-xs font-extrabold text-[#1E453B] bg-[#E7ECE2] px-2 py-0.5 rounded-md">
                    {duration} seconds
                  </span>
                </div>

                <div className="grid grid-cols-4 gap-2">
                  {[
                    { sec: 10, label: '10s', sub: 'Fast Story' },
                    { sec: 15, label: '15s', sub: 'Short/Reel' },
                    { sec: 25, label: '25s', sub: 'Cinematic' },
                    { sec: 40, label: '40s', sub: 'Detailed' },
                  ].map((item) => (
                    <button
                      key={item.sec}
                      type="button"
                      onClick={() => setDuration(item.sec)}
                      className={`p-2.5 rounded-xl border flex flex-col items-center justify-center transition cursor-pointer ${
                        duration === item.sec
                          ? 'bg-[#1E453B] text-white border-[#1E453B] shadow-xs'
                          : 'bg-white border-[#DDE3D7] text-[#1A2E27] hover:bg-[#FAFBF7]'
                      }`}
                    >
                      <span className="text-sm font-black">{item.label}</span>
                      <span className={`text-[10px] ${duration === item.sec ? 'text-[#D5EA83]' : 'text-[#748078]'}`}>
                        {item.sub}
                      </span>
                    </button>
                  ))}
                </div>

                {/* Duration Fine-Tune Slider */}
                <div className="flex items-center gap-3 pt-1">
                  <span className="text-[10px] text-[#748078] font-bold">5s</span>
                  <input
                    type="range"
                    min={5}
                    max={60}
                    step={1}
                    value={duration}
                    onChange={(e) => setDuration(Number(e.target.value))}
                    className="flex-1 accent-[#1E453B] cursor-pointer"
                  />
                  <span className="text-[10px] text-[#748078] font-bold">60s</span>
                </div>
              </div>

              {/* 2. Aspect Ratio & Framing */}
              <div className="flex flex-col gap-2">
                <label className="text-xs font-black tracking-wider text-[#1A2E27] uppercase flex items-center gap-1.5">
                  <Smartphone className="w-3.5 h-3.5 text-[#1E453B]" />
                  <span>Framing & Device Aspect Ratio</span>
                </label>
                <div className="grid grid-cols-3 gap-2">
                  <button
                    type="button"
                    onClick={() => setAspectRatio('9:16')}
                    className={`p-3 rounded-2xl border flex flex-col items-center gap-1.5 transition text-center cursor-pointer ${
                      aspectRatio === '9:16'
                        ? 'bg-[#1E453B] text-white border-[#1E453B] shadow-xs'
                        : 'bg-white border-[#DDE3D7] text-[#1A2E27] hover:bg-[#FAFBF7]'
                    }`}
                  >
                    <Smartphone className="w-5 h-5 stroke-[2.2]" />
                    <div className="flex flex-col">
                      <span className="text-xs font-black">9:16 Vertical</span>
                      <span className={`text-[10px] ${aspectRatio === '9:16' ? 'text-[#D5EA83]' : 'text-[#748078]'}`}>
                        Reels & Stories
                      </span>
                    </div>
                  </button>

                  <button
                    type="button"
                    onClick={() => setAspectRatio('16:9')}
                    className={`p-3 rounded-2xl border flex flex-col items-center gap-1.5 transition text-center cursor-pointer ${
                      aspectRatio === '16:9'
                        ? 'bg-[#1E453B] text-white border-[#1E453B] shadow-xs'
                        : 'bg-white border-[#DDE3D7] text-[#1A2E27] hover:bg-[#FAFBF7]'
                    }`}
                  >
                    <Monitor className="w-5 h-5 stroke-[2.2]" />
                    <div className="flex flex-col">
                      <span className="text-xs font-black">16:9 Landscape</span>
                      <span className={`text-[10px] ${aspectRatio === '16:9' ? 'text-[#D5EA83]' : 'text-[#748078]'}`}>
                        Desktop / TV
                      </span>
                    </div>
                  </button>

                  <button
                    type="button"
                    onClick={() => setAspectRatio('1:1')}
                    className={`p-3 rounded-2xl border flex flex-col items-center gap-1.5 transition text-center cursor-pointer ${
                      aspectRatio === '1:1'
                        ? 'bg-[#1E453B] text-white border-[#1E453B] shadow-xs'
                        : 'bg-white border-[#DDE3D7] text-[#1A2E27] hover:bg-[#FAFBF7]'
                    }`}
                  >
                    <Square className="w-5 h-5 stroke-[2.2]" />
                    <div className="flex flex-col">
                      <span className="text-xs font-black">1:1 Square</span>
                      <span className={`text-[10px] ${aspectRatio === '1:1' ? 'text-[#D5EA83]' : 'text-[#748078]'}`}>
                        Social Feed
                      </span>
                    </div>
                  </button>
                </div>
              </div>

              {/* 3. Camera Tracking Mode */}
              <div className="flex flex-col gap-2">
                <label className="text-xs font-black tracking-wider text-[#1A2E27] uppercase flex items-center gap-1.5">
                  <Navigation className="w-3.5 h-3.5 text-[#1E453B]" />
                  <span>Camera Motion Style</span>
                </label>
                <div className="grid grid-cols-2 gap-2">
                  <button
                    type="button"
                    onClick={() => setCameraMode('dynamic-follow')}
                    className={`p-3 rounded-2xl border flex flex-col items-start gap-1 transition text-left cursor-pointer ${
                      cameraMode === 'dynamic-follow'
                        ? 'bg-[#1E453B] text-white border-[#1E453B] shadow-xs'
                        : 'bg-white border-[#DDE3D7] text-[#1A2E27] hover:bg-[#FAFBF7]'
                    }`}
                  >
                    <div className="flex items-center gap-1.5 font-black text-xs">
                      <Sparkles className="w-3.5 h-3.5 text-[#D5EA83]" />
                      <span>Dynamic Follow</span>
                    </div>
                    <span className={`text-[11px] leading-tight ${cameraMode === 'dynamic-follow' ? 'text-[#E7ECE2]' : 'text-[#748078]'}`}>
                      Smooth zoom-in, tracks delivery van along curves, zooms out at destination
                    </span>
                  </button>

                  <button
                    type="button"
                    onClick={() => setCameraMode('static-overview')}
                    className={`p-3 rounded-2xl border flex flex-col items-start gap-1 transition text-left cursor-pointer ${
                      cameraMode === 'static-overview'
                        ? 'bg-[#1E453B] text-white border-[#1E453B] shadow-xs'
                        : 'bg-white border-[#DDE3D7] text-[#1A2E27] hover:bg-[#FAFBF7]'
                    }`}
                  >
                    <div className="flex items-center gap-1.5 font-black text-xs">
                      <Gauge className="w-3.5 h-3.5 text-[#D5EA83]" />
                      <span>Full Territory Overview</span>
                    </div>
                    <span className={`text-[11px] leading-tight ${cameraMode === 'static-overview' ? 'text-[#E7ECE2]' : 'text-[#748078]'}`}>
                      Entire route stationary; van navigates across city in a single bird's-eye frame
                    </span>
                  </button>
                </div>
              </div>

              {/* 4. Display & Overlays Checklist (What to show) */}
              <div className="flex flex-col gap-2">
                <div className="flex items-center justify-between">
                  <label className="text-xs font-black tracking-wider text-[#1A2E27] uppercase flex items-center gap-1.5">
                    <Eye className="w-3.5 h-3.5 text-[#1E453B]" />
                    <span>What To Display & Show</span>
                  </label>
                  <button
                    type="button"
                    onClick={() => setDisplaySettings(DEFAULT_DISPLAY_SETTINGS)}
                    className="text-[11px] font-bold text-[#1E453B] hover:underline flex items-center gap-1 cursor-pointer"
                  >
                    <RotateCcw className="w-3 h-3" />
                    <span>Reset All</span>
                  </button>
                </div>

                <div className="divide-y divide-[#F3F5ED] border border-[#DDE3D7] rounded-2xl bg-white px-3 py-1 text-xs">
                  {[
                    {
                      key: 'showVan' as const,
                      title: 'Animated Delivery Van',
                      desc: 'Directional van model with glowing headlights & rotation along street angles',
                    },
                    {
                      key: 'showTrail' as const,
                      title: 'Green Completed Road Trail',
                      desc: 'Vibrant green path showing road sections already visited',
                    },
                    {
                      key: 'showRoadTrack' as const,
                      title: 'Full Planned Road Track',
                      desc: 'Distinct outer casing line showing entire planned course',
                    },
                    {
                      key: 'showStopPins' as const,
                      title: 'Numbered Stop Pins',
                      desc: 'Numbered delivery markers that turn into green checkmarks when reached',
                    },
                    {
                      key: 'showStopNames' as const,
                      title: 'Stop Names & Labels',
                      desc: 'Customer destination name labels positioned beneath each pin',
                    },
                    {
                      key: 'showTimeWindows' as const,
                      title: 'Customer Booked Time Slot Badges',
                      desc: 'Prominent scheduled delivery slot pills (e.g. 09:00 - 10:00) next to stops',
                    },
                    {
                      key: 'showCockpitHeader' as const,
                      title: 'Top Navigation Cockpit Banner',
                      desc: 'Route name, current phase indicator, and stops completed count',
                    },
                    {
                      key: 'showSpeedometer' as const,
                      title: 'Speedometer HUD',
                      desc: 'Dynamic MPH driving speed gauge in top corner',
                    },
                    {
                      key: 'showProgressBar' as const,
                      title: 'Bottom Progress Scrub Bar & Clock',
                      desc: 'Visual timeline with elapsed and total duration ticker',
                    },
                    {
                      key: 'showBranding' as const,
                      title: 'RouteKeeper Watermark',
                      desc: 'Clean professional RouteKeeper logo badge in video header',
                    },
                  ].map((item) => (
                    <div
                      key={item.key}
                      onClick={() => handleToggleSetting(item.key)}
                      className="py-2.5 flex items-center justify-between gap-3 cursor-pointer hover:bg-[#F9FAF8] px-1 rounded-xl transition"
                    >
                      <div className="flex flex-col min-w-0">
                        <span className="font-bold text-[#1A2E27]">{item.title}</span>
                        <span className="text-[11px] text-[#748078] leading-tight">{item.desc}</span>
                      </div>
                      <div
                        className={`w-5 h-5 rounded-md flex items-center justify-center shrink-0 transition ${
                          displaySettings[item.key]
                            ? 'bg-[#10B981] text-white'
                            : 'border-2 border-[#CBD5E1] bg-white'
                        }`}
                      >
                        {displaySettings[item.key] && <Check className="w-3.5 h-3.5 stroke-[3]" />}
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </>
          )}
        </div>

        {/* Action Footer */}
        <div className="p-4 sm:p-5 border-t border-[#DDE3D7] bg-[#FAFBF7] flex items-center gap-3 shrink-0">
          <button
            type="button"
            onClick={onClose}
            disabled={isExporting}
            className="flex-1 py-3 rounded-2xl border border-[#DDE3D7] bg-white text-[#1A2E27] font-bold text-xs hover:bg-[#F3F5ED] transition cursor-pointer disabled:opacity-50"
          >
            Cancel
          </button>
          <button
            type="button"
            onClick={handleStartExport}
            disabled={isExporting || locatedStops.length < 2}
            className="flex-2 py-3 px-4 rounded-2xl bg-[#1E453B] text-[#D5EA83] font-black text-xs hover:bg-[#152F29] shadow-md transition flex items-center justify-center gap-2 cursor-pointer disabled:opacity-50"
          >
            <Download className="w-4 h-4 stroke-[2.5]" />
            <span>
              {isExporting ? 'Generating Video…' : `Start Video Export (${duration}s · ${aspectRatio})`}
            </span>
          </button>
        </div>
      </div>
    </div>
  );
};

export default VideoExportModal;
