import React from 'react';
import PropTypes from 'prop-types';

/**
 * Queue panel component displaying now playing and upcoming songs.
 * 
 * @param {Object} props - Component props
 * @param {Object} props.currentSong - Currently playing song
 * @param {Array} props.queue - Manual queue items
 * @param {Array} props.upNextQueue - Autoplay queue items
 * @param {Function} props.onPlaySong - Handler for playing a song
 * @param {boolean} props.isPlaying - Whether currently playing
 */
const PlayerQueue = ({
    currentSong,
    queue = [],
    upNextQueue = [],
    onPlaySong,
    isPlaying = false,
}) => {
    return (
        <div className="player-queue-panel">
            <h3 className="player-queue-title">Queue</h3>

            {/* Now Playing */}
            <div className="player-queue-section">
                <h4 className="player-queue-section-title">Now Playing</h4>
                {currentSong && (
                    <div className="player-queue-item">
                        <img
                            src={currentSong.image}
                            alt=""
                            className="player-queue-item-image"
                            onError={(e) => { e.target.src = '/placeholder.svg'; }}
                        />
                        <div className="player-queue-item-info">
                            <div className="player-queue-item-title playing">
                                {currentSong.title}
                            </div>
                            <div className="player-queue-item-artist">
                                {currentSong.artist}
                            </div>
                        </div>
                        {isPlaying && (
                            <div className="player-eq-bars">
                                <div className="player-eq-bar"></div>
                                <div className="player-eq-bar"></div>
                                <div className="player-eq-bar"></div>
                            </div>
                        )}
                    </div>
                )}
            </div>

            {/* Manual Queue */}
            {queue.length > 0 && (
                <div className="player-queue-section">
                    <h4 className="player-queue-section-title">Next in Queue</h4>
                    {queue.map((song, i) => (
                        <div
                            key={`queue-${i}`}
                            className="player-queue-item"
                            onClick={() => onPlaySong(song)}
                        >
                            <div className="player-queue-item-number">{i + 1}</div>
                            <img
                                src={song.image}
                                alt=""
                                className="player-queue-item-image"
                                onError={(e) => { e.target.src = '/placeholder.svg'; }}
                            />
                            <div className="player-queue-item-info">
                                <div className="player-queue-item-title">{song.title}</div>
                                <div className="player-queue-item-artist">{song.artist}</div>
                            </div>
                        </div>
                    ))}
                </div>
            )}

            {/* Autoplay Queue */}
            {upNextQueue.length > 0 && (
                <div className="player-queue-section">
                    <h4 className="player-queue-section-title">Next From: Autoplay</h4>
                    {upNextQueue.map((song, i) => (
                        <div
                            key={`upnext-${i}`}
                            className="player-queue-item"
                            onClick={() => onPlaySong(song)}
                        >
                            <img
                                src={song.image}
                                alt=""
                                className="player-queue-item-image"
                                onError={(e) => { e.target.src = '/placeholder.svg'; }}
                            />
                            <div className="player-queue-item-info">
                                <div className="player-queue-item-title">{song.title}</div>
                                <div className="player-queue-item-artist">{song.artist}</div>
                            </div>
                        </div>
                    ))}
                </div>
            )}

            {queue.length === 0 && upNextQueue.length === 0 && (
                <div className="player-queue-empty">
                    <p>No songs in queue</p>
                </div>
            )}
        </div>
    );
};

PlayerQueue.propTypes = {
    currentSong: PropTypes.shape({
        id: PropTypes.string,
        title: PropTypes.string,
        artist: PropTypes.string,
        image: PropTypes.string,
    }),
    queue: PropTypes.array,
    upNextQueue: PropTypes.array,
    onPlaySong: PropTypes.func.isRequired,
    isPlaying: PropTypes.bool,
};

export default PlayerQueue;
