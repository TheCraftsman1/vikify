import React from 'react';
import PropTypes from 'prop-types';
import { Play, Pause, Loader } from 'lucide-react';

/**
 * Reusable play/pause button with loading state.
 * Consistent styling across Player, MobileFullScreenPlayer, and queue items.
 * 
 * @param {Object} props - Component props
 * @param {boolean} props.isPlaying - Current playing state
 * @param {boolean} props.isLoading - Loading state
 * @param {Function} props.onClick - Click handler
 * @param {string} props.size - Button size (small, medium, large, xlarge)
 * @param {string} props.variant - Style variant (primary, mobile, fullscreen)
 */
const PlayButton = ({
    isPlaying = false,
    isLoading = false,
    onClick,
    size = 'medium',
    variant = 'primary',
    className = '',
    ...props
}) => {
    const sizeConfig = {
        small: { button: 32, icon: 16 },
        medium: { button: 40, icon: 20 },
        large: { button: 64, icon: 28 },
        xlarge: { button: 72, icon: 32 },
    };

    const config = sizeConfig[size] || sizeConfig.medium;

    const variantClasses = {
        primary: 'player-main-play-btn',
        mobile: 'mobile-play-btn',
        fullscreen: 'mfp-play-btn',
    };

    const buttonClass = variantClasses[variant] || variantClasses.primary;
    const sizeClass = size === 'large' ? 'large' : size === 'xlarge' ? 'large' : '';

    return (
        <button
            onClick={onClick}
            className={`${buttonClass} ${sizeClass} ${className}`}
            disabled={isLoading}
            {...props}
        >
            {isLoading ? (
                <Loader size={config.icon} color="#000" className="animate-spin" />
            ) : isPlaying ? (
                <Pause size={config.icon} fill="#000" color="#000" />
            ) : (
                <Play size={config.icon} fill="#000" color="#000" style={{ marginLeft: '2px' }} />
            )}
        </button>
    );
};

PlayButton.propTypes = {
    isPlaying: PropTypes.bool,
    isLoading: PropTypes.bool,
    onClick: PropTypes.func.isRequired,
    size: PropTypes.oneOf(['small', 'medium', 'large', 'xlarge']),
    variant: PropTypes.oneOf(['primary', 'mobile', 'fullscreen']),
    className: PropTypes.string,
};

export default PlayButton;
