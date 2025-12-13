import React, { useState, useEffect } from 'react';

const SplashScreen = ({ onComplete }) => {
    const [fadeOut, setFadeOut] = useState(false);

    useEffect(() => {
        // Start fade out after 2 seconds
        const fadeTimer = setTimeout(() => {
            setFadeOut(true);
        }, 2000);

        // Complete after fade animation (2.5s total)
        const completeTimer = setTimeout(() => {
            onComplete();
        }, 2500);

        return () => {
            clearTimeout(fadeTimer);
            clearTimeout(completeTimer);
        };
    }, [onComplete]);

    return (
        <div
            className={`splash-screen ${fadeOut ? 'fade-out' : ''}`}
            style={{
                position: 'fixed',
                top: 0,
                left: 0,
                right: 0,
                bottom: 0,
                backgroundColor: '#000',
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                justifyContent: 'center',
                zIndex: 9999,
                transition: 'opacity 0.5s ease-out',
                opacity: fadeOut ? 0 : 1
            }}
        >
            {/* Logo with pulse animation */}
            <div
                className="splash-logo"
                style={{
                    animation: 'logoFadeIn 0.8s ease-out forwards, logoPulse 2s ease-in-out infinite'
                }}
            >
                <img
                    src="/logo.png"
                    alt="Vikify"
                    style={{
                        width: '180px',
                        height: '180px',
                        objectFit: 'contain'
                    }}
                    onError={(e) => { e.target.style.display = 'none'; }}
                />
            </div>

            {/* Loading bar */}
            <div style={{
                position: 'absolute',
                bottom: '80px',
                width: '60px',
                height: '3px',
                backgroundColor: 'rgba(255,255,255,0.1)',
                borderRadius: '2px',
                overflow: 'hidden'
            }}>
                <div
                    style={{
                        height: '100%',
                        backgroundColor: '#1db954',
                        borderRadius: '2px',
                        animation: 'loadingBar 2s ease-out forwards'
                    }}
                />
            </div>

            <style>{`
                @keyframes logoFadeIn {
                    0% { opacity: 0; transform: scale(0.8); }
                    100% { opacity: 1; transform: scale(1); }
                }
                
                @keyframes logoPulse {
                    0%, 100% { transform: scale(1); }
                    50% { transform: scale(1.05); }
                }
                
                @keyframes loadingBar {
                    0% { width: 0%; }
                    100% { width: 100%; }
                }
            `}</style>
        </div>
    );
};

export default SplashScreen;
