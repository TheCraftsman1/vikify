from flask import Flask, jsonify, request, Response, send_file, redirect
from flask_cors import CORS
import yt_dlp
import re
import os
import tempfile
import threading
import time
import requests
import base64
import urllib.parse

app = Flask(__name__)
CORS(app)

# Spotify API credentials
SPOTIFY_CLIENT_ID = os.environ.get('SPOTIFY_CLIENT_ID', '242fffd1ca15426ab8c7396a6931b780')
SPOTIFY_CLIENT_SECRET = os.environ.get('SPOTIFY_CLIENT_SECRET', '5a479c5370ba48bc860048d89878ee4d')
SPOTIFY_REDIRECT_URI = os.environ.get('SPOTIFY_REDIRECT_URI', 'http://127.0.0.1:5000/auth/spotify/callback')
FRONTEND_URL = os.environ.get('FRONTEND_URL', 'http://localhost:5173')

# Spotify token cache (for client credentials - public API)
spotify_token = None
spotify_token_expiry = 0

def get_spotify_token():
    """Get valid Spotify access token"""
    global spotify_token, spotify_token_expiry
    
    # Return cached token if valid
    if spotify_token and time.time() < spotify_token_expiry:
        return spotify_token
    
    try:
        auth_str = f"{SPOTIFY_CLIENT_ID}:{SPOTIFY_CLIENT_SECRET}"
        auth_b64 = base64.b64encode(auth_str.encode()).decode()
        
        response = requests.post(
            'https://accounts.spotify.com/api/token',
            data={'grant_type': 'client_credentials'},
            headers={
                'Authorization': f'Basic {auth_b64}',
                'Content-Type': 'application/x-www-form-urlencoded'
            }
        )
        
        if response.status_code == 200:
            data = response.json()
            spotify_token = data['access_token']
            # Set expiry 60 seconds before actual expiry
            spotify_token_expiry = time.time() + data['expires_in'] - 60
            print(f"[Spotify] ✅ Got new access token")
            return spotify_token
        else:
            print(f"[Spotify] ❌ Token error: {response.status_code} - {response.text}")
            return None
    except Exception as e:
        print(f"[Spotify] ❌ Token exception: {e}")
        return None

# Temp directory for downloads
TEMP_DIR = os.path.join(tempfile.gettempdir(), 'vikify_cache')
os.makedirs(TEMP_DIR, exist_ok=True)

# Production uses system ffmpeg, local dev can override
FFMPEG_PATH = os.environ.get('FFMPEG_PATH', None)


def clean_query(query):
    """Clean up query for better search results"""
    query = re.sub(r'\(From "[^"]+"\)', '', query)
    query = re.sub(r'\[.*?\]', '', query)
    query = re.sub(r'-\s*(TAMIL|TELUGU|HINDI|ENGLISH)', '', query, flags=re.IGNORECASE)
    return query.strip()

from ytmusicapi import YTMusic

# Initialize YTMusic
ytmusic = YTMusic()

@app.route('/search')
def search():
    query = request.args.get('q', '')
    if not query:
        return jsonify({'error': 'No query provided', 'success': False}), 400
    
    clean_q = clean_query(query)
    print(f"[Search] Query: {clean_q}")
    
    # Method 1: Try ytmusicapi first (FAST & ACCURATE)
    try:
        search_results = ytmusic.search(clean_q, filter='songs')
        
        if search_results and len(search_results) > 0:
            top_result = search_results[0]
            video_id = top_result.get('videoId')
            title = top_result.get('title')
            # Artist list to string
            artists = top_result.get('artists', [])
            artist_name = ", ".join([a['name'] for a in artists]) if artists else "Unknown"
            
            # Additional metadata if available
            duration = top_result.get('duration_seconds') # ytmusicapi returns int seconds usually
            image = top_result.get('thumbnails', [{}])[-1].get('url') # Get largest thumbnail

            print(f"[Search] ✅ Found via YTMusic: {video_id} - {title}")
            return jsonify({
                'success': True,
                'videoId': video_id,
                'title': title,
                'artist': artist_name,
                'image': image,
                'youtubeUrl': f'https://www.youtube.com/watch?v={video_id}'
            })
            
    except Exception as e:
        print(f"[Search] ytmusicapi error: {e}")
        # Fallback to yt-dlp if ytmusicapi fails logic continues below...

    # Method 2: Fallback to yt-dlp (SLOWER but robust backup)
    try:
        ydl_opts = {
            'quiet': True,
            'no_warnings': True,
            'extract_flat': True,
            'default_search': 'ytsearch1',
        }
        
        print(f"[Search] Falling back to yt-dlp for: {clean_q}")
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            result = ydl.extract_info(f"ytsearch1:{clean_q}", download=False)
            
            if result and result.get('entries'):
                video = result['entries'][0]
                video_id = video.get('id')
                title = video.get('title', '')
                
                print(f"[Search] ✅ Found via yt-dlp: {video_id} - {title}")
                return jsonify({
                    'success': True,
                    'videoId': video_id,
                    'title': title,
                    'youtubeUrl': f'https://www.youtube.com/watch?v={video_id}'
                })
                
    except Exception as e:
        print(f"[Search] yt-dlp error: {e}")
    
    return jsonify({'success': False, 'error': 'No results'})

