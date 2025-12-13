import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App.jsx'
import './index.css'
import { BrowserRouter } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { PlayerProvider } from './context/PlayerContext';
import { LikedSongsProvider } from './context/LikedSongsContext';
import { OfflineProvider } from './context/OfflineContext';
import { PlaylistProvider } from './context/PlaylistContext';
import ErrorBoundary from './components/ErrorBoundary';

// Debug logging capture - only in development mode
if (import.meta.env.DEV && typeof window !== 'undefined') {
  window.logs = [];
  const captureLog = (type, args) => {
    const message = args.map(arg =>
      typeof arg === 'object' ? JSON.stringify(arg, null, 2) : String(arg)
    ).join(' ');
    window.logs.push(`[${type}] ${message}`);

    // Update a hidden div for the agent to read
    let logDiv = document.getElementById('debug-logs');
    if (!logDiv) {
      logDiv = document.createElement('div');
      logDiv.id = 'debug-logs';
      logDiv.style.display = 'none';
      document.body.appendChild(logDiv);
    }
    logDiv.innerText = window.logs.join('\n');
  };

  const originalLog = console.log;
  const originalError = console.error;
  const originalWarn = console.warn;

  console.log = (...args) => { originalLog(...args); captureLog('LOG', args); };
  console.error = (...args) => { originalError(...args); captureLog('ERROR', args); };
  console.warn = (...args) => { originalWarn(...args); captureLog('WARN', args); };
}

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <OfflineProvider>
          <LikedSongsProvider>
            <PlaylistProvider>
              <PlayerProvider>
                <ErrorBoundary>
                  <App />
                </ErrorBoundary>
              </PlayerProvider>
            </PlaylistProvider>
          </LikedSongsProvider>
        </OfflineProvider>
      </AuthProvider>
    </BrowserRouter>
  </React.StrictMode>,
)

