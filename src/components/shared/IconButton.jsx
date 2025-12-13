import React from 'react';
import PropTypes from 'prop-types';

/**
 * Reusable icon button component with consistent styling.
 * Replaces the pattern of inline-styled icon buttons throughout the app.
 * 
 * @param {Object} props - Component props
 * @param {React.ReactNode} props.icon - Icon element to render
 * @param {Function} props.onClick - Click handler
 * @param {boolean} props.active - Whether button is in active state
 * @param {boolean} props.disabled - Whether button is disabled
 * @param {string} props.title - Tooltip title
 * @param {string} props.className - Additional CSS classes
 * @param {string} props.size - Button size variant
 */
const IconButton = ({
    icon,
    onClick,
    active = false,
    disabled = false,
    title = '',
    className = '',
    size = 'default',
    type = 'button',
    ...props
}) => {
    const sizeClasses = {
        small: 'player-icon-btn--small',
        default: '',
        large: 'player-icon-btn--large',
    };

    const classes = [
        'player-icon-btn',
        active ? 'active' : '',
        sizeClasses[size] || '',
        className,
    ].filter(Boolean).join(' ');

    return (
        <button
            type={type}
            onClick={onClick}
            disabled={disabled}
            title={title}
            className={classes}
            {...props}
        >
            {icon}
        </button>
    );
};

IconButton.propTypes = {
    icon: PropTypes.node.isRequired,
    onClick: PropTypes.func,
    active: PropTypes.bool,
    disabled: PropTypes.bool,
    title: PropTypes.string,
    className: PropTypes.string,
    size: PropTypes.oneOf(['small', 'default', 'large']),
    type: PropTypes.string,
};

export default IconButton;
