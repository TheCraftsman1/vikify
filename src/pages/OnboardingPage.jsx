import { Browser } from '@capacitor/browser';

const OnboardingPage = () => {
    const { user, isAuthenticated, completeOnboarding, skipOnboarding, logout } = useAuth();
    const backendUrl = BACKEND_URL;

    const handleConnectSpotify = async () => {
        await Browser.open({ url: `${backendUrl}/auth/spotify` });
    };

    const handleLoadData = () => {
        // User confirmed - complete onboarding and go to app
        completeOnboarding();
    };

    // If user is authenticated, show "Load data?" confirmation
    if (isAuthenticated && user) {
        return (
            <div style={{
                minHeight: '100vh',
                background: 'linear-gradient(180deg, #1a1a2e 0%, #121212 50%, #000 100%)',
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                padding: '40px 24px',
                textAlign: 'center'
            }}>
                {/* User Avatar */}
                {user.image ? (
                    <img
                        src={user.image}
                        alt={user.name}
                        style={{
                            width: '100px',
                            height: '100px',
                            borderRadius: '50%',
                            marginBottom: '24px',
                            border: '3px solid #1DB954'
                        }}
                    />
                ) : (
                    <div style={{
                        width: '100px',
                        height: '100px',
                        borderRadius: '50%',
                        backgroundColor: '#1DB954',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        marginBottom: '24px',
                        fontSize: '40px',
                        fontWeight: 700,
                        color: '#000'
                    }}>
                        {user.name?.[0]?.toUpperCase() || 'V'}
                    </div>
                )}

                {/* Welcome Text */}
                <h1 style={{
                    color: '#fff',
                    fontSize: '24px',
                    fontWeight: 700,
                    marginBottom: '8px'
                }}>
                    Hey, {user.name}! 👋
                </h1>

                <p style={{
                    color: '#b3b3b3',
                    fontSize: '16px',
                    marginBottom: '40px',
                    maxWidth: '300px',
                    lineHeight: 1.5
                }}>
                    Load your Spotify playlists and liked songs to Vikify?
                </p>

                {/* Load Data Button */}
                <button
                    onClick={handleLoadData}
                    style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        gap: '10px',
                        width: '100%',
                        maxWidth: '320px',
                        padding: '16px 24px',
                        backgroundColor: '#1DB954',
                        color: '#000',
                        border: 'none',
                        borderRadius: '50px',
                        fontSize: '16px',
                        fontWeight: 700,
                        cursor: 'pointer',
                        marginBottom: '12px'
                    }}
                >
                    ✓ Let's Go!
                </button>

                <p style={{
                    color: '#666',
                    fontSize: '12px',
                    marginTop: '24px'
                }}>
                    Your playlists will appear in your Library
                </p>

                {/* Switch Account Button */}
                <button
                    onClick={logout}
                    className="logout-btn"
                    style={{
                        marginTop: '32px',
                        background: 'none',
                        border: '1px solid #333',
                        borderRadius: '20px',
                        padding: '8px 16px',
                        color: '#888',
                        fontSize: '12px',
                        cursor: 'pointer'
                    }}
                >
                    Switch Account
                </button>
            </div>
        );
    }

    // Initial state - Show connect button
    return (
        <div style={{
            minHeight: '100vh',
            background: 'linear-gradient(180deg, #1a1a2e 0%, #121212 50%, #000 100%)',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '40px 24px',
            textAlign: 'center'
        }}>
            {/* Logo */}
            <img
                src="/logo.png"
                alt="Vikify"
                style={{
                    width: '120px',
                    height: '120px',
                    marginBottom: '32px'
                }}
            />

            {/* Welcome Text */}
            <h1 style={{
                color: '#fff',
                fontSize: '28px',
                fontWeight: 700,
                marginBottom: '12px'
            }}>
                Welcome to Vikify
            </h1>

            <p style={{
                color: '#b3b3b3',
                fontSize: '16px',
                marginBottom: '48px',
                maxWidth: '300px',
                lineHeight: 1.5
            }}>
                Connect your Spotify account to import your playlists and liked songs
            </p>

            {/* Connect Button */}
            <button
                onClick={handleConnectSpotify}
                style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: '12px',
                    width: '100%',
                    maxWidth: '320px',
                    padding: '16px 24px',
                    backgroundColor: '#1DB954',
                    color: '#000',
                    border: 'none',
                    borderRadius: '50px',
                    fontSize: '16px',
                    fontWeight: 700,
                    cursor: 'pointer',
                    marginBottom: '16px'
                }}
            >
                <svg width="24" height="24" viewBox="0 0 24 24" fill="currentColor">
                    <path d="M12 0C5.4 0 0 5.4 0 12s5.4 12 12 12 12-5.4 12-12S18.66 0 12 0zm5.521 17.34c-.24.359-.66.48-1.021.24-2.82-1.74-6.36-2.101-10.561-1.141-.418.122-.779-.179-.899-.539-.12-.421.18-.78.54-.9 4.56-1.021 8.52-.6 11.64 1.32.42.18.479.659.301 1.02zm1.44-3.3c-.301.42-.841.6-1.262.3-3.239-1.98-8.159-2.58-11.939-1.38-.479.12-1.02-.12-1.14-.6-.12-.48.12-1.021.6-1.141C9.6 9.9 15 10.561 18.72 12.84c.361.181.54.78.241 1.2zm.12-3.36C15.24 8.4 8.82 8.16 5.16 9.301c-.6.179-1.2-.181-1.38-.721-.18-.601.18-1.2.72-1.381 4.26-1.26 11.28-1.02 15.721 1.621.539.3.719 1.02.419 1.56-.299.421-1.02.599-1.559.3z" />
                </svg>
                Connect with Spotify
            </button>

            {/* Skip Button */}
            <button
                onClick={skipOnboarding}
                style={{
                    background: 'none',
                    border: 'none',
                    color: '#b3b3b3',
                    fontSize: '14px',
                    cursor: 'pointer',
                    padding: '12px 24px'
                }}
            >
                Skip for now
            </button>

            {/* Footer */}
            <p style={{
                position: 'absolute',
                bottom: '40px',
                color: '#666',
                fontSize: '12px'
            }}>
                You can connect later from Library
            </p>
        </div>
    );
};

export default OnboardingPage;
