import React, { useState } from 'react';
import { Routes, Route } from 'react-router-dom';
import { AuthProvider, useAuth } from './context/AuthContext';
import Sidebar from './components/Sidebar';
import Player from './components/Player';
import BottomNav from './components/BottomNav';
import DownloadProgress from './components/DownloadProgress';
import SplashScreen from './components/SplashScreen';
import OnboardingPage from './pages/OnboardingPage';
import Home from './pages/Home';
import Search from './pages/Search';
import Library from './pages/Library';
import Downloads from './pages/Downloads';
import PlaylistView from './pages/PlaylistView';
import LikedSongs from './pages/LikedSongs';
import Settings from './pages/Settings';
import { UIProvider } from './context/UIContext';
import ProfileDrawer from './components/ProfileDrawer';
import './App.css';

function AppContent() {
  const [showSplash, setShowSplash] = useState(true);
  const { hasCompletedOnboarding, loading } = useAuth();

  const handleSplashComplete = () => {
    setShowSplash(false);
  };

  // Show splash screen on every load
  if (showSplash) {
    return <SplashScreen onComplete={handleSplashComplete} />;
  }

  // Show loading while checking auth state
  if (loading) {
    return (
      <div style={{
        minHeight: '100vh',
        backgroundColor: '#000',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center'
      }}>
        <img src="/logo.png" alt="Vikify" style={{ width: '80px', opacity: 0.5 }} />
      </div>
    );
  }

  // Show onboarding for new users
  if (!hasCompletedOnboarding) {
    return <OnboardingPage />;
  }

  // Main app
  return (
    <div className="app-container">
      <ProfileDrawer />
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
          <Route path="/settings" element={<Settings />} />
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

function App() {
  return (
    <AuthProvider>
      <UIProvider>
        <AppContent />
      </UIProvider>
    </AuthProvider>
  );
}

export default App;