# ============== ITUNES SEARCH PROXY ==============

@app.route('/itunes/search')
def itunes_search():
    """Proxy iTunes search API to bypass CORS/redirect issues on mobile"""
    query = request.args.get('q', '')
    limit = request.args.get('limit', '30')
    
    if not query:
        return jsonify({'success': False, 'error': 'No query provided'}), 400
    
    try:
        response = requests.get(
            'https://itunes.apple.com/search',
            params={
                'term': query,
                'media': 'music',
                'limit': limit
            },
            timeout=10
        )
        
        if response.status_code == 200:
            data = response.json()
            # Format results for frontend
            results = []
            for item in data.get('results', []):
                results.append({
                    'id': item.get('trackId'),
                    'title': item.get('trackName'),
                    'artist': item.get('artistName'),
                    'album': item.get('collectionName'),
                    'image': item.get('artworkUrl100', '').replace('100x100', '300x300'),
                    'previewUrl': item.get('previewUrl'),
                    'duration': item.get('trackTimeMillis')
                })
            
            print(f"[iTunes] ✅ Found {len(results)} results for '{query}'")
            return jsonify({'success': True, 'results': results})
        else:
            print(f"[iTunes] API error: {response.status_code}")
            return jsonify({'success': False, 'error': 'iTunes API error'}), 500
            
    except Exception as e:
        print(f"[iTunes] Exception: {e}")
        return jsonify({'success': False, 'error': str(e)}), 500

# ============== SPOTIFY USER OAUTH ==============

@app.route('/auth/spotify')
def spotify_auth():
    """Redirect user to Spotify login"""
    scopes = 'user-read-private user-read-email playlist-read-private playlist-read-collaborative user-library-read'
    params = {
        'client_id': SPOTIFY_CLIENT_ID,
        'response_type': 'code',
        'redirect_uri': SPOTIFY_REDIRECT_URI,
        'scope': scopes,
        'show_dialog': 'true'
    }
    auth_url = f"https://accounts.spotify.com/authorize?{urllib.parse.urlencode(params)}"
    print(f"[Spotify Auth] Redirecting to Spotify login")
    return redirect(auth_url)

@app.route('/auth/spotify/callback')
def spotify_callback():
    """Handle OAuth callback from Spotify"""
    code = request.args.get('code')
    error = request.args.get('error')
    
    if error:
        return redirect(f"{FRONTEND_URL}/?auth_error={error}")
    
    if not code:
        return redirect(f"{FRONTEND_URL}/?auth_error=no_code")
    
    try:
        auth_str = f"{SPOTIFY_CLIENT_ID}:{SPOTIFY_CLIENT_SECRET}"
        auth_b64 = base64.b64encode(auth_str.encode()).decode()
        
        response = requests.post(
            'https://accounts.spotify.com/api/token',
            data={
                'grant_type': 'authorization_code',
                'code': code,
                'redirect_uri': SPOTIFY_REDIRECT_URI
            },
            headers={
                'Authorization': f'Basic {auth_b64}',
                'Content-Type': 'application/x-www-form-urlencoded'
            }
        )
        
        if response.status_code == 200:
            tokens = response.json()
            params = urllib.parse.urlencode({
                'access_token': tokens['access_token'],
                'refresh_token': tokens.get('refresh_token', ''),
                'expires_in': tokens.get('expires_in', 3600)
            })
            print(f"[Spotify Auth] ✅ Got user tokens")
            return redirect(f"{FRONTEND_URL}/?{params}")
        else:
            print(f"[Spotify Auth] ❌ Token exchange failed: {response.status_code}")
            return redirect(f"{FRONTEND_URL}/?auth_error=token_failed")
            
    except Exception as e:
        print(f"[Spotify Auth] ❌ Exception: {e}")
        return redirect(f"{FRONTEND_URL}/?auth_error=exception")

