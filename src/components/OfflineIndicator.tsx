import React from 'react';
import { WifiOff, Database } from 'lucide-react';
import { useOnlineStatus } from '../hooks/useOnlineStatus';

export const OfflineIndicator: React.FC = () => {
  const isOnline = useOnlineStatus();

  if (isOnline) {
    return null;
  }

  return (
    <aside
      aria-label="Offline Mode Notification"
      className="fixed bottom-4 left-4 z-[999] flex items-center gap-2 rounded-xl bg-[#1E293B]/95 text-white backdrop-blur-md px-3.5 py-2 text-xs font-semibold shadow-xl border border-slate-700 animate-in slide-in-from-bottom-2"
    >
      <div className="w-2.5 h-2.5 rounded-full bg-amber-400 animate-pulse" />
      <WifiOff className="w-4 h-4 text-amber-400 shrink-0" />
      <div className="flex flex-col">
        <span className="font-bold text-[11px] text-amber-300">Offline Mode Active</span>
        <span className="text-[10px] text-slate-300 flex items-center gap-1">
          <Database className="w-2.5 h-2.5" />
          Cached routes & maps loaded
        </span>
      </div>
    </aside>
  );
};
