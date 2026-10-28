import React, { useState } from 'react';
import { Download, Share2, PlusSquare, X, Check } from 'lucide-react';
import { usePWAInstall } from '../hooks/usePWAInstall';

interface PWAInstallButtonProps {
  className?: string;
  variant?: 'compact' | 'full';
}

export const PWAInstallButton: React.FC<PWAInstallButtonProps> = ({
  className = '',
  variant = 'compact',
}) => {
  const { isInstallable, isInstalled, isIOS, install } = usePWAInstall();
  const [showIOSModal, setShowIOSModal] = useState(false);
  const [installedSuccess, setInstalledSuccess] = useState(false);

  // If already installed or running as standalone PWA, hide
  if (isInstalled) {
    return null;
  }

  const handleInstallClick = async () => {
    if (isInstallable) {
      const success = await install();
      if (success) {
        setInstalledSuccess(true);
        setTimeout(() => setInstalledSuccess(false), 3000);
      }
    } else if (isIOS) {
      setShowIOSModal(true);
    }
  };

  if (!isInstallable && !isIOS) {
    return null;
  }

  if (installedSuccess) {
    return (
      <div className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl bg-[#ECFDF5] text-[#065F46] text-xs font-bold border border-[#A7F3D0]">
        <Check className="w-3.5 h-3.5 text-[#10B981]" />
        <span>Installed!</span>
      </div>
    );
  }

  return (
    <>
      <button
        type="button"
        onClick={handleInstallClick}
        className={`flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-bold transition shadow-xs cursor-pointer border ${
          variant === 'full'
            ? 'bg-[#1E453B] hover:bg-[#152F29] text-white border-[#152F29]'
            : 'bg-[#E7ECE2] hover:bg-[#DDE3D7] text-[#1E453B] border-[#DDE3D7]'
        } ${className}`}
        title={isIOS ? 'Install RouteKeeper on iOS' : 'Install RouteKeeper app for offline use'}
      >
        <Download className="w-3.5 h-3.5 text-[#1E453B] shrink-0" />
        <span>{variant === 'full' ? 'Install Offline App' : 'Install App'}</span>
      </button>

      {/* iOS Safari Installation Guide Modal */}
      {showIOSModal && (
        <div className="fixed inset-0 z-[1200] flex items-center justify-center bg-black/60 backdrop-blur-xs p-4 animate-in fade-in">
          <div className="w-full max-w-sm rounded-2xl bg-white p-5 shadow-2xl border border-[#DDE3D7] flex flex-col gap-4 text-[#1A2E27]">
            <div className="flex items-center justify-between border-b border-[#F3F5ED] pb-3">
              <div className="flex items-center gap-2">
                <span className="w-7 h-7 rounded-xl bg-[#1E453B] text-white flex items-center justify-center text-sm font-bold">
                  🧭
                </span>
                <h3 className="font-bold text-sm text-[#1A2E27]">
                  Install RouteKeeper on iPhone / iPad
                </h3>
              </div>
              <button
                type="button"
                onClick={() => setShowIOSModal(false)}
                className="w-7 h-7 rounded-lg hover:bg-[#F3F5ED] text-[#748078] flex items-center justify-center cursor-pointer"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <div className="text-xs text-[#53635d] flex flex-col gap-3">
              <p>
                Install RouteKeeper to your home screen so your delivery routes and stops remain accessible even with poor signal or no cellular data:
              </p>

              <div className="bg-[#FAFBF7] border border-[#DDE3D7] rounded-xl p-3 flex flex-col gap-2.5">
                <div className="flex items-center gap-2.5">
                  <span className="w-6 h-6 rounded-lg bg-[#E7ECE2] text-[#1E453B] font-bold text-xs flex items-center justify-center shrink-0">
                    1
                  </span>
                  <span className="flex items-center gap-1.5">
                    Tap the <Share2 className="w-4 h-4 text-blue-600 inline" /> <strong>Share</strong> button in Safari's bottom toolbar.
                  </span>
                </div>
                <div className="flex items-center gap-2.5">
                  <span className="w-6 h-6 rounded-lg bg-[#E7ECE2] text-[#1E453B] font-bold text-xs flex items-center justify-center shrink-0">
                    2
                  </span>
                  <span className="flex items-center gap-1.5">
                    Scroll down and tap <PlusSquare className="w-4 h-4 text-[#1E453B] inline" /> <strong>Add to Home Screen</strong>.
                  </span>
                </div>
                <div className="flex items-center gap-2.5">
                  <span className="w-6 h-6 rounded-lg bg-[#E7ECE2] text-[#1E453B] font-bold text-xs flex items-center justify-center shrink-0">
                    3
                  </span>
                  <span>
                    Tap <strong>Add</strong> in the top-right corner to finish.
                  </span>
                </div>
              </div>
            </div>

            <button
              type="button"
              onClick={() => setShowIOSModal(false)}
              className="w-full py-2.5 rounded-xl bg-[#1E453B] hover:bg-[#152F29] text-white text-xs font-bold cursor-pointer transition shadow-xs"
            >
              Got it
            </button>
          </div>
        </div>
      )}
    </>
  );
};
