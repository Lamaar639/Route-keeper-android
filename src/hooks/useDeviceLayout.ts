import { useState, useEffect } from 'react';

export interface DeviceLayoutInfo {
  width: number;
  height: number;
  isMobile: boolean;
  isTablet: boolean;
  isDesktop: boolean;
  isLandscape: boolean;
  isPortrait: boolean;
  isDashmountLandscape: boolean;
}

export function useDeviceLayout(): DeviceLayoutInfo {
  const [layout, setLayout] = useState<DeviceLayoutInfo>(() => {
    if (typeof window === 'undefined') {
      return {
        width: 1200,
        height: 800,
        isMobile: false,
        isTablet: false,
        isDesktop: true,
        isLandscape: true,
        isPortrait: false,
        isDashmountLandscape: false,
      };
    }
    const w = window.innerWidth;
    const h = window.innerHeight;
    const isLandscape = w > h;
    const isMobile = w < 768;
    const isTablet = w >= 768 && w < 1024;
    const isDesktop = w >= 1024;
    const isDashmountLandscape = isLandscape && (h < 600 || (w < 1024 && isLandscape));

    return {
      width: w,
      height: h,
      isMobile,
      isTablet,
      isDesktop,
      isLandscape,
      isPortrait: !isLandscape,
      isDashmountLandscape,
    };
  });

  useEffect(() => {
    const handleResize = () => {
      const w = window.innerWidth;
      const h = window.innerHeight;
      const isLandscape = w > h;
      const isMobile = w < 768;
      const isTablet = w >= 768 && w < 1024;
      const isDesktop = w >= 1024;
      const isDashmountLandscape = isLandscape && (h < 600 || (w < 1024 && isLandscape));

      setLayout({
        width: w,
        height: h,
        isMobile,
        isTablet,
        isDesktop,
        isLandscape,
        isPortrait: !isLandscape,
        isDashmountLandscape,
      });
    };

    window.addEventListener('resize', handleResize);
    window.addEventListener('orientationchange', handleResize);
    return () => {
      window.removeEventListener('resize', handleResize);
      window.removeEventListener('orientationchange', handleResize);
    };
  }, []);

  return layout;
}
