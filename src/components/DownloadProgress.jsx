import React, { useState } from 'react';
import { Download, X, Check, Loader, ChevronDown, ChevronUp } from 'lucide-react';
import { useOffline } from '../context/OfflineContext';

/**
 * Enhanced download progress toast with queue visibility
 * Shows current download and expandable queue list
 */
const DownloadProgress = () => {
    const { downloadProgress, downloadQueue, isDownloading } = useOffline();
    const [isExpanded, setIsExpanded] = useState(false);

    // Show nothing if no active downloads
    if (!downloadProgress && (!downloadQueue || downloadQueue.length === 0) && !isDownloading) {
        return null;
    }

    const { currentSong, status, queueSize } = downloadProgress || {};
    const pendingCount = downloadQueue?.length || 0;
    const totalInQueue = pendingCount + (isDownloading ? 1 : 0);

    return (
        <div style={{
            position: 'fixed',
            bottom: '100px',
            left: '50%',
            transform: 'translateX(-50%)',
            backgroundColor: '#282828',
            borderRadius: '12px',
            boxShadow: '0 8px 32px rgba(0,0,0,0.6)',
            zIndex: 1000,
            minWidth: '340px',
            maxWidth: '420px',
            overflow: 'hidden',
            transition: 'all 0.3s ease'
        }}>
            {/* Main Toast Bar */}
            <div style={{
                padding: '14px 20px',
                display: 'flex',
                alignItems: 'center',
                gap: '14px',
            }}>
                {/* Animated Icon */}
                <div style={{
                    width: '44px',
                    height: '44px',
                    borderRadius: '8px',
                    background: status === 'complete' 
                        ? 'linear-gradient(135deg, #1db954, #1ed760)' 
                        : 'linear-gradient(135deg, #1db954, #169c46)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    flexShrink: 0,
                    boxShadow: '0 4px 12px rgba(29, 185, 84, 0.3)'
                }}>
                    {status === 'complete' ? (
                        <Check size={22} color="#000" strokeWidth={3} />
                    ) : (
                        <Loader size={22} color="#000" className="download-spin" />
                    )}
                </div>

                {/* Content */}
                <div style={{ flex: 1, minWidth: 0 }}>
                    <div style={{
                        fontSize: '14px',
                        fontWeight: 600,
                        color: '#fff',
                        marginBottom: '3px',
                        display: 'flex',
                        alignItems: 'center',
                        gap: '8px'
                    }}>
                        {status === 'complete' ? 'Download Complete!' : 'Downloading...'}
                        {totalInQueue > 1 && status !== 'complete' && (
                            <span style={{
                                fontSize: '11px',
                                backgroundColor: '#1db954',
                                color: '#000',
                                padding: '2px 8px',
                                borderRadius: '10px',
                                fontWeight: 700
                            }}>
                                {totalInQueue} in queue
                            </span>
                        )}
                    </div>

                    {currentSong && (
                        <div style={{
                            fontSize: '13px',
                            color: '#b3b3b3',
                            whiteSpace: 'nowrap',
                            overflow: 'hidden',
                            textOverflow: 'ellipsis'
                        }}>
                            {currentSong.title}
                            <span style={{ color: '#666', margin: '0 6px' }}>•</span>
                            {currentSong.artist}
                        </div>
                    )}

                    {/* Progress indicator */}
                    {status !== 'complete' && (
                        <div style={{
                            marginTop: '8px',
                            height: '3px',
                            backgroundColor: '#404040',
                            borderRadius: '2px',
                            overflow: 'hidden'
                        }}>
                            <div 
                                className="download-progress-bar"
                                style={{
                                    height: '100%',
                                    backgroundColor: '#1db954',
                                    borderRadius: '2px',
                                    width: '30%'
                                }} 
                            />
                        </div>
                    )}
                </div>

                {/* Expand button for queue */}
                {pendingCount > 0 && (
                    <button
                        onClick={() => setIsExpanded(!isExpanded)}
                        style={{
                            background: 'rgba(255,255,255,0.1)',
                            border: 'none',
                            borderRadius: '50%',
                            width: '32px',
                            height: '32px',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            cursor: 'pointer',
                            transition: 'all 0.2s'
                        }}
                    >
                        {isExpanded ? (
                            <ChevronDown size={18} color="#fff" />
                        ) : (
                            <ChevronUp size={18} color="#fff" />
                        )}
                    </button>
                )}
            </div>

            {/* Expandable Queue List */}
            {isExpanded && pendingCount > 0 && (
                <div style={{
                    borderTop: '1px solid rgba(255,255,255,0.1)',
                    maxHeight: '200px',
                    overflowY: 'auto'
                }}>
                    <div style={{
                        padding: '8px 20px',
                        fontSize: '11px',
                        color: '#888',
                        textTransform: 'uppercase',
                        letterSpacing: '0.5px'
                    }}>
                        Up Next ({pendingCount})
                    </div>
                    {downloadQueue.slice(0, 5).map((song, index) => (
                        <div
                            key={song.id}
                            style={{
                                padding: '10px 20px',
                                display: 'flex',
                                alignItems: 'center',
                                gap: '12px',
                                backgroundColor: index % 2 === 0 ? 'transparent' : 'rgba(255,255,255,0.02)'
                            }}
                        >
                            <img
                                src={song.image}
                                alt=""
                                style={{
                                    width: '36px',
                                    height: '36px',
                                    borderRadius: '4px',
                                    objectFit: 'cover'
                                }}
                                onError={(e) => { e.target.src = '/placeholder.svg'; }}
                            />
                            <div style={{ minWidth: 0, flex: 1 }}>
                                <div style={{
                                    fontSize: '13px',
                                    color: '#fff',
                                    whiteSpace: 'nowrap',
                                    overflow: 'hidden',
                                    textOverflow: 'ellipsis'
                                }}>
                                    {song.title}
                                </div>
                                <div style={{
                                    fontSize: '11px',
                                    color: '#888'
                                }}>
                                    {song.artist}
                                </div>
                            </div>
                            <Download size={14} color="#666" />
                        </div>
                    ))}
                    {pendingCount > 5 && (
                        <div style={{
                            padding: '12px 20px',
                            fontSize: '12px',
                            color: '#888',
                            textAlign: 'center'
                        }}>
                            +{pendingCount - 5} more songs
                        </div>
                    )}
                </div>
            )}

            <style>{`
                @keyframes download-spin {
                    from { transform: rotate(0deg); }
                    to { transform: rotate(360deg); }
                }
                .download-spin {
                    animation: download-spin 1s linear infinite;
                }
                @keyframes download-progress {
                    0% { transform: translateX(-100%); }
                    100% { transform: translateX(400%); }
                }
                .download-progress-bar {
                    animation: download-progress 1.5s ease-in-out infinite;
                }
            `}</style>
        </div>
    );
};

export default DownloadProgress;
