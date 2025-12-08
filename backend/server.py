from flask import Flask, jsonify, request, Response, send_file
from flask_cors import CORS
import yt_dlp
import re
import os
import tempfile
import threading

app = Flask(__name__)
CORS(app)

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

@app.route('/search')
def search():
    query = request.args.get('q', '')
    if not query:
        return jsonify({'error': 'No query provided', 'success': False}), 400
    
    clean_q = clean_query(query)
    print(f"[Search] Query: {clean_q}")
    
    try:
        ydl_opts = {
            'quiet': True,
            'no_warnings': True,
            'extract_flat': True,
            'default_search': 'ytsearch1',
        }
        
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            result = ydl.extract_info(f"ytsearch1:{clean_q}", download=False)
            
            if result and result.get('entries'):
                video = result['entries'][0]
                video_id = video.get('id')
                title = video.get('title', '')
                
                print(f"[Search] ✅ Found: {video_id} - {title}")
                return jsonify({
                    'success': True,
                    'videoId': video_id,
                    'title': title,
                    'youtubeUrl': f'https://www.youtube.com/watch?v={video_id}'
                })
                
    except Exception as e:
        print(f"[Search] yt-dlp error: {e}")
    
    return jsonify({'success': False, 'error': 'No results'})

@app.route('/stream/<video_id>')
def get_stream(video_id):
    try:
        ydl_opts = {
            'quiet': True,
            'no_warnings': True,
            'format': 'bestaudio/best',
        }
        
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            url = f'https://www.youtube.com/watch?v={video_id}'
            result = ydl.extract_info(url, download=False)
            
            if result:
                audio_url = result.get('url')
                
                if not audio_url and result.get('formats'):
                    audio_formats = [f for f in result['formats'] if f.get('acodec') != 'none' and f.get('vcodec') == 'none']
                    if audio_formats:
                        audio_formats.sort(key=lambda x: x.get('abr', 0) or 0, reverse=True)
                        audio_url = audio_formats[0].get('url')
                
                if audio_url:
                    print(f"[Stream] ✅ Got audio stream")
                    return jsonify({
                        'success': True,
                        'audioUrl': audio_url,
                        'duration': result.get('duration')
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
