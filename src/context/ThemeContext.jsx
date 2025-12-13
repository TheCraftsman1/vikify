import React, { createContext, useContext, useState, useCallback, useEffect } from 'react';

const ThemeContext = createContext();

export const useTheme = () => useContext(ThemeContext);

/**
 * Extract dominant color from image using canvas
 * @param {string} imageUrl 
 * @returns {Promise<{h: number, s: number, l: number}>}
 */
const extractColor = (imageUrl) => {
    return new Promise((resolve) => {
        if (!imageUrl) {
            resolve({ h: 0, s: 0, l: 20 });
            return;
        }

        const img = new Image();
        img.crossOrigin = 'anonymous';

        img.onload = () => {
            try {
                const canvas = document.createElement('canvas');
                const ctx = canvas.getContext('2d');
                canvas.width = 50; // Small for performance
                canvas.height = 50;
                ctx.drawImage(img, 0, 0, 50, 50);

                const data = ctx.getImageData(0, 0, 50, 50).data;
                let r = 0, g = 0, b = 0, count = 0;

                // Sample every 4th pixel for speed
                for (let i = 0; i < data.length; i += 16) {
                    r += data[i];
                    g += data[i + 1];
                    b += data[i + 2];
                    count++;
                }

                r = Math.round(r / count);
                g = Math.round(g / count);
                b = Math.round(b / count);

                // Convert RGB to HSL
                const hsl = rgbToHsl(r, g, b);
                resolve(hsl);
            } catch {
                // Fallback to hash-based color
                resolve(hashToColor(imageUrl));
            }
        };

        img.onerror = () => {
            resolve(hashToColor(imageUrl));
        };

        img.src = imageUrl;
    });
};

const rgbToHsl = (r, g, b) => {
    r /= 255; g /= 255; b /= 255;
    const max = Math.max(r, g, b), min = Math.min(r, g, b);
    let h, s, l = (max + min) / 2;

    if (max === min) {
        h = s = 0;
    } else {
        const d = max - min;
        s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
        switch (max) {
            case r: h = ((g - b) / d + (g < b ? 6 : 0)) / 6; break;
            case g: h = ((b - r) / d + 2) / 6; break;
            case b: h = ((r - g) / d + 4) / 6; break;
            default: h = 0;
        }
    }
    return { h: Math.round(h * 360), s: Math.round(s * 100), l: Math.round(l * 100) };
};

const hashToColor = (str) => {
    let hash = 0;
    for (let i = 0; i < str.length; i++) {
        hash = str.charCodeAt(i) + ((hash << 5) - hash);
    }
    return {
        h: Math.abs(hash) % 360,
        s: 35 + (Math.abs(hash >> 8) % 25),
        l: 20 + (Math.abs(hash >> 16) % 15)
    };
};

export const ThemeProvider = ({ children }) => {
    const [themeColor, setThemeColor] = useState({ h: 0, s: 0, l: 12 });
    const [isExtracting, setIsExtracting] = useState(false);

    const updateThemeFromImage = useCallback(async (imageUrl) => {
        if (!imageUrl || isExtracting) return;

        setIsExtracting(true);
        try {
            const color = await extractColor(imageUrl);
            // Clamp lightness for dark theme
            color.l = Math.min(color.l, 35);
            color.s = Math.min(color.s, 60);
            setThemeColor(color);
        } catch {
            // Keep existing color
        }
        setIsExtracting(false);
    }, [isExtracting]);

    const getGradient = useCallback((direction = '180deg') => {
        const { h, s, l } = themeColor;
        return `linear-gradient(${direction}, hsl(${h}, ${s}%, ${l}%) 0%, #121212 100%)`;
    }, [themeColor]);

    const getAccentColor = useCallback(() => {
        const { h, s } = themeColor;
        return `hsl(${h}, ${Math.min(s + 20, 70)}%, 45%)`;
    }, [themeColor]);

    return (
        <ThemeContext.Provider value={{
            themeColor,
            updateThemeFromImage,
            getGradient,
            getAccentColor,
            isExtracting,
        }}>
            {children}
        </ThemeContext.Provider>
    );
};
