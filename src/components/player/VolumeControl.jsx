import React from 'react';
import PropTypes from 'prop-types';
import { Volume2, Volume1, VolumeX } from 'lucide-react';

/**
 * Volume control component with mute toggle and slider.
 * 
 * @param {Object} props - Component props
 * @param {number} props.volume - Current volume (0-1)
 * @param {boolean} props.isMuted - Whether audio is muted
 * @param {Function} props.onVolumeChange - Handler for volume change
 * @param {Function} props.onToggleMute - Handler for mute toggle
 */
const VolumeControl = ({
    volume = 1,
    isMuted = false,
    onVolumeChange,
    onToggleMute,
}) => {
    const getVolumeIcon = () => {
        if (isMuted || volume === 0) return <VolumeX size={16} />;
        if (volume < 0.5) return <Volume1 size={16} />;
        return <Volume2 size={16} />;
    };

    const handleBarClick = (e) => {
        const rect = e.currentTarget.getBoundingClientRect();
        const newVolume = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
        onVolumeChange(newVolume);
    };

    const effectiveVolume = isMuted ? 0 : volume;

    return (
        <div className="player-volume-wrapper">
            <button
                onClick={onToggleMute}
                className="player-icon-btn"
                title={isMuted ? 'Unmute' : 'Mute'}
            >
                {getVolumeIcon()}
            </button>
            <div
                onClick={handleBarClick}
                className="player-volume-bar"
            >
                <div
                    className="player-volume-fill"
                    style={{ width: `${effectiveVolume * 100}%` }}
                />
            </div>
        </div>
    );
};

VolumeControl.propTypes = {
    volume: PropTypes.number,
    isMuted: PropTypes.bool,
    onVolumeChange: PropTypes.func.isRequired,
    onToggleMute: PropTypes.func.isRequired,
};

export default VolumeControl;
