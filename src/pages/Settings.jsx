import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
    Settings as SettingsIcon,
    User,
    Volume2,
    Download,
    Palette,
    Info,
    LogOut,
    Trash2,
    ChevronRight,
    ExternalLink,
    Smartphone,
    HardDrive,
    Zap,
    Moon,
    Music
} from 'lucide-react';
import { useAuth } from '../context/AuthContext';
import { useOffline } from '../context/OfflineContext';
import { usePlaylists } from '../context/PlaylistContext';
import { useLikedSongs } from '../context/LikedSongsContext';
import { getStorageUsage, clearAllOfflineData } from '../utils/offlineDB';

const Settings = () => {
    const navigate = useNavigate();
    const { user, isAuthenticated, logout } = useAuth();
    const { playlists } = usePlaylists();
    const { likedSongs } = useLikedSongs();
    const [storageInfo, setStorageInfo] = useState({ usedMB: '0', songs: 0 });
    const [isClearing, setIsClearing] = useState(false);

    // Load storage info on mount
    React.useEffect(() => {
        loadStorageInfo();
    }, []);

    const loadStorageInfo = async () => {
        const usage = await getStorageUsage();
        setStorageInfo(usage);
    };

    const handleClearCache = async () => {
        if (window.confirm('Clear all downloaded songs? This cannot be undone.')) {
            setIsClearing(true);
            try {
                await clearAllOfflineData();
                localStorage.setItem('downloads', JSON.stringify([]));
                await loadStorageInfo();
            } catch (error) {
                console.error('Failed to clear cache:', error);
            }
            setIsClearing(false);
        }
    };

    const handleLogout = () => {
        if (window.confirm('Disconnect from Spotify?')) {
            logout();
        }
    };

    const handleExportPlaylists = () => {
        const exportData = {
            exportedAt: new Date().toISOString(),
            version: '2.1.0',
            playlists: playlists,
            likedSongs: likedSongs,
        };
        const blob = new Blob([JSON.stringify(exportData, null, 2)], { type: 'application/json' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `vikify-backup-${new Date().toISOString().slice(0, 10)}.json`;
        a.click();
        URL.revokeObjectURL(url);
    };

    return (
        <div className="settings-page" style={{ minHeight: '100%', position: 'relative' }}>
            {/* Header Gradient */}
            <div style={{
                position: 'absolute',
                top: 0,
                left: 0,
                right: 0,
                height: '280px',
                background: 'linear-gradient(180deg, #404040 0%, #121212 100%)',
                pointerEvents: 'none'
            }} />

            <div style={{ position: 'relative', padding: '24px', paddingBottom: '160px' }}>
                {/* Header */}
                <div style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '16px',
                    marginBottom: '32px',
                    paddingTop: '24px'
                }}>
                    <div className="settings-icon-box" style={{
                        width: '80px',
                        height: '80px',
                        borderRadius: '16px',
                        background: 'linear-gradient(135deg, #535353, #282828)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        boxShadow: '0 8px 32px rgba(0, 0, 0, 0.4)'
                    }}>
                        <SettingsIcon size={36} color="#fff" />
                    </div>
                    <div>
                        <h1 style={{ fontSize: '28px', fontWeight: 800, marginBottom: '4px' }}>Settings</h1>
                        <p style={{ color: '#b3b3b3', fontSize: '14px' }}>Customize your Vikify experience</p>
                    </div>
                </div>

                {/* Profile Section */}
                <SettingsSection title="Profile" icon={<User size={20} />}>
                    {isAuthenticated && user ? (
                        <div style={{
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'space-between',
                            padding: '16px',
                            background: 'rgba(255,255,255,0.05)',
                            borderRadius: '12px',
                            marginBottom: '12px'
                        }}>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                                {user.image ? (
                                    <img
                                        src={user.image}
                                        alt={user.name}
                                        style={{ width: '48px', height: '48px', borderRadius: '50%', objectFit: 'cover' }}
                                    />
                                ) : (
                                    <div style={{
                                        width: '48px',
                                        height: '48px',
                                        borderRadius: '50%',
                                        background: 'linear-gradient(135deg, #1db954, #191414)',
                                        display: 'flex',
                                        alignItems: 'center',
                                        justifyContent: 'center',
                                        fontSize: '18px',
                                        fontWeight: 700
                                    }}>
                                        {user.name?.[0]?.toUpperCase() || 'V'}
                                    </div>
                                )}
                                <div>
                                    <div style={{ fontWeight: 600, fontSize: '16px' }}>{user.name || 'User'}</div>
                                    <div style={{ color: '#1db954', fontSize: '13px', display: 'flex', alignItems: 'center', gap: '4px' }}>
                                        <span style={{ width: '6px', height: '6px', borderRadius: '50%', background: '#1db954' }}></span>
                                        Connected to Spotify
                                    </div>
                                </div>
                            </div>
                            <button
                                onClick={handleLogout}
                                style={{
                                    display: 'flex',
                                    alignItems: 'center',
                                    gap: '8px',
                                    padding: '10px 16px',
                                    background: 'rgba(255,255,255,0.1)',
                                    borderRadius: '20px',
                                    color: '#fff',
                                    fontSize: '13px',
                                    fontWeight: 600,
                                    border: 'none',
                                    cursor: 'pointer',
                                    transition: 'all 0.2s'
                                }}
                            >
                                <LogOut size={16} />
                                Disconnect
                            </button>
                        </div>
                    ) : (
                        <SettingsItem
                            icon={<ExternalLink size={18} />}
                            title="Connect Spotify"
                            subtitle="Sign in to access your playlists"
                            onClick={() => navigate('/onboarding')}
                        />
                    )}
                </SettingsSection>

                {/* Playback Section */}
                <SettingsSection title="Playback" icon={<Volume2 size={20} />}>
                    <SettingsItem
                        icon={<Zap size={18} />}
                        title="Autoplay"
                        subtitle="Play similar songs when queue ends"
                        toggle={true}
                        defaultOn={true}
                    />
                    <SettingsItem
                        icon={<Music size={18} />}
                        title="Audio Quality"
                        subtitle="High (256 kbps)"
                        value="High"
                    />
                </SettingsSection>

                {/* Downloads Section */}
                <SettingsSection title="Downloads" icon={<Download size={20} />}>
                    <div style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'space-between',
                        padding: '16px',
                        background: 'rgba(255,255,255,0.05)',
                        borderRadius: '12px',
                        marginBottom: '12px'
                    }}>
                        <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                            <div style={{
                                width: '40px',
                                height: '40px',
                                borderRadius: '10px',
                                background: 'linear-gradient(135deg, #3b82f6, #8b5cf6)',
                                display: 'flex',
                                alignItems: 'center',
                                justifyContent: 'center'
                            }}>
                                <HardDrive size={20} color="#fff" />
                            </div>
                            <div>
                                <div style={{ fontWeight: 600, fontSize: '15px' }}>Storage Used</div>
                                <div style={{ color: '#b3b3b3', fontSize: '13px' }}>
                                    {storageInfo.usedMB} MB • {storageInfo.songs} songs cached
                                </div>
                            </div>
                        </div>
                    </div>
                    <SettingsItem
                        icon={<Trash2 size={18} />}
                        title="Clear All Downloads"
                        subtitle="Remove all cached audio files"
                        onClick={handleClearCache}
                        danger={true}
                        loading={isClearing}
                    />
                </SettingsSection>

                {/* Appearance Section */}
                <SettingsSection title="Appearance" icon={<Palette size={20} />}>
                    <SettingsItem
                        icon={<Moon size={18} />}
                        title="Dark Mode"
                        subtitle="Always enabled for best experience"
                        toggle={true}
                        defaultOn={true}
                        disabled={true}
                    />
                </SettingsSection>

                {/* About Section */}
                <SettingsSection title="About" icon={<Info size={20} />}>
                    <SettingsItem
                        icon={<Smartphone size={18} />}
                        title="Version"
                        value="2.1.0"
                    />
                    <SettingsItem
                        icon={<Download size={18} />}
                        title="Export Playlists"
                        subtitle="Backup playlists and liked songs to JSON"
                        onClick={handleExportPlaylists}
                    />
                    <div style={{
                        textAlign: 'center',
                        padding: '24px',
                        color: '#6a6a6a',
                        fontSize: '13px'
                    }}>
                        <p style={{ marginBottom: '8px' }}>Made with ❤️ by TheCraftsman</p>
                        <p>© 2024 Vikify. All rights reserved.</p>
                    </div>
                </SettingsSection>
            </div>

            <style>{`
                .settings-icon-box {
                    position: relative;
                }
                .settings-icon-box::before {
                    content: '';
                    position: absolute;
                    inset: -2px;
                    background: linear-gradient(135deg, #1db954, #535353, #1db954);
                    border-radius: 18px;
                    z-index: -1;
                    opacity: 0.6;
                }
            `}</style>
        </div>
    );
};

