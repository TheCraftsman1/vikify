import { useState, useCallback, useRef } from 'react';

/**
 * Hook for handling swipe gestures
 * @param {Object} handlers - { onSwipeLeft, onSwipeRight, onSwipeUp, onSwipeDown }
 * @param {Object} options - { threshold: number, preventScroll: boolean }
 */
export const useSwipeGesture = (handlers = {}, options = {}) => {
    const { threshold = 50, preventScroll = false } = options;
    const [isSwiping, setIsSwiping] = useState(false);
    const startPos = useRef({ x: 0, y: 0 });
    const currentPos = useRef({ x: 0, y: 0 });

    const onTouchStart = useCallback((e) => {
        const touch = e.touches[0];
        startPos.current = { x: touch.clientX, y: touch.clientY };
        currentPos.current = { x: touch.clientX, y: touch.clientY };
        setIsSwiping(true);
    }, []);

    const onTouchMove = useCallback((e) => {
        if (!isSwiping) return;

        const touch = e.touches[0];
        currentPos.current = { x: touch.clientX, y: touch.clientY };

        if (preventScroll) {
            const deltaX = Math.abs(touch.clientX - startPos.current.x);
            const deltaY = Math.abs(touch.clientY - startPos.current.y);
            if (deltaX > deltaY) {
                e.preventDefault();
            }
        }
    }, [isSwiping, preventScroll]);

    const onTouchEnd = useCallback(() => {
        if (!isSwiping) return;

        const deltaX = currentPos.current.x - startPos.current.x;
        const deltaY = currentPos.current.y - startPos.current.y;
        const absDeltaX = Math.abs(deltaX);
        const absDeltaY = Math.abs(deltaY);

        setIsSwiping(false);

        // Determine swipe direction
        if (absDeltaX > threshold && absDeltaX > absDeltaY) {
            if (deltaX > 0) {
                handlers.onSwipeRight?.();
            } else {
                handlers.onSwipeLeft?.();
            }
        } else if (absDeltaY > threshold && absDeltaY > absDeltaX) {
            if (deltaY > 0) {
                handlers.onSwipeDown?.();
            } else {
                handlers.onSwipeUp?.();
            }
        }
    }, [isSwiping, handlers, threshold]);

    const getSwipeOffset = useCallback(() => {
        if (!isSwiping) return { x: 0, y: 0 };
        return {
            x: currentPos.current.x - startPos.current.x,
            y: currentPos.current.y - startPos.current.y,
        };
    }, [isSwiping]);

    return {
        isSwiping,
        handlers: {
            onTouchStart,
            onTouchMove,
            onTouchEnd,
        },
        getSwipeOffset,
    };
};

export default useSwipeGesture;
