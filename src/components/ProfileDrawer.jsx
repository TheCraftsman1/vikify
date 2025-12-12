
import React, { useState } from 'react';
import { useAuth } from '../context/AuthContext';
import { useUI } from '../context/UIContext';
import { Plus, Bell, Clock, Settings, X, LogOut } from 'lucide-react';

const ProfileDrawer = () => {
    const { user, isAuthenticated, logout } = useAuth();
    const { isProfileMenuOpen, closeProfileMenu } = useUI();
    const [showSwitchConfirm, setShowSwitchConfirm] = useState(false);

    // Derived user data
    const userInitials = isAuthenticated && Object.keys(user || {}).length > 0 && user.name ? user.name[0].toUpperCase() : 'V';
    const userImage = isAuthenticated && user?.image ? user.image : null;
    const userName = isAuthenticated && user?.name ? user.name : 'Viky User';
    const userEmail = isAuthenticated && user?.email ? user.email : 'Guest User';

    const handleSwitchAccount = () => {
        setShowSwitchConfirm(true);
    };

    const confirmSwitchAccount = () => {
        localStorage.removeItem('spotify_access_token');
        localStorage.removeItem('spotify_refresh_token');
        localStorage.removeItem('vikify_onboarded');
        logout();
        closeProfileMenu();
        window.location.reload();
    };

    return (
        <>
            {/* Backdrop */}
            {isProfileMenuOpen && (
                <div
                    onClick={closeProfileMenu}
                    style={{
                        position: 'fixed',
                        top: 0,
                        left: 0,
                        right: 0,
                        bottom: 0,
                        backgroundColor: 'rgba(0,0,0,0.7)',
                        zIndex: 49,
                        animation: 'fadeIn 0.2s ease'
                    }}
                />
            )}

            {/* Drawer */}
            <div style={{
                position: 'fixed',
                top: 0,
                left: 0,
                width: '85%',
                maxWidth: '320px',
                height: '100%',
                backgroundColor: '#0a0a0a',
                zIndex: 50,
                transform: isProfileMenuOpen ? 'translateX(0)' : 'translateX(-100%)',
                transition: 'transform 0.3s cubic-bezier(0.1, 0.9, 0.2, 1)',
                boxShadow: '4px 0 32px rgba(0,0,0,0.8)',
                display: 'flex',
                flexDirection: 'column',
                overflowY: 'auto'
            }}>
                {/* Close Button */}
                <button
                    onClick={closeProfileMenu}
                    style={{
                        position: 'absolute',
                        top: '16px',
                        right: '16px',
                        padding: '8px',
                        color: '#b3b3b3',
                        zIndex: 51
                    }}
                >
                    <X size={24} />
                </button>

                {/* Header Profile */}
                <div style={{ padding: '32px 24px 24px', borderBottom: '1px solid #282828' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                        <div style={{
                            width: '64px',
                            height: '64px',
                            borderRadius: '50%',
                            background: userImage ? `url(${userImage}) no-repeat center/cover` : 'linear-gradient(135deg, #1db954, #1ed760)',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            fontSize: '28px',
                            fontWeight: 700,
                            color: userImage ? 'transparent' : '#000',
                            boxShadow: '0 4px 16px rgba(0,0,0,0.4)'
                        }}>
                            {!userImage && userInitials}
                        </div>
                        <div style={{ flex: 1 }}>
                            <h2 style={{ fontSize: '20px', fontWeight: 700, margin: 0, color: '#fff' }}>{userName}</h2>
                            <span style={{ fontSize: '13px', color: '#b3b3b3' }}>{userEmail}</span>
                        </div>
                    </div>
                </div>

                {/* Menu Items */}
                <div style={{ padding: '16px 0', flex: 1 }}>
                    <MenuItem
                        icon={<Plus size={22} />}
                        label="Add account"
                        onClick={handleSwitchAccount}
                    />
                    <MenuItem
                        icon={<Bell size={22} />}
                        label="What's new"
                        onClick={() => { }}
                    />
                    <MenuItem
                        icon={<Clock size={22} />}
                        label="Listening history"
                        onClick={() => { }}
                    />
                    <MenuItem
                        icon={<Settings size={22} />}
                        label="Settings and privacy"
                        onClick={() => { }}
                    />
                </div>

                {/* Logout Button */}
                {isAuthenticated && (
                    <div style={{ padding: '16px 24px', borderTop: '1px solid #282828' }}>
                        <button
                            onClick={() => {
                                logout();
                                closeProfileMenu();
                            }}
                            style={{
                                display: 'flex',
                                alignItems: 'center',
                                gap: '12px',
                                width: '100%',
                                padding: '12px 16px',
                                color: '#f15e6c',
                                fontSize: '15px',
                                fontWeight: 500,
                                borderRadius: '8px',
                                transition: 'background 0.2s'
                            }}
                        >
                            <LogOut size={20} />
                            Log out
                        </button>
                    </div>
                )}
            </div>

            {/* Switch Account Confirmation Modal */}
            {showSwitchConfirm && (
                <>
                    <div
                        onClick={() => setShowSwitchConfirm(false)}
                        style={{
                            position: 'fixed',
                            inset: 0,
                            backgroundColor: 'rgba(0,0,0,0.85)',
                            zIndex: 100,
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            padding: '24px'
                        }}
                    >
                        <div
                            onClick={(e) => e.stopPropagation()}
                            style={{
                                background: '#282828',
                                borderRadius: '12px',
                                padding: '24px',
                                maxWidth: '340px',
                                width: '100%',
                                boxShadow: '0 16px 48px rgba(0,0,0,0.5)'
                            }}
                        >
                            <h3 style={{ fontSize: '20px', fontWeight: 700, marginBottom: '12px', color: '#fff' }}>
                                Switch account?
                            </h3>
                            <p style={{ color: '#b3b3b3', fontSize: '14px', lineHeight: 1.5, marginBottom: '24px' }}>
                                This will log you out of your current Spotify account. You can then connect a different account.
                            </p>
                            <div style={{ display: 'flex', gap: '12px' }}>
                                <button
                                    onClick={() => setShowSwitchConfirm(false)}
                                    style={{
                                        flex: 1,
                                        padding: '12px',
                                        borderRadius: '24px',
                                        border: '1px solid #404040',
                                        color: '#fff',
                                        fontWeight: 600,
                                        fontSize: '14px'
                                    }}
                                >
                                    Cancel
                                </button>
                                <button
                                    onClick={confirmSwitchAccount}
                                    style={{
                                        flex: 1,
                                        padding: '12px',
                                        borderRadius: '24px',
                                        background: '#1db954',
                                        color: '#000',
                                        fontWeight: 600,
                                        fontSize: '14px'
                                    }}
                                >
                                    Switch
                                </button>
                            </div>
                        </div>
                    </div>
                </>
            )}

            <style>{`
                @keyframes fadeIn {
                    from { opacity: 0; }
                    to { opacity: 1; }
                }
            `}</style>
        </>
    );
};

const MenuItem = ({ icon, label, onClick }) => (
    <button
        onClick={onClick}
        style={{
            display: 'flex',
            alignItems: 'center',
            gap: '16px',
            width: '100%',
            padding: '14px 24px',
            color: '#fff',
            fontSize: '15px',
            fontWeight: 500,
            transition: 'background 0.2s',
            textAlign: 'left'
        }}
        onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'rgba(255,255,255,0.1)'}
        onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
    >
        <span style={{ color: '#b3b3b3' }}>{icon}</span>
        {label}
    </button>
);

export default ProfileDrawer;
