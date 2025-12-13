"""
Stream Resolver for Vikify
Unified stream resolution with Cobalt-first strategy
"""
import asyncio
import time
from typing import Tuple, Optional
import yt_dlp

from cache_manager import cache
from sources.cobalt import cobalt
from sources.fast_apis import try_fast_sources

class StreamResolver:
    def __init__(self):
        self.stats = {
            'cache': 0,
            'cobalt': 0,
            'fast_apis': 0,
            'ytdlp': 0,
            'failed': 0
        }
        self.timings = {
            'cache': [],
            'cobalt': [],
            'fast_apis': [],
            'ytdlp': []
        }
    
    async def resolve(
        self, 
        video_id: str, 
        song_title: str = '', 
        song_artist: str = ''
    ) -> Tuple[Optional[str], str, float]:
        """
        Resolve stream URL for a song using cascading sources
        
        Args:
            video_id: YouTube video ID or song ID
            song_title: Song title (for yt-dlp search fallback)
            song_artist: Song artist (for yt-dlp search fallback)
            
        Returns:
            Tuple of (url, source, time_taken)
        """
        start_time = time.time()
        
        # Layer 1: Check cache first (instant)
        cached_url = cache.get_url(video_id)
        if cached_url:
            self.stats['cache'] += 1
            elapsed = time.time() - start_time
            self._record_timing('cache', elapsed)
            return (cached_url, 'cache', elapsed)
        
        # Layer 2: Cobalt (primary, fast - usually <1s)
        print(f"[Resolver] Trying Cobalt for {video_id}")
        try:
            url = await asyncio.wait_for(
                cobalt.extract_audio(video_id), 
                timeout=5.0
            )
            if url:
                cache.set_url(video_id, url, 'cobalt')
                self.stats['cobalt'] += 1
                elapsed = time.time() - start_time
                self._record_timing('cobalt', elapsed)
                print(f"[Resolver] ✅ Cobalt succeeded in {elapsed:.2f}s")
                return (url, 'cobalt', elapsed)
        except asyncio.TimeoutError:
            print(f"[Resolver] ⏱️ Cobalt timeout (5s)")
        except Exception as e:
            print(f"[Resolver] ❌ Cobalt error: {e}")
        
        # Layer 3: Fast APIs - Piped/Invidious (race them)
        print(f"[Resolver] Trying fast APIs for {video_id}")
        try:
            url = await asyncio.wait_for(
                try_fast_sources(video_id), 
                timeout=4.0
            )
            if url:
                cache.set_url(video_id, url, 'fast_api')
                self.stats['fast_apis'] += 1
                elapsed = time.time() - start_time
                self._record_timing('fast_apis', elapsed)
                print(f"[Resolver] ✅ Fast API succeeded in {elapsed:.2f}s")
                return (url, 'fast_api', elapsed)
        except asyncio.TimeoutError:
            print(f"[Resolver] ⏱️ Fast APIs timeout (4s)")
        except Exception as e:
            print(f"[Resolver] ❌ Fast APIs error: {e}")
        
        # Layer 4: yt-dlp (slow but reliable fallback)
        print(f"[Resolver] Falling back to yt-dlp for {video_id}")
        try:
            url = await self._ytdlp_extract(video_id, song_title, song_artist)
            if url:
                cache.set_url(video_id, url, 'ytdlp')
                self.stats['ytdlp'] += 1
                elapsed = time.time() - start_time
                self._record_timing('ytdlp', elapsed)
                print(f"[Resolver] ✅ yt-dlp succeeded in {elapsed:.2f}s")
                return (url, 'ytdlp', elapsed)
        except Exception as e:
            print(f"[Resolver] ❌ yt-dlp error: {e}")
        
        # All sources failed
        self.stats['failed'] += 1
        elapsed = time.time() - start_time
        print(f"[Resolver] ❌ All sources failed for {video_id} in {elapsed:.2f}s")
        return (None, 'failed', elapsed)
    
    async def _ytdlp_extract(
        self, 
        video_id: str, 
        title: str = '', 
        artist: str = ''
    ) -> Optional[str]:
        """
        Extract using yt-dlp (runs in thread pool to not block)
        """
        loop = asyncio.get_event_loop()
        return await loop.run_in_executor(
            None, 
            self._ytdlp_sync, 
            video_id, 
            title, 
            artist
        )
    
    def _ytdlp_sync(
        self, 
        video_id: str, 
        title: str, 
        artist: str
    ) -> Optional[str]:
        """Synchronous yt-dlp extraction"""
        
        # Try direct video ID first
        if video_id and len(video_id) == 11:
            url = f"https://www.youtube.com/watch?v={video_id}"
        elif title:
            # Search by title/artist
            url = f"ytsearch1:{title} {artist} audio"
        else:
            return None
        
        ydl_opts = {
            'format': 'bestaudio/best',
            'noplaylist': True,
            'quiet': True,
            'no_warnings': True,
            'extract_flat': False,
        }
        
        try:
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                info = ydl.extract_info(url, download=False)
                
                if info:
                    # Direct video
                    if 'url' in info:
                        return info['url']
                    
                    # Search result
                    if 'entries' in info and info['entries']:
                        entry = info['entries'][0]
                        return entry.get('url')
                        
        except Exception as e:
            print(f"[yt-dlp] Error: {e}")
        
        return None
    
    def _record_timing(self, source: str, elapsed: float):
        """Record timing for statistics"""
        timings = self.timings.get(source, [])
        timings.append(elapsed)
        # Keep last 100 timings
        self.timings[source] = timings[-100:]
    
    def get_stats(self) -> dict:
        """Get resolver statistics"""
        total = sum(self.stats.values())
        
        result = {
            **self.stats,
            'total': total,
        }
        
        if total > 0:
            result['cache_rate'] = f"{self.stats['cache'] / total * 100:.1f}%"
            result['cobalt_rate'] = f"{self.stats['cobalt'] / total * 100:.1f}%"
            result['success_rate'] = f"{(total - self.stats['failed']) / total * 100:.1f}%"
        
        # Average timings
        for source, timings in self.timings.items():
            if timings:
                result[f'{source}_avg_time'] = f"{sum(timings) / len(timings):.2f}s"
        
        return result

# Global resolver instance
resolver = StreamResolver()


# Convenience function for sync code
def resolve_stream_sync(video_id: str, title: str = '', artist: str = ''):
    """Synchronous wrapper for resolve()"""
    loop = asyncio.new_event_loop()
    try:
        return loop.run_until_complete(resolver.resolve(video_id, title, artist))
    finally:
        loop.close()
