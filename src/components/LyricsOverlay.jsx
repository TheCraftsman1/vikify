import React, { useEffect, useRef, useState } from 'react';
import { useLyrics } from '../context/LyricsContext';
import { usePlayer } from '../context/PlayerContext';
import { Loader, Music2, X } from 'lucide-react';

const LyricsOverlay = ({ onClose }) => {
    const { lyrics, plainLyrics, isLoading, error } = useLyrics();
    const { progress, currentSong } = usePlayer();
    const listRef = useRef(null);
    const [activeIndex, setActiveIndex] = useState(-1);
    const [userScrolled, setUserScrolled] = useState(false);
    const scrollTimeoutRef = useRef(null);

    // Determine active line based on progress
    useEffect(() => {
        if (!lyrics || lyrics.length === 0) return;

        // Find the index of the line that started most recently
        let index = -1;
        for (let i = 0; i < lyrics.length; i++) {
            if (lyrics[i].time <= progress) {
                index = i;
            } else {
                break;
            }
        }
        setActiveIndex(index);
    }, [progress, lyrics]);

    // Auto-scroll to active line
    useEffect(() => {
        if (userScrolled || activeIndex === -1 || !listRef.current) return;

        const activeEl = listRef.current.children[activeIndex];
        if (activeEl) {
            activeEl.scrollIntoView({
                behavior: 'smooth',
                block: 'center',
            });
        }
    }, [activeIndex, userScrolled]);

    // Handle user scroll to pause auto-scroll temporarily
    const handleScroll = () => {
        setUserScrolled(true);
        if (scrollTimeoutRef.current) clearTimeout(scrollTimeoutRef.current);
        scrollTimeoutRef.current = setTimeout(() => {
            setUserScrolled(false);
        }, 3000); // Resume auto-scroll after 3s of inactivity
    };

    if (!currentSong) return null;

    // Dominant color logic (simplified, ideally shared or passed prop)
    const bgColor = '#121212'; // Default black

    return (
        <div style={{
            position: 'fixed',
            top: 0,
            left: 0,
            right: 0,
            bottom: '90px', // Above player bar
            backgroundColor: 'rgba(0,0,0,0.9)', // Fallback / Darker overlay
            zIndex: 1000,
            display: 'flex',
            flexDirection: 'column',
            overflow: 'hidden',
            backdropFilter: 'blur(20px)',
            animation: 'fadeIn 0.3s ease-out'
        }}>
            {/* Header */}
            <div style={{
                padding: '24px',
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                background: 'linear-gradient(180deg, rgba(0,0,0,0.4) 0%, transparent 100%)'
            }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                    <img
                        src={currentSong.image}
                        alt={currentSong.title}
                        style={{ width: '56px', height: '56px', borderRadius: '4px', boxShadow: '0 4px 12px rgba(0,0,0,0.3)' }}
                    />
                    <div>
                        <h2 style={{ fontSize: '18px', fontWeight: '700', margin: 0 }}>{currentSong.title}</h2>
                        <p style={{ color: '#b3b3b3', margin: 0, fontSize: '14px' }}>{currentSong.artist}</p>
                    </div>
                </div>
                <button
                    onClick={onClose}
                    style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#fff', padding: '8px' }}
                >
                    <X size={24} />
                </button>
            </div>

            {/* Content */}
            <div
                ref={listRef}
                onScroll={handleScroll}
                style={{
                    flex: 1,
                    overflowY: 'auto',
                    padding: '40px 24px 100px', // Extra padding bottom for scrolling
                    textAlign: 'left',
                    maxWidth: '800px',
                    margin: '0 auto',
                    width: '100%',
                    scrollbarWidth: 'none' // Hide scrollbar for cleaner look
                }}
            >
                {isLoading ? (
                    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100%', opacity: 0.6 }}>
                        <Loader size={32} className="animate-spin" />
                    </div>
                ) : lyrics && lyrics.length > 0 ? (
                    lyrics.map((line, i) => (
                        <p
                            key={i}
                            style={{
                                fontSize: 'clamp(24px, 4vw, 32px)',
                                fontWeight: '700',
                                color: i === activeIndex ? '#fff' : 'rgba(255,255,255,0.4)',
                                margin: '0 0 24px 0',
                                transition: 'color 0.3s ease, transform 0.3s ease',
                                transform: i === activeIndex ? 'scale(1.02)' : 'scale(1)',
                                transformOrigin: 'left',
                                lineHeight: 1.4,
                                cursor: 'default'
                            }}
                        >
                            {line.text}
                        </p>
                    ))
                ) : plainLyrics ? (
                    <div style={{ whiteSpace: 'pre-wrap', fontSize: '20px', lineHeight: 1.6, color: '#e0e0e0' }}>
                        {plainLyrics}
                    </div>
                ) : (
                    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', height: '100%', opacity: 0.5 }}>
                        <Music2 size={48} style={{ marginBottom: '16px' }} />
                        <p>No lyrics found for this song</p>
                    </div>
                )}
            </div>

            <style>{`
                @keyframes fadeIn {
                    from { opacity: 0; transform: translateY(20px); }
                    to { opacity: 1; transform: translateY(0); }
                }
            `}</style>
        </div>
    );
};

export default LyricsOverlay;
