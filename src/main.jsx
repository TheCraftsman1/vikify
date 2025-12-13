import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App.jsx'
import './index.css'
import { HashRouter } from 'react-router-dom';
import { AuthProvider } from './context/AuthContext';
import { PlayerProvider } from './context/PlayerContext';
import { LikedSongsProvider } from './context/LikedSongsContext';
import { HistoryProvider } from './context/HistoryContext';
import { LyricsProvider } from './context/LyricsContext';
import { ThemeProvider } from './context/ThemeContext';
import { CrossfadeProvider } from './context/CrossfadeContext';
import { OfflineProvider } from './context/OfflineContext';
import { PlaylistProvider } from './context/PlaylistContext';
import ErrorBoundary from './components/ErrorBoundary';

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <HashRouter>
      <AuthProvider>
        <ThemeProvider>
          <CrossfadeProvider>
            <OfflineProvider>
              <HistoryProvider>
                <LikedSongsProvider>
                  <PlaylistProvider>
                    <PlayerProvider>
                      <LyricsProvider>
                        <ErrorBoundary>
                          <App />
                        </ErrorBoundary>
                      </LyricsProvider>
                    </PlayerProvider>
                  </PlaylistProvider>
                </LikedSongsProvider>
              </HistoryProvider>
            </OfflineProvider>
          </CrossfadeProvider>
        </ThemeProvider>
      </AuthProvider>
    </HashRouter>
  </React.StrictMode>,
)


