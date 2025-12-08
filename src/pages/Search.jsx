import React, { useState } from 'react';
import { Search as SearchIcon, X, Play, Download, Heart, Plus, Loader } from 'lucide-react';
import { usePlayer } from '../context/PlayerContext';
import { useLikedSongs } from '../context/LikedSongsContext';
import { useOffline } from '../context/OfflineContext';
import axios from 'axios';
import { downloadSong } from '../utils/download';
import AddToPlaylistModal from '../components/AddToPlaylistModal';

const Search = () => {
    const [query, setQuery] = useState('');
    const [results, setResults] = useState([]);
    const [loading, setLoading] = useState(false);
    const [downloading, setDownloading] = useState(null);
    const [addToPlaylistSong, setAddToPlaylistSong] = useState(null);
    const { playSong, currentSong, isPlaying } = usePlayer();
    const { isLiked, toggleLike } = useLikedSongs();
    const { isSongOffline } = useOffline();

    const categories = [
        { id: 1, title: 'Music', color: '#dc148c' },
        { id: 2, title: 'Podcasts', color: '#006450' },
        { id: 3, title: 'Live Events', color: '#8400e7' },
        { id: 4, title: 'Made For You', color: '#1e3264' },
        { id: 5, title: 'New Releases', color: '#e8115b' },
        { id: 6, title: 'Pop', color: '#148a08' },
        { id: 7, title: 'Hip-Hop', color: '#bc5900' },
        { id: 8, title: 'Rock', color: '#e91429' },
        { id: 9, title: 'Indie', color: '#7358ff' },
        { id: 10, title: 'Chill', color: '#503750' },
        { id: 11, title: 'Party', color: '#0d73ec' },
        { id: 12, title: 'Workout', color: '#777777' },
    ];

    const handleSearch = async (e) => {
        e.preventDefault();
        if (!query.trim()) return;

        setLoading(true);
        try {
            const response = await axios.get(
                `https://itunes.apple.com/search?term=${encodeURIComponent(query)}&media=music&limit=30`
            );
            const formattedResults = response.data.results.map(item => ({
                id: item.trackId,
                title: item.trackName,
                artist: item.artistName,
                album: item.collectionName,
                image: item.artworkUrl100?.replace('100x100', '300x300') || '',
                previewUrl: item.previewUrl,
                duration: item.trackTimeMillis
            }));
            setResults(formattedResults);
        } catch (error) {
            console.error("Search failed:", error);
        } finally {
            setLoading(false);
        }
    };

    const formatDuration = (ms) => {
        if (!ms) return '0:00';
        const minutes = Math.floor(ms / 60000);
        const seconds = Math.floor((ms % 60000) / 1000);
        return `${minutes}:${seconds.toString().padStart(2, '0')}`;
    };

    const handleDownload = async (e, song) => {
        e.stopPropagation();
        setDownloading(song.id);
        await downloadSong(song);
        setDownloading(null);
    };

    return (
        <div style={{ minHeight: '100%', backgroundColor: '#121212' }}>
            {/* Sticky Search Header */}
            <div style={{
                position: 'sticky',
                top: 0,
                zIndex: 20,
                backgroundColor: '#121212',
                padding: '16px 24px 12px'
            }}>
                <form onSubmit={handleSearch} style={{ position: 'relative', maxWidth: '364px' }}>
                    <SearchIcon
                        size={24}
                        style={{
                            position: 'absolute',
                            left: '12px',
                            top: '50%',
                            transform: 'translateY(-50%)',
                            color: '#121212',
                            pointerEvents: 'none'
                        }}
                    />
                    <input
                        type="text"
                        placeholder="What do you want to listen to?"
                        value={query}
                        onChange={(e) => setQuery(e.target.value)}
                        style={{
                            width: '100%',
                            padding: '14px 40px 14px 48px',
                            backgroundColor: '#fff',
                            border: 'none',
                            borderRadius: '500px',
                            fontSize: '14px',
                            fontWeight: 500,
                            color: '#121212',
                            outline: 'none'
                        }}
                    />
                    {query && (
                        <button
                            type="button"
                            onClick={() => { setQuery(''); setResults([]); }}
                            style={{
                                position: 'absolute',
                                right: '12px',
                                top: '50%',
                                transform: 'translateY(-50%)',
                                background: 'none',
                                border: 'none',
                                color: '#121212',
                                cursor: 'pointer',
                                padding: '4px'
                            }}
                        >
                            <X size={20} />
                        </button>
                    )}
                </form>
            </div>

            <div style={{ padding: '0 24px 120px' }}>
                {query ? (
                    loading ? (
                        <div style={{ display: 'flex', justifyContent: 'center', padding: '64px' }}>
                            <Loader size={32} className="animate-spin" style={{ color: '#1db954' }} />
                        </div>
                    ) : results.length > 0 ? (
                        <div>
                            <h2 style={{ fontSize: '24px', fontWeight: 700, marginBottom: '16px', color: '#fff' }}>
                                Songs
                            </h2>
                            <div style={{ display: 'flex', flexDirection: 'column' }}>
                                {results.map((song, index) => {
                                    const isThisSong = currentSong?.id === song.id;
                                    const liked = isLiked(song.id);
                                    const isOffline = isSongOffline(song.id);

                                    return (
                                        <div
                                            key={song.id}
                                            onClick={() => playSong(song)}
                                            style={{
                                                display: 'grid',
                                                gridTemplateColumns: '16px 4fr 2fr 120px',
                                                gap: '16px',
                                                alignItems: 'center',
                                                padding: '8px',
                                                borderRadius: '4px',
                                                cursor: 'pointer',
                                                transition: 'background 0.2s'
                                            }}
                                            onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'rgba(255,255,255,0.1)'}
                                            onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
                                            className="track-row"
                                        >
                                            {/* Number / Play icon */}
                                            <div style={{
                                                display: 'flex',
                                                justifyContent: 'center',
                                                color: isThisSong && isPlaying ? '#1db954' : '#b3b3b3',
                                                fontSize: '14px'
                                            }}>
                                                <span className="track-num">{index + 1}</span>
                                                <Play size={14} fill="#fff" className="track-play" style={{ display: 'none' }} />
                                            </div>

                                            {/* Song info */}
                                            <div style={{ display: 'flex', alignItems: 'center', gap: '12px', minWidth: 0 }}>
                                                <div style={{ position: 'relative' }}>
                                                    <img
                                                        src={song.image}
                                                        alt={song.title}
                                                        style={{
                                                            width: '40px',
                                                            height: '40px',
                                                            borderRadius: '4px',
                                                            objectFit: 'cover'
                                                        }}
                                                        onError={(e) => { e.target.src = '/placeholder.svg'; }}
                                                    />
                                                    {isOffline && (
                                                        <div style={{
                                                            position: 'absolute',
                                                            bottom: '-4px',
                                                            right: '-4px',
                                                            width: '14px',
                                                            height: '14px',
                                                            borderRadius: '50%',
                                                            backgroundColor: '#1db954',
                                                            display: 'flex',
                                                            alignItems: 'center',
                                                            justifyContent: 'center'
                                                        }}>
                                                            <Download size={8} color="#000" />
                                                        </div>
                                                    )}
                                                </div>
                                                <div style={{ minWidth: 0 }}>
                                                    <div style={{
                                                        color: isThisSong ? '#1db954' : '#fff',
                                                        fontSize: '16px',
                                                        fontWeight: 400,
                                                        whiteSpace: 'nowrap',
                                                        overflow: 'hidden',
                                                        textOverflow: 'ellipsis'
                                                    }}>{song.title}</div>
                                                    <div style={{
                                                        color: '#b3b3b3',
                                                        fontSize: '14px',
                                                        whiteSpace: 'nowrap',
                                                        overflow: 'hidden',
                                                        textOverflow: 'ellipsis'
                                                    }}>{song.artist}</div>
                                                </div>
                                            </div>

                                            {/* Album */}
                                            <div style={{
                                                color: '#b3b3b3',
                                                fontSize: '14px',
                                                whiteSpace: 'nowrap',
                                                overflow: 'hidden',
                                                textOverflow: 'ellipsis'
                                            }}>{song.album}</div>

                                            {/* Actions & Duration */}
                                            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: '8px' }}>
                                                <button
                                                    onClick={(e) => { e.stopPropagation(); toggleLike(song); }}
                                                    className="action-btn"
                                                    style={{
                                                        opacity: liked ? 1 : 0,
                                                        padding: '4px',
                                                        color: liked ? '#1db954' : '#b3b3b3',
                                                        transition: 'all 0.2s'
                                                    }}
                                                    title={liked ? 'Remove from Liked Songs' : 'Add to Liked Songs'}
                                                >
                                                    <Heart size={16} fill={liked ? '#1db954' : 'none'} />
                                                </button>
                                                <button
                                                    onClick={(e) => { e.stopPropagation(); setAddToPlaylistSong(song); }}
                                                    className="action-btn"
                                                    style={{
                                                        opacity: 0,
                                                        padding: '4px',
                                                        color: '#b3b3b3',
                                                        transition: 'all 0.2s'
                                                    }}
                                                    title="Add to playlist"
                                                >
                                                    <Plus size={16} />
                                                </button>
                                                <button
                                                    onClick={(e) => handleDownload(e, song)}
                                                    className="action-btn"
                                                    style={{
                                                        opacity: 0,
                                                        padding: '4px',
                                                        color: '#b3b3b3',
                                                        transition: 'all 0.2s'
                                                    }}
                                                    title="Download for offline"
                                                >
                                                    {downloading === song.id ? (
                                                        <Loader size={16} className="animate-spin" />
                                                    ) : (
                                                        <Download size={16} />
                                                    )}
                                                </button>
                                                <span style={{ color: '#b3b3b3', fontSize: '14px', fontVariantNumeric: 'tabular-nums', minWidth: '40px', textAlign: 'right' }}>
                                                    {formatDuration(song.duration)}
                                                </span>
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        </div>
                    ) : (
                        <div style={{ textAlign: 'center', padding: '64px' }}>
                            <h3 style={{ fontSize: '24px', fontWeight: 700, marginBottom: '8px' }}>
                                No results found for "{query}"
                            </h3>
                            <p style={{ color: '#b3b3b3' }}>
                                Please check your spelling or try different keywords.
                            </p>
                        </div>
                    )
                ) : (
                    <>
                        <h2 style={{ fontSize: '24px', fontWeight: 700, marginBottom: '20px', color: '#fff' }}>
                            Browse all
                        </h2>
                        <div style={{
                            display: 'grid',
                            gridTemplateColumns: 'repeat(auto-fill, minmax(180px, 1fr))',
                            gap: '24px'
                        }}>
                            {categories.map((cat) => (
                                <div
                                    key={cat.id}
                                    style={{
                                        position: 'relative',
                                        height: '220px',
                                        borderRadius: '8px',
                                        overflow: 'hidden',
                                        cursor: 'pointer',
                                        backgroundColor: cat.color,
                                        transition: 'transform 0.2s'
                                    }}
                                    onMouseEnter={(e) => e.currentTarget.style.transform = 'scale(1.02)'}
                                    onMouseLeave={(e) => e.currentTarget.style.transform = 'scale(1)'}
                                >
                                    <span style={{
                                        position: 'absolute',
                                        top: '16px',
                                        left: '16px',
                                        fontSize: '24px',
                                        fontWeight: 700,
                                        color: '#fff',
                                        maxWidth: '80%',
                                        lineHeight: 1.2
                                    }}>{cat.title}</span>
                                </div>
                            ))}
                        </div>
                    </>
                )}
            </div>

            {/* Add to Playlist Modal */}
            <AddToPlaylistModal
                isOpen={!!addToPlaylistSong}
                onClose={() => setAddToPlaylistSong(null)}
                song={addToPlaylistSong}
            />

            <style>{`
                .track-row:hover .track-num { display: none; }
                .track-row:hover .track-play { display: block !important; }
                .track-row:hover .action-btn { opacity: 1 !important; }
                .action-btn:hover { color: #fff !important; transform: scale(1.1); }
                @keyframes spin { from { transform: rotate(0deg); } to { transform: rotate(360deg); } }
                .animate-spin { animation: spin 1s linear infinite; }
            `}</style>
        </div>
    );
};

export default Search;
