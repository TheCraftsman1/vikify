import { useState, useRef, useCallback, useEffect } from 'react';
import { hapticLight, hapticSelection } from '../utils/haptics';

/**
 * Custom hook for handling progress bar scrubbing with touch/pointer support.
 * Provides smooth scrubbing with haptic feedback and RAF-based updates.
 * 
 * @param {Object} options - Hook options
 * @param {number} options.duration - Total duration in seconds
 * @param {number} options.progress - Current progress in seconds
 * @param {Function} options.onSeek - Callback when seek is complete
 * @param {React.RefObject} options.playerRef - Reference to audio element
 * @param {boolean} options.isYoutube - Whether playing YouTube content
 * @returns {Object} Scrubbing state and handlers
 */
export function useScrubbing({ duration, progress, onSeek, playerRef, isYoutube = false }) {
    const [isScrubbing, setIsScrubbing] = useState(false);
    const [scrubTime, setScrubTime] = useState(0);

    const scrubbingRef = useRef(false);
    const rafRef = useRef(null);
    const lastSelectionSecondRef = useRef(-1);
    const activeScrubElRef = useRef(null);
    const lastScrubEndEpochMsRef = useRef(0);

    // The currently displayed progress (scrub time or actual progress)
    const displayedProgress = isScrubbing ? scrubTime : progress;
    const progressPercent = (displayedProgress / (duration || 1)) * 100;

    // Calculate scrub time from pointer position
    const clampScrubTimeFromClientX = useCallback((clientX, element) => {
        const target = element || activeScrubElRef.current;
        if (!target) return 0;
        const rect = target.getBoundingClientRect();
        const percent = Math.max(0, Math.min(1, (clientX - rect.left) / Math.max(1, rect.width)));
        return percent * (duration || 0);
    }, [duration]);

    // Schedule scrub update with requestAnimationFrame for performance
    const scheduleScrubUpdate = useCallback((nextTime) => {
        if (rafRef.current) return;
        rafRef.current = requestAnimationFrame(() => {
            rafRef.current = null;
            setScrubTime(nextTime);
            // Haptic feedback when crossing second boundaries
            const nextSecond = Math.floor(nextTime);
            if (nextSecond !== lastSelectionSecondRef.current) {
                lastSelectionSecondRef.current = nextSecond;
                hapticSelection();
            }
        });
    }, []);

    // Handler for pointer down (start scrubbing)
    const onPointerDown = useCallback((e) => {
        if (!duration) return;
        e.preventDefault();
        activeScrubElRef.current = e.currentTarget;
        scrubbingRef.current = true;
        setIsScrubbing(true);
        const nextTime = clampScrubTimeFromClientX(e.clientX, e.currentTarget);
        lastSelectionSecondRef.current = Math.floor(nextTime);
        setScrubTime(nextTime);
        hapticLight();
        try {
            e.currentTarget.setPointerCapture?.(e.pointerId);
        } catch { }
    }, [duration, clampScrubTimeFromClientX]);

    // Handler for pointer move (during scrubbing)
    const onPointerMove = useCallback((e) => {
        if (!scrubbingRef.current || !duration) return;
        e.preventDefault();
        scheduleScrubUpdate(clampScrubTimeFromClientX(e.clientX, e.currentTarget));
    }, [duration, clampScrubTimeFromClientX, scheduleScrubUpdate]);

    // Complete the scrub and seek to final position
    const endScrub = useCallback((finalClientX, element) => {
        if (!scrubbingRef.current) return;
        scrubbingRef.current = false;
        lastScrubEndEpochMsRef.current = Date.now();
        const finalTime = clampScrubTimeFromClientX(finalClientX, element);
        setIsScrubbing(false);
        setScrubTime(finalTime);
        hapticLight();
        onSeek(finalTime);
        if (playerRef?.current && !isYoutube) {
            playerRef.current.currentTime = finalTime;
        }
    }, [clampScrubTimeFromClientX, onSeek, playerRef, isYoutube]);

    // Handler for pointer up (end scrubbing)
    const onPointerUp = useCallback((e) => {
        e.preventDefault();
        endScrub(e.clientX, e.currentTarget);
    }, [endScrub]);

    // Handler for pointer cancel
    const onPointerCancel = useCallback((e) => {
        e.preventDefault();
        endScrub(e.clientX, e.currentTarget);
    }, [endScrub]);

    // Direct click seek (without scrubbing)
    const onClick = useCallback((e) => {
        // On many browsers, a click event fires after pointerup; avoid double-seeking.
        if (scrubbingRef.current) return;
        const now = Date.now();
        if (now - lastScrubEndEpochMsRef.current < 250) return;
        const rect = e.currentTarget.getBoundingClientRect();
        const percent = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
        const time = percent * (duration || 0);
        onSeek(time);
        if (playerRef?.current && !isYoutube) {
            playerRef.current.currentTime = time;
        }
    }, [duration, onSeek, playerRef, isYoutube]);

    // Cleanup RAF on unmount
    useEffect(() => {
        return () => {
            if (rafRef.current) {
                cancelAnimationFrame(rafRef.current);
                rafRef.current = null;
            }
        };
    }, []);

    return {
        isScrubbing,
        displayedProgress,
        progressPercent,
        handlers: {
            onPointerDown,
            onPointerMove,
            onPointerUp,
            onPointerCancel,
            onClick,
        },
    };
}

export default useScrubbing;
