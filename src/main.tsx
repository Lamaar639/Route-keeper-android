import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App';
import { RouteDataProvider } from './context/RouteDataContext';
import './index.css';
import { registerSW } from 'virtual:pwa-register';

// Register PWA service worker immediately for offline capability
registerSW({ immediate: true });

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <RouteDataProvider>
      <App />
    </RouteDataProvider>
  </React.StrictMode>,
);
