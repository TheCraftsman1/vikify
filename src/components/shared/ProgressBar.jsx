import React from 'react';
import PropTypes from 'prop-types';

/**
 * Reusable progress bar component with scrubbing support.
 * Used in both Player and MobileFullScreenPlayer.
 * 
 * @param {Object} props - Component props
 * @param {number} props.progress - Current progress (0-100)
 * @param {Object} props.handlers - Pointer event handlers from useScrubbing
 * @param {boolean} props.showThumb - Whether to show the scrub thumb
 * @param {string} props.className - Additional CSS classes
 * @param {string} props.variant - Style variant (default, fullscreen, mobile)
 */
const ProgressBar = ({
    progress = 0,
    handlers = {},
    showThumb = true,
    className = '',
    variant = 'default',
    trackRef = null,
}) => {
    const variantClasses = {
        default: 'player-progress-wrapper',
        fullscreen: 'player-fullscreen-progress-bar',
        mobile: 'mfp-progress-scrubber',
    };

    const trackClasses = {
        default: 'player-progress-track',
        fullscreen: 'player-fullscreen-progress-track',
        mobile: 'mfp-progress-track',
    };

    const fillClasses = {
        default: 'player-progress-fill',
        fullscreen: 'player-fullscreen-progress-fill',
        mobile: 'mfp-progress-fill',
    };

    const wrapperClass = variantClasses[variant] || variantClasses.default;
    const trackClass = trackClasses[variant] || trackClasses.default;
    const fillClass = fillClasses[variant] || fillClasses.default;

    return (
        <div
            className={`${wrapperClass} ${className}`}
            onPointerDown={handlers.onPointerDown}
            onPointerMove={handlers.onPointerMove}
            onPointerUp={handlers.onPointerUp}
            onPointerCancel={handlers.onPointerCancel}
            onClick={handlers.onClick}
        >
            <div ref={trackRef} className={trackClass}>
                <div className={fillClass} style={{ width: `${progress}%` }}>
                    {showThumb && variant === 'default' && (
                        <div className="player-progress-thumb" />
                    )}
                    {showThumb && variant === 'mobile' && (
                        <div className="mfp-progress-thumb" />
                    )}
                </div>
            </div>
        </div>
    );
};

ProgressBar.propTypes = {
    progress: PropTypes.number,
    handlers: PropTypes.shape({
        onPointerDown: PropTypes.func,
        onPointerMove: PropTypes.func,
        onPointerUp: PropTypes.func,
        onPointerCancel: PropTypes.func,
        onClick: PropTypes.func,
    }),
    showThumb: PropTypes.bool,
    className: PropTypes.string,
    variant: PropTypes.oneOf(['default', 'fullscreen', 'mobile']),
    trackRef: PropTypes.oneOfType([
        PropTypes.func,
        PropTypes.shape({ current: PropTypes.any }),
    ]),
};

export default ProgressBar;
