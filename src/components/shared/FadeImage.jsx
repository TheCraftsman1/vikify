import React, { useState, useCallback } from 'react';

/**
 * Image component with fade-in effect on load.
 * Shows a subtle placeholder while loading for smoother UX.
 */
const FadeImage = ({
    src,
    alt = '',
    className = '',
    style = {},
    placeholder = 'rgba(40,40,40,1)',
    ...props
}) => {
    const [loaded, setLoaded] = useState(false);
    const [error, setError] = useState(false);

    const handleLoad = useCallback(() => {
        setLoaded(true);
    }, []);

    const handleError = useCallback(() => {
        setError(true);
        setLoaded(true);
    }, []);

    return (
        <div
            className={className}
            style={{
                position: 'relative',
                overflow: 'hidden',
                backgroundColor: placeholder,
                ...style,
            }}
        >
            {!error && src && (
                <img
                    src={src}
                    alt={alt}
                    onLoad={handleLoad}
                    onError={handleError}
                    style={{
                        width: '100%',
                        height: '100%',
                        objectFit: 'cover',
                        opacity: loaded ? 1 : 0,
                        transition: 'opacity 0.25s ease-in-out',
                    }}
                    {...props}
                />
            )}
            {error && (
                <div style={{
                    width: '100%',
                    height: '100%',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    color: '#666',
                    fontSize: '24px',
                }}>
                    ♪
                </div>
            )}
        </div>
    );
};

export default FadeImage;
