import React from 'react';
import { Home, Search, Library, Plus, ArrowRight, Heart, Download, Music, Settings } from 'lucide-react';
import { Link, useLocation } from 'react-router-dom';
import { albums } from '../data/songs';

const Sidebar = () => {
  const location = useLocation();

  const NavItem = ({ to, icon: Icon, label }) => {
    const isActive = location.pathname === to;
    return (
      <Link
        to={to}
        className="nav-item"
        style={{
          display: 'flex',
          alignItems: 'center',
          gap: '16px',
          padding: '8px 12px',
          borderRadius: '4px',
          color: isActive ? '#fff' : '#b3b3b3',
          fontWeight: isActive ? 700 : 600,
          transition: 'all 0.2s ease',
          textDecoration: 'none'
        }}
        onMouseEnter={(e) => !isActive && (e.currentTarget.style.color = '#fff')}
        onMouseLeave={(e) => !isActive && (e.currentTarget.style.color = '#b3b3b3')}
      >
        <Icon size={24} strokeWidth={isActive ? 2.5 : 2} />
        <span style={{ fontSize: '15px' }}>{label}</span>
      </Link>
    );
  };

  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column', gap: '8px' }}>
      {/* Top Navigation Panel */}
      <div style={{
        backgroundColor: '#121212',
        borderRadius: '8px',
        padding: '12px 12px 8px 12px'
      }}>
        {/* Logo */}
        <Link to="/" style={{
          display: 'flex',
          alignItems: 'center',
          gap: '8px',
          padding: '8px 12px',
          marginBottom: '12px',
          textDecoration: 'none'
        }}>
          <div style={{
            width: '32px',
            height: '32px',
            borderRadius: '50%',
            background: 'linear-gradient(135deg, #1db954, #1ed760)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            boxShadow: '0 4px 12px rgba(29, 185, 84, 0.4)'
          }}>
            <span style={{ fontWeight: 900, fontSize: '18px', color: '#000' }}>V</span>
          </div>
          <span style={{ fontWeight: 700, fontSize: '22px', color: '#fff', letterSpacing: '-0.5px' }}>Vikify</span>
        </Link>

        {/* Navigation Links */}
        <nav style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
          <NavItem to="/" icon={Home} label="Home" />
          <NavItem to="/search" icon={Search} label="Search" />
          <NavItem to="/settings" icon={Settings} label="Settings" />
        </nav>
      </div>

      {/* Library Panel */}
      <div style={{
        backgroundColor: '#121212',
        borderRadius: '8px',
        flex: 1,
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
        minHeight: 0
      }}>
        {/* Library Header */}
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          padding: '12px 16px 8px 20px'
        }}>
          <Link
            to="/library"
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '12px',
              color: '#b3b3b3',
              textDecoration: 'none',
              transition: 'color 0.2s'
            }}
            onMouseEnter={(e) => e.currentTarget.style.color = '#fff'}
            onMouseLeave={(e) => e.currentTarget.style.color = '#b3b3b3'}
          >
            <Library size={24} />
            <span style={{ fontWeight: 700, fontSize: '15px' }}>Your Library</span>
          </Link>
          <div style={{ display: 'flex', gap: '8px' }}>
            <button
              style={{
                padding: '8px',
                borderRadius: '50%',
                color: '#b3b3b3',
                transition: 'all 0.2s'
              }}
              onMouseEnter={(e) => { e.currentTarget.style.color = '#fff'; e.currentTarget.style.backgroundColor = 'rgba(255,255,255,0.1)'; }}
              onMouseLeave={(e) => { e.currentTarget.style.color = '#b3b3b3'; e.currentTarget.style.backgroundColor = 'transparent'; }}
              title="Create playlist"
            >
              <Plus size={20} />
            </button>
          </div>
        </div>

        {/* Filter Chips */}
        <div style={{ padding: '8px 12px', display: 'flex', gap: '8px' }}>
          <span style={{
            padding: '6px 12px',
            backgroundColor: '#fff',
            color: '#000',
            borderRadius: '16px',
            fontSize: '13px',
            fontWeight: 500,
            cursor: 'pointer'
          }}>Playlists</span>
          <span style={{
            padding: '6px 12px',
            backgroundColor: 'rgba(255,255,255,0.07)',
            color: '#fff',
            borderRadius: '16px',
            fontSize: '13px',
            fontWeight: 500,
            cursor: 'pointer',
            transition: 'background 0.2s'
          }}
            onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'rgba(255,255,255,0.1)'}
            onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'rgba(255,255,255,0.07)'}
          >Artists</span>
        </div>

        {/* Scrollable Library List */}
        <div style={{
          flex: 1,
          overflowY: 'auto',
          padding: '4px 8px 8px 8px'
        }}>
          {/* Liked Songs */}
          <Link
            to="/playlist/liked"
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '12px',
              padding: '8px',
              borderRadius: '6px',
              textDecoration: 'none',
              transition: 'background 0.2s'
            }}
            onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'rgba(255,255,255,0.1)'}
            onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
          >
            <div style={{
              width: '48px',
              height: '48px',
              borderRadius: '4px',
              background: 'linear-gradient(135deg, #450af5, #c4efd9)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              flexShrink: 0
            }}>
              <Heart size={20} fill="white" color="white" />
            </div>
            <div style={{ minWidth: 0 }}>
              <div style={{ color: '#fff', fontWeight: 500, fontSize: '15px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>Liked Songs</div>
              <div style={{ color: '#b3b3b3', fontSize: '13px', display: 'flex', alignItems: 'center', gap: '4px' }}>
                <span style={{ color: '#1db954' }}>📌</span>
                <span>Playlist • Auto</span>
              </div>
            </div>
          </Link>

          {/* Downloads */}
          <Link
            to="/downloads"
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '12px',
              padding: '8px',
              borderRadius: '6px',
              textDecoration: 'none',
              transition: 'background 0.2s'
            }}
            onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'rgba(255,255,255,0.1)'}
            onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
          >
            <div style={{
              width: '48px',
              height: '48px',
              borderRadius: '4px',
              background: 'linear-gradient(135deg, #3b82f6, #8b5cf6)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              flexShrink: 0
            }}>
              <Download size={20} color="white" />
            </div>
            <div style={{ minWidth: 0 }}>
              <div style={{ color: '#fff', fontWeight: 500, fontSize: '15px' }}>Downloads</div>
              <div style={{ color: '#b3b3b3', fontSize: '13px' }}>Playlist • Offline</div>
            </div>
          </Link>

          {/* User Playlists from data */}
          {albums.map((album) => (
            <Link
              key={album.id}
              to={`/playlist/${album.id}`}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '12px',
                padding: '8px',
                borderRadius: '6px',
                textDecoration: 'none',
                transition: 'background 0.2s'
              }}
              onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'rgba(255,255,255,0.1)'}
              onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'transparent'}
            >
              <div style={{
                width: '48px',
                height: '48px',
                borderRadius: '4px',
                overflow: 'hidden',
                flexShrink: 0,
                backgroundColor: '#282828'
              }}>
                <img
                  src={album.image}
                  alt={album.title}
                  style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                  onError={(e) => {
                    e.target.style.display = 'none';
                  }}
                />
              </div>
              <div style={{ minWidth: 0 }}>
                <div style={{ color: '#fff', fontWeight: 500, fontSize: '15px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                  {album.title}
                </div>
                <div style={{ color: '#b3b3b3', fontSize: '13px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                  Playlist • {album.artist}
                </div>
              </div>
            </Link>
          ))}
        </div>
      </div>
    </div>
  );
};

export default Sidebar;
