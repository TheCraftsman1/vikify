import React from 'react';
import { Routes, Route } from 'react-router-dom';
import Sidebar from './components/Sidebar';
import Player from './components/Player';
import BottomNav from './components/BottomNav';
import DownloadProgress from './components/DownloadProgress';
import Home from './pages/Home';
import Search from './pages/Search';
import Library from './pages/Library';
import Downloads from './pages/Downloads';
import PlaylistView from './pages/PlaylistView';
import LikedSongs from './pages/LikedSongs';
import './App.css';

function App() {
  return (
    <div className="app-container">
      {/* Sidebar - Desktop Only */}
      <div className="sidebar-area">
        <Sidebar />
      </div>

      {/* Main Content */}
      <main className="main-area">
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/search" element={<Search />} />
          <Route path="/library" element={<Library />} />
          <Route path="/downloads" element={<Downloads />} />
          <Route path="/liked" element={<LikedSongs />} />
          <Route path="/playlist/:id" element={<PlaylistView />} />
        </Routes>
      </main>

      {/* Player Bar */}
      <div className="player-area">
        <Player />
      </div>

      {/* Download Progress Toast */}
      <DownloadProgress />

      {/* Bottom Navigation - Mobile Only */}
      <div className="mobile-only" style={{ gridArea: 'bottom-nav' }}>
        <BottomNav />
      </div>
    </div>
  );
}

export default App;