@app.route('/spotify/me')
def spotify_user_profile():
    """Get authenticated user's profile"""
    auth_header = request.headers.get('Authorization')
    if not auth_header:
        return jsonify({'success': False, 'error': 'No authorization'}), 401
    
    try:
        response = requests.get('https://api.spotify.com/v1/me', headers={'Authorization': auth_header})
        if response.status_code == 200:
            data = response.json()
            return jsonify({
                'success': True,
                'user': {
                    'id': data.get('id'),
                    'name': data.get('display_name'),
                    'email': data.get('email'),
                    'image': data.get('images', [{}])[0].get('url') if data.get('images') else None
                }
            })
        return jsonify({'success': False, 'error': 'Failed'}), response.status_code
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/spotify/me/playlists')
def spotify_user_playlists():
    """Get user's playlists"""
    auth_header = request.headers.get('Authorization')
    if not auth_header:
        return jsonify({'success': False, 'error': 'No authorization'}), 401
    
    try:
        response = requests.get('https://api.spotify.com/v1/me/playlists?limit=50', headers={'Authorization': auth_header})
        if response.status_code == 200:
            data = response.json()
            playlists = [{
                'id': p.get('id'),
                'title': p.get('name'),
                'image': p.get('images', [{}])[0].get('url') if p.get('images') else None,
                'tracksCount': p.get('tracks', {}).get('total', 0)
            } for p in data.get('items', [])]
            return jsonify({'success': True, 'playlists': playlists})
        return jsonify({'success': False, 'error': 'Failed'}), response.status_code
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/spotify/me/tracks')
def spotify_user_tracks():
    """Get user's liked songs"""
    auth_header = request.headers.get('Authorization')
    if not auth_header:
        return jsonify({'success': False, 'error': 'No authorization'}), 401
    
    try:
        response = requests.get('https://api.spotify.com/v1/me/tracks?limit=50', headers={'Authorization': auth_header})
        if response.status_code == 200:
            data = response.json()
            tracks = [{
                'id': t.get('track', {}).get('id'),
                'title': t.get('track', {}).get('name'),
                'artist': ', '.join([a.get('name', '') for a in t.get('track', {}).get('artists', [])]),
                'image': t.get('track', {}).get('album', {}).get('images', [{}])[0].get('url') if t.get('track', {}).get('album', {}).get('images') else None,
                'duration': t.get('track', {}).get('duration_ms')
            } for t in data.get('items', [])]
            return jsonify({'success': True, 'tracks': tracks, 'total': data.get('total', 0)})
        return jsonify({'success': False, 'error': 'Failed'}), response.status_code
    except Exception as e:
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/stream/<video_id>')
def get_stream(video_id):
    try:
        ydl_opts = {
            'quiet': True,
            'no_warnings': True,
            # Force highest quality audio - prefer 256kbps+ m4a/opus for browser compatibility
            'format': 'bestaudio[abr>=256]/bestaudio[abr>=192]/bestaudio[abr>=128]/bestaudio/best',
            'prefer_free_formats': False,  # Don't limit to free formats
        }
        
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            url = f'https://www.youtube.com/watch?v={video_id}'
            result = ydl.extract_info(url, download=False)
            
            if result:
                audio_url = result.get('url')
                audio_bitrate = result.get('abr', 0)
                audio_codec = result.get('acodec', 'unknown')
                
                # If direct URL not available, find best audio format manually
                if not audio_url and result.get('formats'):
                    audio_formats = [f for f in result['formats'] 
                                    if f.get('acodec') != 'none' and f.get('vcodec') == 'none']
                    if audio_formats:
                        # Sort by bitrate (highest first), prefer opus/aac codecs
                        audio_formats.sort(
                            key=lambda x: (
                                x.get('abr', 0) or 0,
                                1 if x.get('acodec') in ['opus', 'aac'] else 0
                            ), 
                            reverse=True
                        )
                        best = audio_formats[0]
                        audio_url = best.get('url')
                        audio_bitrate = best.get('abr', 0)
                        audio_codec = best.get('acodec', 'unknown')
                
                if audio_url:
                    quality = 'high' if audio_bitrate and audio_bitrate >= 128 else 'standard'
                    print(f"[Stream] ✅ Got {quality} quality audio: {audio_bitrate}kbps {audio_codec}")
                    return jsonify({
                        'success': True,
                        'audioUrl': audio_url,
                        'duration': result.get('duration'),
                        'quality': {
                            'bitrate': audio_bitrate,
                            'codec': audio_codec,
                            'level': quality
                        }
                    })
                    
    except Exception as e:
        print(f"[Stream] Error: {e}")
    
    return jsonify({'success': False, 'error': 'No audio streams'})

