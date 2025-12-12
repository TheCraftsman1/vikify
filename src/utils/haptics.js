/**
 * Haptics Utility
 * Provides consistent haptic feedback across the app
 * Falls back gracefully on web or unsupported devices
 */

import { Haptics, ImpactStyle, NotificationType } from '@capacitor/haptics';
import { Capacitor } from '@capacitor/core';

const isNative = Capacitor.isNativePlatform();

/**
 * Light impact - for subtle feedback (button taps, selections)
 */
export const hapticLight = async () => {
    if (!isNative) return;
    try {
        await Haptics.impact({ style: ImpactStyle.Light });
    } catch (e) {
        // Silently fail on unsupported devices
    }
};

/**
 * Medium impact - for standard interactions (play/pause, navigation)
 */
export const hapticMedium = async () => {
    if (!isNative) return;
    try {
        await Haptics.impact({ style: ImpactStyle.Medium });
    } catch (e) {
        // Silently fail
    }
};

/**
 * Heavy impact - for significant actions (delete, confirm)
 */
export const hapticHeavy = async () => {
    if (!isNative) return;
    try {
        await Haptics.impact({ style: ImpactStyle.Heavy });
    } catch (e) {
        // Silently fail
    }
};

/**
 * Success notification - for completed actions
 */
export const hapticSuccess = async () => {
    if (!isNative) return;
    try {
        await Haptics.notification({ type: NotificationType.Success });
    } catch (e) {
        // Silently fail
    }
};

/**
 * Warning notification - for cautionary feedback
 */
export const hapticWarning = async () => {
    if (!isNative) return;
    try {
        await Haptics.notification({ type: NotificationType.Warning });
    } catch (e) {
        // Silently fail
    }
};

/**
 * Error notification - for failed actions
 */
export const hapticError = async () => {
    if (!isNative) return;
    try {
        await Haptics.notification({ type: NotificationType.Error });
    } catch (e) {
        // Silently fail
    }
};

/**
 * Selection changed - for picker/slider interactions
 */
export const hapticSelection = async () => {
    if (!isNative) return;
    try {
        await Haptics.selectionChanged();
    } catch (e) {
        // Silently fail
    }
};

/**
 * Vibrate for a specific duration (Android only)
 */
export const vibrate = async (durationMs = 100) => {
    if (!isNative) return;
    try {
        await Haptics.vibrate({ duration: durationMs });
    } catch (e) {
        // Silently fail
    }
};
