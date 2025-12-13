import React from 'react';
import PropTypes from 'prop-types';
import { Timer } from 'lucide-react';

/**
 * Sleep timer menu component with preset duration options.
 * 
 * @param {Object} props - Component props
 * @param {number} props.sleepTimer - Current timer value in minutes (null if not set)
 * @param {Function} props.onSetTimer - Handler for setting timer
 * @param {Function} props.onCancelTimer - Handler for canceling timer
 * @param {Function} props.onClose - Handler for closing menu
 */
const SleepTimerMenu = ({
    sleepTimer,
    onSetTimer,
    onCancelTimer,
    onClose,
}) => {
    const presets = [5, 15, 30, 45, 60, 90];

    const handleSetTimer = (mins) => {
        onSetTimer(mins);
        onClose();
    };

    const handleCancel = () => {
        onCancelTimer();
        onClose();
    };

    return (
        <div className="player-sleep-menu">
            <div className="player-sleep-menu-title">SLEEP TIMER</div>
            {presets.map(mins => (
                <button
                    key={mins}
                    onClick={() => handleSetTimer(mins)}
                    className={`player-sleep-menu-item ${sleepTimer === mins ? 'active' : ''}`}
                >
                    {mins} minutes
                </button>
            ))}
            {sleepTimer && (
                <button
                    onClick={handleCancel}
                    className="player-sleep-menu-item cancel"
                >
                    Turn off timer
                </button>
            )}
        </div>
    );
};

SleepTimerMenu.propTypes = {
    sleepTimer: PropTypes.number,
    onSetTimer: PropTypes.func.isRequired,
    onCancelTimer: PropTypes.func.isRequired,
    onClose: PropTypes.func.isRequired,
};

/**
 * Sleep timer button with badge showing remaining time.
 * 
 * @param {Object} props - Component props
 * @param {number} props.sleepTimer - Current timer value in minutes
 * @param {boolean} props.isOpen - Whether menu is open
 * @param {Function} props.onClick - Click handler to toggle menu
 */
const SleepTimerButton = ({ sleepTimer, isOpen, onClick }) => {
    return (
        <div style={{ position: 'relative' }}>
            <button
                onClick={onClick}
                className={`player-icon-btn ${sleepTimer ? 'active' : ''}`}
                title={sleepTimer ? `Sleep in ${sleepTimer} min` : 'Sleep Timer'}
            >
                <Timer size={16} />
                {sleepTimer && (
                    <span className="player-sleep-badge">{sleepTimer}</span>
                )}
            </button>
        </div>
    );
};

SleepTimerButton.propTypes = {
    sleepTimer: PropTypes.number,
    isOpen: PropTypes.bool,
    onClick: PropTypes.func.isRequired,
};

export { SleepTimerMenu, SleepTimerButton };
export default SleepTimerMenu;