@app.route('/download/<video_id>')
def download_audio(video_id):
    """Download audio using yt-dlp and serve the file"""
    try:
        output_path = os.path.join(TEMP_DIR, f'{video_id}.mp3')
        
        # Check if already cached
        if os.path.exists(output_path):
            print(f"[Download] ✅ Serving cached: {video_id}")
            return send_file(
                output_path,
                mimetype='audio/mpeg',
                as_attachment=True,
                download_name=f'{video_id}.mp3'
            )
        
        print(f"[Download] Starting download for: {video_id}")
        
        ydl_opts = {
            'quiet': True,
            'no_warnings': True,
            'format': 'bestaudio/best',
            'outtmpl': os.path.join(TEMP_DIR, f'{video_id}.%(ext)s'),
            'postprocessors': [{
                'key': 'FFmpegExtractAudio',
                'preferredcodec': 'mp3',
                'preferredquality': '320',  # High quality audio
            }],
        }
        
        # Only set ffmpeg_location if explicitly provided
        if FFMPEG_PATH:
            ydl_opts['ffmpeg_location'] = FFMPEG_PATH
        
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            url = f'https://www.youtube.com/watch?v={video_id}'
            ydl.download([url])
        
        if os.path.exists(output_path):
            print(f"[Download] ✅ Download complete: {video_id}")
            return send_file(
                output_path,
                mimetype='audio/mpeg',
                as_attachment=True,
                download_name=f'{video_id}.mp3'
            )
        else:
            print(f"[Download] ❌ File not created")
            return jsonify({'success': False, 'error': 'Download failed'}), 500
                    
    except Exception as e:
        print(f"[Download] Error: {e}")
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/related/<video_id>')
def get_related(video_id):
    """Get related songs for autoplay"""
    try:
        ydl_opts = {
            'quiet': True,
            'no_warnings': True,
            'extract_flat': False,
        }
        
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            url = f'https://www.youtube.com/watch?v={video_id}'
            result = ydl.extract_info(url, download=False)
            
            if result:
                title = result.get('title', '')
                artist = result.get('uploader', '')
                
                search_query = f"{title.split('|')[0].strip()} {artist} similar songs"
                search_result = ydl.extract_info(f"ytsearch5:{search_query}", download=False)
                
                related = []
                if search_result and search_result.get('entries'):
                    for entry in search_result['entries'][:5]:
                        if entry and entry.get('id') != video_id:
                            related.append({
                                'videoId': entry.get('id'),
                                'title': entry.get('title', ''),
                                'artist': entry.get('uploader', ''),
                                'duration': entry.get('duration', 0),
                                'image': entry.get('thumbnail', '')
                            })
                
                print(f"[Related] Found {len(related)} related songs")
                return jsonify({
                    'success': True,
                    'related': related
                })
                    
    except Exception as e:
        print(f"[Related] Error: {e}")
    
    return jsonify({'success': False, 'related': []})

