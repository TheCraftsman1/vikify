import React from 'react';
import { Download } from 'lucide-react';

/**
 * Small badge indicating a song is available offline.
 * @param {Object} props
 * @param {boolean} props.small - Use smaller size
 */
const OfflineBadge = ({ small = false }) => (
    <span
        className={`offline-badge ${small ? 'offline-badge-small' : ''}`}
        title="Available offline"
    >
        <Download size={small ? 6 : 8} strokeWidth={3} />
    </span>
);

export default OfflineBadge;
