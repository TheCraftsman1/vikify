
import React from 'react';
import { useAuth } from '../context/AuthContext';
import { useUI } from '../context/UIContext';
import { Plus, Bell, Clock, Settings } from 'lucide-react';

const ProfileDrawer = () => {
    const { user, isAuthenticated } = useAuth();
    const { isProfileMenuOpen, closeProfileMenu } = useUI();

    // Derived user data
    const userInitials = isAuthenticated && Object.keys(user || {}).length > 0 && user.name ? user.name[0].toUpperCase() : 'V';
    const userImage = isAuthenticated && user?.image ? user.image : null;
    const userName = isAuthenticated && user?.name ? user.name : 'Viky User';

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
                        backgroundColor: 'rgba(0,0,0,0.5)',
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
                width: '85%', // Mobile width
                maxWidth: '320px',
                height: '100%',
                backgroundColor: '#191919',
                zIndex: 50,
                transform: isProfileMenuOpen ? 'translateX(0)' : 'translateX(-100%)',
                transition: 'transform 0.3s cubic-bezier(0.1, 0.9, 0.2, 1)',
                boxShadow: '4px 0 24px rgba(0,0,0,0.5)',
                display: 'flex',
                flexDirection: 'column',
                padding: '24px'
            }}>
                {/* Header Profile */}
                <div style={{ display: 'flex', alignItems: 'center', gap: '16px', marginBottom: '32px' }}>
                    <div style={{
                        width: '48px',
                        height: '48px',
                        borderRadius: '50%',
                        background: userImage ? `url(${userImage}) no-repeat center/cover` : 'linear-gradient(135deg, #ff6b35, #f7c59f)',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        fontSize: '20px',
                        fontWeight: 700,
                        color: userImage ? 'transparent' : '#000'
                    }}>
                        {!userImage && userInitials}
                    </div>
                    <div>
                        <h2 style={{ fontSize: '18px', fontWeight: 700, margin: 0, color: '#fff' }}>{userName}</h2>
                        <span style={{ fontSize: '12px', color: '#b3b3b3' }}>View profile</span>
                    </div>
                </div>

                {/* Menu Items */}
                <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
                    <div
                        onClick={() => {
                            if (confirm("Switch account? This will log you out.")) {
                                window.localStorage.removeItem('spotify_access_token');
                                window.localStorage.removeItem('vikify_onboarded');
                                window.location.reload();
                            }
                        }}
                        style={{ display: 'flex', alignItems: 'center', gap: '16px', color: '#fff', fontSize: '16px', fontWeight: 500, cursor: 'pointer' }}
                    >
                        <Plus size={24} color="#b3b3b3" />
                        Add account
                    </div>

                    <div style={{ display: 'flex', alignItems: 'center', gap: '16px', color: '#fff', fontSize: '16px', fontWeight: 500 }}>
                        <Bell size={24} color="#b3b3b3" />
                        What's new
                    </div>

                    <div style={{ display: 'flex', alignItems: 'center', gap: '16px', color: '#fff', fontSize: '16px', fontWeight: 500 }}>
                        <Clock size={24} color="#b3b3b3" />
                        Recents
                    </div>

                    <div style={{ display: 'flex', alignItems: 'center', gap: '16px', color: '#fff', fontSize: '16px', fontWeight: 500 }}>
                        <Settings size={24} color="#b3b3b3" />
                        Settings and privacy
                    </div>
                </div>
            </div>

            <style>{`
                @keyframes fadeIn {
                    from { opacity: 0; }
                    to { opacity: 1; }
                }
            `}</style>
        </>
    );
};

export default ProfileDrawer;