// Section Component
const SettingsSection = ({ title, icon, children }) => (
    <div style={{ marginBottom: '32px' }}>
        <div style={{
            display: 'flex',
            alignItems: 'center',
            gap: '10px',
            marginBottom: '16px',
            color: '#fff',
            fontSize: '18px',
            fontWeight: 700
        }}>
            {icon}
            {title}
        </div>
        <div style={{
            background: 'rgba(255,255,255,0.03)',
            borderRadius: '16px',
            overflow: 'hidden',
            border: '1px solid rgba(255,255,255,0.06)'
        }}>
            {children}
        </div>
    </div>
);

// Item Component
const SettingsItem = ({ icon, title, subtitle, value, onClick, toggle, defaultOn, disabled, danger, loading }) => {
    const [isOn, setIsOn] = useState(defaultOn || false);

    return (
        <div
            onClick={!disabled && onClick ? onClick : undefined}
            style={{
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                padding: '16px',
                cursor: onClick && !disabled ? 'pointer' : 'default',
                transition: 'background 0.2s',
                borderBottom: '1px solid rgba(255,255,255,0.05)'
            }}
            onMouseEnter={(e) => onClick && !disabled && (e.currentTarget.style.background = 'rgba(255,255,255,0.05)')}
            onMouseLeave={(e) => e.currentTarget.style.background = 'transparent'}
        >
            <div style={{ display: 'flex', alignItems: 'center', gap: '14px' }}>
                <div style={{
                    color: danger ? '#f15e6c' : '#b3b3b3',
                    opacity: disabled ? 0.5 : 1
                }}>
                    {icon}
                </div>
                <div style={{ opacity: disabled ? 0.5 : 1 }}>
                    <div style={{
                        fontWeight: 500,
                        fontSize: '15px',
                        color: danger ? '#f15e6c' : '#fff'
                    }}>
                        {loading ? 'Clearing...' : title}
                    </div>
                    {subtitle && (
                        <div style={{ color: '#6a6a6a', fontSize: '13px', marginTop: '2px' }}>
                            {subtitle}
                        </div>
                    )}
                </div>
            </div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                {value && (
                    <span style={{ color: '#b3b3b3', fontSize: '14px' }}>{value}</span>
                )}
                {toggle && (
                    <div
                        onClick={(e) => {
                            e.stopPropagation();
                            if (!disabled) setIsOn(!isOn);
                        }}
                        style={{
                            width: '44px',
                            height: '24px',
                            borderRadius: '12px',
                            background: isOn ? '#1db954' : '#535353',
                            position: 'relative',
                            cursor: disabled ? 'not-allowed' : 'pointer',
                            transition: 'background 0.2s',
                            opacity: disabled ? 0.5 : 1
                        }}
                    >
                        <div style={{
                            width: '20px',
                            height: '20px',
                            borderRadius: '50%',
                            background: '#fff',
                            position: 'absolute',
                            top: '2px',
                            left: isOn ? '22px' : '2px',
                            transition: 'left 0.2s',
                            boxShadow: '0 2px 4px rgba(0,0,0,0.3)'
                        }} />
                    </div>
                )}
                {onClick && !toggle && (
                    <ChevronRight size={18} color="#6a6a6a" />
                )}
            </div>
        </div>
    );
};

export default Settings;
