import React from 'react';
import { Download, X, Check, Loader } from 'lucide-react';
import { useOffline } from '../context/OfflineContext';

/**
 * Toast notification showing download progress
 * Appears at bottom of screen when downloading
 */
const DownloadProgress = () => {
    const { downloadProgress, downloadQueue } = useOffline();

    // Show nothing if no active downloads
    if (!downloadProgress && (!downloadQueue || downloadQueue.length === 0)) {
        return null;
    }

    const { currentSong, progress, total, current, status } = downloadProgress || {};

    return (
        <div style={{
            position: 'fixed',
            bottom: '100px', // Above the player bar
            left: '50%',
            transform: 'translateX(-50%)',
            backgroundColor: '#282828',
            borderRadius: '8px',
            padding: '16px 24px',
            display: 'flex',
            alignItems: 'center',
            gap: '16px',
            boxShadow: '0 8px 24px rgba(0,0,0,0.5)',
            zIndex: 1000,
            minWidth: '320px',
            maxWidth: '480px'
        }}>
            {/* Icon */}
            <div style={{
                width: '48px',
                height: '48px',
                borderRadius: '4px',
                backgroundColor: '#1db954',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                flexShrink: 0
            }}>
                {status === 'complete' ? (
                    <Check size={24} color="#000" />
                ) : (
                    <Loader size={24} color="#000" className="spin" />
                )}
            </div>

            {/* Content */}
            <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{
                    fontSize: '14px',
                    fontWeight: 600,
                    color: '#fff',
                    marginBottom: '4px'
                }}>
                    {status === 'complete' ? 'Download Complete!' : 'Downloading...'}
                </div>

                {currentSong && (
                    <div style={{
                        fontSize: '13px',
                        color: '#b3b3b3',
                        whiteSpace: 'nowrap',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis'
                    }}>
                        {currentSong.title} - {currentSong.artist}
                    </div>
                )}

                {/* Progress bar */}
                {total && total > 1 && (
                    <div style={{ marginTop: '8px' }}>
                        <div style={{
                            display: 'flex',
                            justifyContent: 'space-between',
                            fontSize: '12px',
                            color: '#b3b3b3',
                            marginBottom: '4px'
                        }}>
                            <span>{current} of {total}</span>
                            <span>{Math.round((current / total) * 100)}%</span>
                        </div>
                        <div style={{
                            height: '4px',
                            backgroundColor: '#404040',
                            borderRadius: '2px',
                            overflow: 'hidden'
                        }}>
                            <div style={{
                                height: '100%',
                                backgroundColor: '#1db954',
                                borderRadius: '2px',
                                width: `${(current / total) * 100}%`,
                                transition: 'width 0.3s ease'
                            }} />
                        </div>
                    </div>
                )}

                {/* Single song progress */}
                {progress !== undefined && (!total || total === 1) && (
                    <div style={{ marginTop: '8px' }}>
                        <div style={{
                            height: '4px',
                            backgroundColor: '#404040',
                            borderRadius: '2px',
                            overflow: 'hidden'
                        }}>
                            <div style={{
                                height: '100%',
                                backgroundColor: '#1db954',
                                borderRadius: '2px',
                                width: `${progress}%`,
                                transition: 'width 0.3s ease'
                            }} />
                        </div>
                    </div>
                )}
            </div>

            <style>{`
                @keyframes spin {
                    from { transform: rotate(0deg); }
                    to { transform: rotate(360deg); }
                }
                .spin {
                    animation: spin 1s linear infinite;
                }
            `}</style>
        </div>
    );
};

export default DownloadProgress;
