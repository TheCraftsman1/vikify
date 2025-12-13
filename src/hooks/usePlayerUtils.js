import { useState, useEffect, useCallback } from 'react';

/**
 * Custom hook for extracting a dominant color from an image URL.
 * Uses a hash-based approach for consistent, performant color generation.
 * 
 * @param {string} imageUrl - The image URL to extract color from
 * @param {string} defaultColor - Default color if no image is provided
 * @returns {string} HSL color string
 */
export function useDominantColor(imageUrl, defaultColor = '#121212') {
    const [color, setColor] = useState(defaultColor);

    useEffect(() => {
        if (!imageUrl) {
            setColor(defaultColor);
            return;
        }

        // Generate color from image URL hash for consistency and performance
        // This avoids canvas-based color extraction which is slow
        const hash = imageUrl.split('').reduce((a, b) => {
            a = ((a << 5) - a) + b.charCodeAt(0);
            return a & a;
        }, 0);

        const hue = Math.abs(hash) % 360;
        const saturation = 35 + (Math.abs(hash >> 8) % 20); // 35-55%
        const lightness = 20 + (Math.abs(hash >> 16) % 15);  // 20-35%

        setColor(`hsl(${hue}, ${saturation}%, ${lightness}%)`);
    }, [imageUrl, defaultColor]);

    return color;
}

/**
 * Custom hook for managing audio player state and controls.
 * Handles play/pause with proper audio element synchronization.
 * 
 * @param {Object} options - Hook options
 * @param {boolean} options.isPlaying - Current playing state
 * @param {Function} options.togglePlay - Toggle play state function
 * @param {React.RefObject} options.playerRef - Reference to audio element
 * @returns {Object} Audio control functions
 */
export function useAudioControl({ isPlaying, togglePlay, playerRef }) {
    const handlePlayPause = useCallback(() => {
        togglePlay();
        if (playerRef?.current) {
            const audio = playerRef.current;
            if (audio.paused) {
                audio.play().catch((err) => {
                    console.error('[useAudioControl] play() failed, reverting UI:', err);
                    togglePlay();
                });
            } else {
                audio.pause();
            }
        }
    }, [isPlaying, togglePlay, playerRef]);

    return { handlePlayPause };
}

/**
 * Custom hook for responsive mobile detection.
 * Updates on window resize.
 * 
 * @param {number} breakpoint - Mobile breakpoint in pixels
 * @returns {boolean} Whether the viewport is mobile-sized
 */
export function useMobileDetect(breakpoint = 768) {
    const [isMobile, setIsMobile] = useState(
        typeof window !== 'undefined' ? window.innerWidth <= breakpoint : false
    );

    useEffect(() => {
        if (typeof window === 'undefined') return;

        const handleResize = () => {
            setIsMobile(window.innerWidth <= breakpoint);
        };

        window.addEventListener('resize', handleResize);
        return () => window.removeEventListener('resize', handleResize);
    }, [breakpoint]);

    return isMobile;
}

/**
 * Custom hook for managing volume controls.
 * 
 * @param {Object} options - Hook options
 * @param {number} options.initialVolume - Initial volume (0-1)
 * @returns {Object} Volume state and controls
 */
export function useVolumeControl({ initialVolume = 1 }) {
    const [volume, setVolume] = useState(initialVolume);
    const [isMuted, setIsMuted] = useState(false);
    const [previousVolume, setPreviousVolume] = useState(initialVolume);

    const changeVolume = useCallback((newVolume) => {
        const clamped = Math.max(0, Math.min(1, newVolume));
        setVolume(clamped);
        if (clamped > 0 && isMuted) {
            setIsMuted(false);
        }
    }, [isMuted]);

    const toggleMute = useCallback(() => {
        if (isMuted) {
            setIsMuted(false);
            setVolume(previousVolume || 0.5);
        } else {
            setPreviousVolume(volume);
            setIsMuted(true);
        }
    }, [isMuted, volume, previousVolume]);

    const handleVolumeBarClick = useCallback((e) => {
        const rect = e.currentTarget.getBoundingClientRect();
        const newVolume = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
        changeVolume(newVolume);
    }, [changeVolume]);

    return {
        volume,
        isMuted,
        changeVolume,
        toggleMute,
        handleVolumeBarClick,
        effectiveVolume: isMuted ? 0 : volume,
    };
}

export default {
    useDominantColor,
    useAudioControl,
    useMobileDetect,
    useVolumeControl,
};
