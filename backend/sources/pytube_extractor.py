"""
PyTube Extractor for Vikify
Fast YouTube audio extraction using pytubefix (1-2s vs 5-10s for yt-dlp)
"""
import asyncio
from typing import Optional
from concurrent.futures import ThreadPoolExecutor

try:
    from pytubefix import YouTube
    PYTUBE_AVAILABLE = True
except ImportError:
    PYTUBE_AVAILABLE = False
    print("[Pytube] ⚠️ pytubefix not installed, pytube extraction disabled")

# Thread pool for running synchronous pytube code
_executor = ThreadPoolExecutor(max_workers=3)

class PytubeExtractor:
    """Fast YouTube audio stream extractor using pytubefix"""
    
    def __init__(self):
        self.stats = {'success': 0, 'failed': 0}
    
    async def extract_audio(self, video_id: str) -> Optional[str]:
        """
        Extract audio stream URL from YouTube video
        
        Args:
            video_id: YouTube video ID (11 characters)
            
        Returns:
            Audio stream URL or None if failed
        """
        if not PYTUBE_AVAILABLE:
            return None
        
        if not video_id or len(video_id) != 11:
            print(f"[Pytube] Invalid video ID: {video_id}")
            return None
        
        try:
            # Run synchronous pytube code in thread pool
            loop = asyncio.get_event_loop()
            url = await loop.run_in_executor(
                _executor,
                self._extract_sync,
                video_id
            )
            
            if url:
                self.stats['success'] += 1
                print(f"[Pytube] ✅ Extracted audio URL for {video_id}")
            else:
                self.stats['failed'] += 1
            
            return url
            
        except Exception as e:
            self.stats['failed'] += 1
            print(f"[Pytube] ❌ Error extracting {video_id}: {e}")
            return None
    
    def _extract_sync(self, video_id: str) -> Optional[str]:
        """
        Synchronous extraction using pytubefix
        Runs in thread pool to avoid blocking async loop
        """
        try:
            url = f"https://www.youtube.com/watch?v={video_id}"
            yt = YouTube(url)
            
            # Get audio-only streams
            audio_streams = yt.streams.filter(only_audio=True)
            
            if not audio_streams:
                print(f"[Pytube] No audio streams found for {video_id}")
                return None
            
            # Sort by bitrate (highest quality first)
            # pytubefix returns streams with .abr attribute
            sorted_streams = sorted(
                audio_streams,
                key=lambda s: int(s.abr.replace('kbps', '')) if s.abr else 0,
                reverse=True
            )
            
            # Get best quality audio stream
            best_audio = sorted_streams[0]
            stream_url = best_audio.url
            
            print(f"[Pytube] Found audio stream: {best_audio.abr or 'unknown'} kbps")
            return stream_url
            
        except Exception as e:
            print(f"[Pytube] Sync extraction error: {e}")
            return None
    
    def get_stats(self) -> dict:
        """Get extraction statistics"""
        total = self.stats['success'] + self.stats['failed']
        success_rate = (self.stats['success'] / total * 100) if total > 0 else 0
        
        return {
            'enabled': PYTUBE_AVAILABLE,
            'success': self.stats['success'],
            'failed': self.stats['failed'],
            'total': total,
            'success_rate': f"{success_rate:.1f}%"
        }

# Global pytube extractor instance
pytube_extractor = PytubeExtractor()
