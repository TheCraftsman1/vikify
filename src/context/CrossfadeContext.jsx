import React, { createContext, useContext, useState, useCallback, useRef, useEffect } from 'react';

const CrossfadeContext = createContext();

export const useCrossfade = () => useContext(CrossfadeContext);

/**
 * Crossfade settings and state management
 */
export const CrossfadeProvider = ({ children }) => {
    const [enabled, setEnabled] = useState(() => {
        const stored = localStorage.getItem('vikify_crossfade_enabled');
        return stored === 'true';
    });

    const [duration, setDuration] = useState(() => {
        const stored = localStorage.getItem('vikify_crossfade_duration');
        return stored ? parseInt(stored, 10) : 5; // 5 seconds default
    });

    const [isTransitioning, setIsTransitioning] = useState(false);
    const fadeOutRef = useRef(null);
    const fadeInRef = useRef(null);

    // Persist settings
    useEffect(() => {
        localStorage.setItem('vikify_crossfade_enabled', String(enabled));
    }, [enabled]);

    useEffect(() => {
        localStorage.setItem('vikify_crossfade_duration', String(duration));
    }, [duration]);

    /**
     * Start crossfade transition between two audio elements
     * @param {HTMLAudioElement} outgoing - Fading out audio
     * @param {HTMLAudioElement} incoming - Fading in audio
     */
    const startCrossfade = useCallback((outgoing, incoming) => {
        if (!enabled || !outgoing || !incoming) return false;

        setIsTransitioning(true);

        const steps = 30; // Animation frames
        const stepDuration = (duration * 1000) / steps;
        let currentStep = 0;

        // Store original volumes
        const outgoingVolume = outgoing.volume;
        incoming.volume = 0;

        // Clear any existing transitions
        if (fadeOutRef.current) clearInterval(fadeOutRef.current);
        if (fadeInRef.current) clearInterval(fadeInRef.current);

        // Start incoming audio
        incoming.play().catch(() => { });

        // Crossfade animation
        fadeOutRef.current = setInterval(() => {
            currentStep++;
            const progress = currentStep / steps;

            // Ease-in-out curve
            const eased = progress < 0.5
                ? 2 * progress * progress
                : 1 - Math.pow(-2 * progress + 2, 2) / 2;

            outgoing.volume = outgoingVolume * (1 - eased);
            incoming.volume = outgoingVolume * eased;

            if (currentStep >= steps) {
                clearInterval(fadeOutRef.current);
                outgoing.pause();
                outgoing.volume = outgoingVolume; // Reset for next use
                setIsTransitioning(false);
            }
        }, stepDuration);

        return true;
    }, [enabled, duration]);

    const cancelTransition = useCallback(() => {
        if (fadeOutRef.current) clearInterval(fadeOutRef.current);
        if (fadeInRef.current) clearInterval(fadeInRef.current);
        setIsTransitioning(false);
    }, []);

    return (
        <CrossfadeContext.Provider value={{
            enabled,
            setEnabled,
            duration,
            setDuration,
            isTransitioning,
            startCrossfade,
            cancelTransition,
        }}>
            {children}
        </CrossfadeContext.Provider>
    );
};
