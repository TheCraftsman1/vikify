import React, { useEffect, useState } from 'react';
import { X, Keyboard } from 'lucide-react';

/**
 * Keyboard shortcuts help modal.
 * Triggered by pressing '?' key.
 */
const KeyboardHelp = () => {
    const [isOpen, setIsOpen] = useState(false);

    useEffect(() => {
        const handleKeyDown = (e) => {
            // Open on '?' (Shift + /)
            if (e.key === '?' && !e.ctrlKey && !e.metaKey) {
                e.preventDefault();
                setIsOpen(prev => !prev);
            }
            // Close on Escape
            if (e.key === 'Escape' && isOpen) {
                setIsOpen(false);
            }
        };

        window.addEventListener('keydown', handleKeyDown);
        return () => window.removeEventListener('keydown', handleKeyDown);
    }, [isOpen]);

    if (!isOpen) return null;

    const shortcuts = [
        { key: 'Space', action: 'Play / Pause' },
        { key: '←', action: 'Previous track' },
        { key: '→', action: 'Next track' },
        { key: '↑', action: 'Volume up' },
        { key: '↓', action: 'Volume down' },
        { key: 'M', action: 'Mute / Unmute' },
        { key: 'S', action: 'Toggle shuffle' },
        { key: 'F', action: 'Toggle fullscreen player' },
        { key: 'Esc', action: 'Exit fullscreen' },
        { key: '?', action: 'Show this help' },
    ];

    return (
        <div
            style={{
                position: 'fixed',
                inset: 0,
                backgroundColor: 'rgba(0,0,0,0.8)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                zIndex: 9999,
                backdropFilter: 'blur(8px)',
            }}
            onClick={() => setIsOpen(false)}
        >
            <div
                style={{
                    backgroundColor: '#282828',
                    borderRadius: '12px',
                    padding: '24px',
                    maxWidth: '400px',
                    width: '90%',
                    boxShadow: '0 8px 32px rgba(0,0,0,0.5)',
                }}
                onClick={e => e.stopPropagation()}
            >
                {/* Header */}
                <div style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    marginBottom: '20px'
                }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                        <Keyboard size={24} color="#1db954" />
                        <h2 style={{ margin: 0, fontSize: '20px', fontWeight: 700 }}>
                            Keyboard Shortcuts
                        </h2>
                    </div>
                    <button
                        onClick={() => setIsOpen(false)}
                        style={{
                            background: 'transparent',
                            border: 'none',
                            color: '#b3b3b3',
                            cursor: 'pointer',
                            padding: '4px',
                        }}
                    >
                        <X size={20} />
                    </button>
                </div>

                {/* Shortcuts list */}
                <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                    {shortcuts.map(({ key, action }) => (
                        <div
                            key={key}
                            style={{
                                display: 'flex',
                                justifyContent: 'space-between',
                                alignItems: 'center',
                            }}
                        >
                            <span style={{ color: '#b3b3b3' }}>{action}</span>
                            <kbd style={{
                                backgroundColor: '#404040',
                                padding: '4px 10px',
                                borderRadius: '6px',
                                fontSize: '13px',
                                fontFamily: 'monospace',
                                color: '#fff',
                                minWidth: '40px',
                                textAlign: 'center',
                            }}>
                                {key}
                            </kbd>
                        </div>
                    ))}
                </div>

                {/* Footer */}
                <p style={{
                    marginTop: '20px',
                    fontSize: '12px',
                    color: '#666',
                    textAlign: 'center'
                }}>
                    Press <kbd style={{ backgroundColor: '#404040', padding: '2px 6px', borderRadius: '4px' }}>?</kbd> to toggle
                </p>
            </div>
        </div>
    );
};

export default KeyboardHelp;