# ============== SPOTIFY PROXY ENDPOINTS ==============

@app.route('/spotify/playlist/<playlist_id>')
def get_spotify_playlist(playlist_id):
    """Fetch Spotify playlist data (bypasses CORS)"""
    token = get_spotify_token()
    if not token:
        return jsonify({'success': False, 'error': 'Failed to authenticate with Spotify'}), 500
    
    try:
        response = requests.get(
            f'https://api.spotify.com/v1/playlists/{playlist_id}',
            headers={'Authorization': f'Bearer {token}'}
        )
        
        if response.status_code != 200:
            print(f"[Spotify] Playlist error: {response.status_code}")
            return jsonify({'success': False, 'error': f'Spotify API error: {response.status_code}'}), response.status_code
        
        data = response.json()
        
        # Map to Vikify format
        playlist = {
            'id': data['id'],
            'title': data['name'],
            'description': data.get('description') or f"By {data['owner']['display_name']}",
            'image': data['images'][0]['url'] if data.get('images') else None,
            'type': 'playlist',
            'songs': []
        }
        
        # Map tracks
        for item in data.get('tracks', {}).get('items', []):
            track = item.get('track')
            if track:
                playlist['songs'].append({
                    'id': track['id'],
                    'title': track['name'],
                    'artist': ', '.join([a['name'] for a in track['artists']]),
                    'image': track['album']['images'][0]['url'] if track.get('album', {}).get('images') else None,
                    'duration': track['duration_ms'] / 1000,
                    'album': track.get('album', {}).get('name', ''),
                    'isSpotify': True
                })
        
        print(f"[Spotify] ✅ Playlist '{playlist['title']}' with {len(playlist['songs'])} tracks")
        return jsonify({'success': True, 'data': playlist})
        
    except Exception as e:
        print(f"[Spotify] Playlist exception: {e}")
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/spotify/album/<album_id>')
def get_spotify_album(album_id):
    """Fetch Spotify album data (bypasses CORS)"""
    token = get_spotify_token()
    if not token:
        return jsonify({'success': False, 'error': 'Failed to authenticate with Spotify'}), 500
    
    try:
        response = requests.get(
            f'https://api.spotify.com/v1/albums/{album_id}',
            headers={'Authorization': f'Bearer {token}'}
        )
        
        if response.status_code != 200:
            print(f"[Spotify] Album error: {response.status_code}")
            return jsonify({'success': False, 'error': f'Spotify API error: {response.status_code}'}), response.status_code
        
        data = response.json()
        
        # Map to Vikify format
        album = {
            'id': data['id'],
            'title': data['name'],
            'description': ', '.join([a['name'] for a in data['artists']]),
            'image': data['images'][0]['url'] if data.get('images') else None,
            'type': 'album',
            'songs': []
        }
        
        # Map tracks
        for track in data.get('tracks', {}).get('items', []):
            album['songs'].append({
                'id': track['id'],
                'title': track['name'],
                'artist': ', '.join([a['name'] for a in track['artists']]),
                'image': album['image'],  # Albums share the same image
                'duration': track['duration_ms'] / 1000,
                'album': album['title'],
                'isSpotify': True
            })
        
        print(f"[Spotify] ✅ Album '{album['title']}' with {len(album['songs'])} tracks")
        return jsonify({'success': True, 'data': album})
        
    except Exception as e:
        print(f"[Spotify] Album exception: {e}")
        return jsonify({'success': False, 'error': str(e)}), 500

@app.route('/health')
def health():
    return jsonify({'status': 'ok'})

if __name__ == '__main__':
    print("🎵 Vikify Backend Server (yt-dlp)")
    print("   /search?q=song+name  - Search YouTube")
    print("   /stream/<video_id>   - Get audio stream URL")
    print("   /download/<video_id> - Download audio as MP3")
    print(f"   Cache dir: {TEMP_DIR}")
    print("")
    port = int(os.environ.get('PORT', 5000))
    app.run(host='0.0.0.0', port=port, debug=os.environ.get('DEBUG', 'false').lower() == 'true', threaded=True)
