import { Browser } from '@capacitor/browser';
import { Capacitor } from '@capacitor/core';
import { useAuth } from '../context/AuthContext';
import { BACKEND_URL } from '../config';

const OnboardingPage = () => {
    const { user, isAuthenticated, completeOnboarding, skipOnboarding, logout } = useAuth();
    const backendUrl = BACKEND_URL;

    const handleConnectSpotify = async () => {
        // Detect if running on mobile (Android/iOS) to tell backend to redirect via deep link
        const isMobile = Capacitor.isNativePlatform();
        const authUrl = `${backendUrl}/auth/spotify${isMobile ? '?mobile=true' : ''}`;

        if (isMobile) {
            await Browser.open({ url: authUrl });
        } else {
            // For web, open in same window to handle redirect
            window.location.href = authUrl;
        }
    };

    const handleLoadData = () => {
        // User confirmed - complete onboarding and go to app
        completeOnboarding();
    };

    const handleSkip = () => {
        // User wants to skip for now
        skipOnboarding();
    };

    const handleLogout = () => {
        logout();
    };

    // If user is already authenticated, show the data loading confirmation
    if (isAuthenticated && user) {
        return (
            <div style={{
                minHeight: '100vh',
                background: 'linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%)',
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                padding: '20px',
                color: 'white'
            }}>
                <div style={{
                    textAlign: 'center',
                    maxWidth: '400px'
                }}>
                    {/* User Profile */}
                    <div style={{ marginBottom: '30px' }}>
                        {user.images?.[0]?.url && (
                            <img
                                src={user.images[0].url}
                                alt={user.display_name}
                                style={{
                                    width: '100px',
                                    height: '100px',
                                    borderRadius: '50%',
                                    marginBottom: '15px',
                                    border: '3px solid #1DB954'
                                }}
                            />
                        )}
                        <h2 style={{ margin: '0 0 5px 0' }}>Welcome, {user.display_name}!</h2>
                        <p style={{ color: '#b3b3b3', margin: 0 }}>{user.email}</p>
                    </div>

                    {/* Confirmation Message */}
                    <div style={{
                        background: 'rgba(29, 185, 84, 0.1)',
                        border: '1px solid rgba(29, 185, 84, 0.3)',
                        borderRadius: '12px',
                        padding: '20px',
                        marginBottom: '30px'
                    }}>
                        <h3 style={{ color: '#1DB954', marginTop: 0 }}>✓ Spotify Connected!</h3>
                        <p style={{ color: '#b3b3b3', marginBottom: 0 }}>
                            We'll import your playlists and liked songs to personalize your Vikify experience.
                        </p>
                    </div>

                    {/* Action Buttons */}
                    <button
                        onClick={handleLoadData}
                        style={{
                            width: '100%',
                            padding: '15px 30px',
                            fontSize: '16px',
                            fontWeight: 'bold',
                            border: 'none',
                            borderRadius: '25px',
                            cursor: 'pointer',
                            marginBottom: '15px',
                            background: '#1DB954',
                            color: 'white'
                        }}
                    >
                        Continue to Vikify
                    </button>

                    <button
                        onClick={handleLogout}
                        style={{
                            width: '100%',
                            padding: '12px 30px',
                            fontSize: '14px',
                            background: 'transparent',
                            border: '1px solid #666',
                            borderRadius: '25px',
                            cursor: 'pointer',
                            color: '#b3b3b3'
                        }}
                    >
                        Use a different account
                    </button>
                </div>
            </div>
        );
    }

    // Initial onboarding screen
    return (
        <div style={{
            minHeight: '100vh',
            background: 'linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%)',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '20px',
            color: 'white'
        }}>
            <div style={{
                textAlign: 'center',
                maxWidth: '400px'
            }}>
                {/* Logo/Brand */}
                <div style={{ marginBottom: '40px' }}>
                    <h1 style={{
                        fontSize: '48px',
                        fontWeight: 'bold',
                        background: 'linear-gradient(135deg, #1DB954, #1ed760)',
                        WebkitBackgroundClip: 'text',
                        WebkitTextFillColor: 'transparent',
                        margin: '0 0 10px 0'
                    }}>
                        Vikify
                    </h1>
                    <p style={{ color: '#b3b3b3', fontSize: '16px' }}>
                        Your music, your way
                    </p>
                </div>

                {/* Features */}
                <div style={{
                    marginBottom: '40px',
                    textAlign: 'left',
                    background: 'rgba(255,255,255,0.05)',
                    borderRadius: '12px',
                    padding: '20px'
                }}>
                    <div style={{ display: 'flex', alignItems: 'center', marginBottom: '15px' }}>
                        <span style={{ fontSize: '24px', marginRight: '15px' }}>🎵</span>
                        <span>Import your Spotify playlists</span>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', marginBottom: '15px' }}>
                        <span style={{ fontSize: '24px', marginRight: '15px' }}>❤️</span>
                        <span>Sync your liked songs</span>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', marginBottom: '15px' }}>
                        <span style={{ fontSize: '24px', marginRight: '15px' }}>📥</span>
                        <span>Download for offline listening</span>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center' }}>
                        <span style={{ fontSize: '24px', marginRight: '15px' }}>🆓</span>
                        <span>Free, no ads, ever</span>
                    </div>
                </div>

                {/* Connect Button */}
                <button
                    onClick={handleConnectSpotify}
                    style={{
                        width: '100%',
                        padding: '15px 30px',
                        fontSize: '16px',
                        fontWeight: 'bold',
                        border: 'none',
                        borderRadius: '25px',
                        cursor: 'pointer',
                        marginBottom: '15px',
                        background: '#1DB954',
                        color: 'white',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        gap: '10px'
                    }}
                >
                    <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor">
                        <path d="M12 0C5.4 0 0 5.4 0 12s5.4 12 12 12 12-5.4 12-12S18.66 0 12 0zm5.521 17.34c-.24.359-.66.48-1.021.24-2.82-1.74-6.36-2.101-10.561-1.141-.418.122-.779-.179-.899-.539-.12-.421.18-.78.54-.9 4.56-1.021 8.52-.6 11.64 1.32.42.18.479.659.301 1.02zm1.44-3.3c-.301.42-.841.6-1.262.3-3.239-1.98-8.159-2.58-11.939-1.38-.479.12-1.02-.12-1.14-.6-.12-.48.12-1.021.6-1.141C9.6 9.9 15 10.561 18.72 12.84c.361.181.54.78.241 1.2zm.12-3.36C15.24 8.4 8.82 8.16 5.16 9.301c-.6.179-1.2-.181-1.38-.721-.18-.601.18-1.2.72-1.381 4.26-1.26 11.28-1.02 15.721 1.621.539.3.719 1.02.419 1.56-.299.421-1.02.599-1.559.3z" />
                    </svg>
                    Connect with Spotify
                </button>

                {/* Skip Option */}
                <button
                    onClick={handleSkip}
                    style={{
                        width: '100%',
                        padding: '12px 30px',
                        fontSize: '14px',
                        background: 'transparent',
                        border: '1px solid #666',
                        borderRadius: '25px',
                        cursor: 'pointer',
                        color: '#b3b3b3'
                    }}
                >
                    Skip for now
                </button>

                <p style={{
                    marginTop: '20px',
                    fontSize: '12px',
                    color: '#666'
                }}>
                    You can connect your Spotify account later in Settings
                </p>
            </div>
        </div>
    );
};

export default OnboardingPage;
