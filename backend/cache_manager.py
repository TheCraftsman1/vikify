"""
Cache Manager for Vikify
Handles URL caching and metadata caching for fast playback
"""
import json
import time
import os
from pathlib import Path
from typing import Optional, Dict, Any
import threading

# Cache directory
CACHE_DIR = Path('/tmp/vikify_cache')
try:
    CACHE_DIR.mkdir(exist_ok=True)
except:
    CACHE_DIR = Path('./cache')
    CACHE_DIR.mkdir(exist_ok=True)

URL_CACHE_FILE = CACHE_DIR / 'url_cache.json'
METADATA_CACHE_FILE = CACHE_DIR / 'metadata_cache.json'

# TTL settings
URL_CACHE_TTL = 6 * 60 * 60  # 6 hours (YouTube URLs expire)
METADATA_CACHE_TTL = 30 * 24 * 60 * 60  # 30 days

class CacheManager:
    def __init__(self):
        self._lock = threading.Lock()
        self.url_cache = self._load_cache(URL_CACHE_FILE)
        self.metadata_cache = self._load_cache(METADATA_CACHE_FILE)
        self.stats = {'hits': 0, 'misses': 0}
        
        # Clean expired on startup
        self.cleanup_expired()
    
    def _load_cache(self, file_path: Path) -> Dict:
        """Load cache from disk"""
        try:
            if file_path.exists():
                with open(file_path, 'r') as f:
                    return json.load(f)
        except Exception as e:
            print(f"[Cache] Load error for {file_path}: {e}")
        return {}
    
    def _save_cache(self, cache: Dict, file_path: Path):
        """Save cache to disk (async-safe)"""
        try:
            with self._lock:
                with open(file_path, 'w') as f:
                    json.dump(cache, f)
        except Exception as e:
            print(f"[Cache] Save error for {file_path}: {e}")
    
    def get_url(self, song_id: str) -> Optional[str]:
        """
        Get cached URL for a song
        Returns None if not cached or expired
        """
        entry = self.url_cache.get(song_id)
        
        if entry and time.time() < entry.get('expiry', 0):
            self.stats['hits'] += 1
            print(f"[Cache] ✅ HIT for {song_id} (source: {entry.get('source', 'unknown')})")
            return entry['url']
        
        # Remove expired entry
        if entry:
            del self.url_cache[song_id]
        
        self.stats['misses'] += 1
        print(f"[Cache] ❌ MISS for {song_id}")
        return None
    
    def set_url(self, song_id: str, url: str, source: str = 'unknown'):
        """
        Cache URL with metadata
        Args:
            song_id: Unique song identifier
            url: Audio stream URL
            source: Source of the URL (cobalt, piped, ytdlp, etc.)
        """
        self.url_cache[song_id] = {
            'url': url,
            'source': source,
            'expiry': time.time() + URL_CACHE_TTL,
            'cached_at': time.time()
        }
        self._save_cache(self.url_cache, URL_CACHE_FILE)
        print(f"[Cache] 💾 Saved {song_id} from {source}")
    
    def get_metadata(self, song_id: str) -> Optional[Dict]:
        """Get cached song metadata"""
        entry = self.metadata_cache.get(song_id)
        if entry:
            # Metadata doesn't really expire, but check anyway
            if time.time() < entry.get('expiry', time.time() + 1):
                return entry.get('data')
        return None
    
    def set_metadata(self, song_id: str, metadata: Dict):
        """
        Cache song metadata (artist, title, album, duration, etc.)
        This is long-lived cache since metadata doesn't change
        """
        self.metadata_cache[song_id] = {
            'data': metadata,
            'expiry': time.time() + METADATA_CACHE_TTL,
            'cached_at': time.time()
        }
        self._save_cache(self.metadata_cache, METADATA_CACHE_FILE)
    
    def cleanup_expired(self):
        """Remove expired entries from caches"""
        now = time.time()
        
        # Clean URL cache
        before_url = len(self.url_cache)
        self.url_cache = {
            k: v for k, v in self.url_cache.items()
            if v.get('expiry', 0) > now
        }
        removed_url = before_url - len(self.url_cache)
        
        # Clean metadata cache
        before_meta = len(self.metadata_cache)
        self.metadata_cache = {
            k: v for k, v in self.metadata_cache.items()
            if v.get('expiry', 0) > now
        }
        removed_meta = before_meta - len(self.metadata_cache)
        
        if removed_url > 0 or removed_meta > 0:
            self._save_cache(self.url_cache, URL_CACHE_FILE)
            self._save_cache(self.metadata_cache, METADATA_CACHE_FILE)
            print(f"[Cache] 🗑️ Cleaned {removed_url} URLs, {removed_meta} metadata entries")
    
    def get_stats(self) -> Dict[str, Any]:
        """Get cache statistics"""
        total = self.stats['hits'] + self.stats['misses']
        hit_rate = (self.stats['hits'] / total * 100) if total > 0 else 0
        
        return {
            'hits': self.stats['hits'],
            'misses': self.stats['misses'],
            'hit_rate': f"{hit_rate:.1f}%",
            'url_cache_size': len(self.url_cache),
            'metadata_cache_size': len(self.metadata_cache),
            'url_cache_ttl': f"{URL_CACHE_TTL // 3600}h",
            'metadata_cache_ttl': f"{METADATA_CACHE_TTL // 86400}d"
        }
    
    def clear_all(self):
        """Clear all caches (for debugging)"""
        self.url_cache = {}
        self.metadata_cache = {}
        self.stats = {'hits': 0, 'misses': 0}
        self._save_cache(self.url_cache, URL_CACHE_FILE)
        self._save_cache(self.metadata_cache, METADATA_CACHE_FILE)
        print("[Cache] 🗑️ All caches cleared")

# Global cache instance
cache = CacheManager()
