"""
Cobalt.tools Integration for Vikify
Primary audio extraction source - fast and reliable
"""
import aiohttp
import asyncio
import time
import sys
import os
from typing import Optional, Dict, List

# Add parent to path for imports
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from utils.anti_block import user_agents, rate_limiter

# Public Cobalt instances (that don't require auth)
# Sorted by reliability - updated regularly
COBALT_INSTANCES = [
    'https://cobalt.canine.tools',
    'https://cobalt.wukko.me',
    'https://cobalt-api.hyper.lol',
    'https://api.cobalt.tools',  # May require auth but try anyway
    'https://co.eepy.today',
]

# Instance health tracking
cobalt_health = {
    instance: {'success': 0, 'fail': 0, 'last_used': 0, 'avg_time': 0, 'consecutive_fails': 0} 
    for instance in COBALT_INSTANCES
}

class CobaltExtractor:
    def __init__(self):
        self.timeout = aiohttp.ClientTimeout(total=8)
    
    async def extract_audio(self, video_id: str) -> Optional[str]:
        """
        Extract audio URL from YouTube using Cobalt
        Args:
            video_id: YouTube video ID
        Returns:
            Audio stream URL or None
        """
        video_url = f"https://www.youtube.com/watch?v={video_id}"
        
        # Check rate limit for cobalt
        if not await rate_limiter.wait_if_needed('cobalt'):
            print("[Cobalt] Rate limited, skipping")
            return None
        
        # Try instances in order of health
        for instance in self._get_healthy_instances():
            # Skip instances with too many consecutive failures
            health = cobalt_health.get(instance, {})
            if health.get('consecutive_fails', 0) >= 5:
                continue
            
            try:
                start = time.time()
                rate_limiter.record_request('cobalt')
                url = await self._try_instance(instance, video_url)
                elapsed = time.time() - start
                
                if url:
                    self._mark_success(instance, elapsed)
                    rate_limiter.record_success('cobalt')
                    print(f"[Cobalt] ✅ Got URL from {instance} in {elapsed:.2f}s")
                    return url
                else:
                    self._mark_failure(instance)
            except asyncio.TimeoutError:
                self._mark_failure(instance)
                rate_limiter.record_error('cobalt')
                print(f"[Cobalt] ⏱️ {instance} timeout")
            except Exception as e:
                self._mark_failure(instance)
                rate_limiter.record_error('cobalt')
                print(f"[Cobalt] ❌ {instance} failed: {e}")
                continue
        
        return None
    
    async def _try_instance(self, instance: str, video_url: str) -> Optional[str]:
        """Try to get audio from specific Cobalt instance"""
        # Cobalt API uses POST / (root endpoint)
        api_url = f"{instance}/"
        
        payload = {
            "url": video_url,
            "downloadMode": "audio",
            "audioFormat": "best",
        }
        
        # Use rotating headers
        headers = user_agents.get_headers({
            "Accept": "application/json",
            "Content-Type": "application/json",
        })
        
        async with aiohttp.ClientSession(timeout=self.timeout) as session:
            async with session.post(api_url, json=payload, headers=headers) as resp:
                if resp.status == 429:  # Rate limited
                    rate_limiter.record_error('cobalt', is_rate_limit=True)
                    return None
                
                if resp.status != 200:
                    text = await resp.text()
                    print(f"[Cobalt] {instance} returned {resp.status}: {text[:100]}")
                    return None
                
                data = await resp.json()
                return self._parse_response(data)
    
    def _parse_response(self, data: Dict) -> Optional[str]:
        """Parse Cobalt API response"""
        status = data.get('status')
        
        if status == 'stream':
            return data.get('url')
        
        elif status == 'redirect':
            return data.get('url')
        
        elif status == 'tunnel':
            return data.get('url')
        
        elif status == 'picker':
            # Multiple options - pick audio
            picker = data.get('picker', [])
            for item in picker:
                item_type = item.get('type', '')
                if item_type == 'audio' or 'audio' in item_type:
                    return item.get('url')
            # Fallback to first item
            if picker:
                return picker[0].get('url')
        
        elif status == 'error':
            error = data.get('error', {})
            code = error.get('code', 'Unknown error')
            print(f"[Cobalt] API error: {code}")
            return None
        
        # Try to find URL in response anyway
        if 'url' in data:
            return data['url']
        
        return None
    
    def _get_healthy_instances(self) -> List[str]:
        """Get instances sorted by health score"""
        def health_score(instance):
            h = cobalt_health[instance]
            total = h['success'] + h['fail']
            if total == 0:
                return 0.5  # Unknown - middle priority
            success_rate = h['success'] / total
            # Factor in average time (faster is better)
            time_factor = 1 / max(h['avg_time'], 0.1) if h['avg_time'] > 0 else 1
            return success_rate * time_factor
        
        return sorted(COBALT_INSTANCES, key=health_score, reverse=True)
    
    def _mark_success(self, instance: str, elapsed: float):
        """Mark instance as successful"""
        if instance not in cobalt_health:
            cobalt_health[instance] = {'success': 0, 'fail': 0, 'last_used': 0, 'avg_time': 0, 'consecutive_fails': 0}
        h = cobalt_health[instance]
        h['success'] += 1
        h['last_used'] = time.time()
        h['consecutive_fails'] = 0  # Reset on success
        # Rolling average for time
        if h['avg_time'] == 0:
            h['avg_time'] = elapsed
        else:
            h['avg_time'] = (h['avg_time'] * 0.8) + (elapsed * 0.2)
    
    def _mark_failure(self, instance: str):
        """Mark instance as failed"""
        if instance not in cobalt_health:
            cobalt_health[instance] = {'success': 0, 'fail': 0, 'last_used': 0, 'avg_time': 0, 'consecutive_fails': 0}
        cobalt_health[instance]['fail'] += 1
        cobalt_health[instance]['consecutive_fails'] = cobalt_health[instance].get('consecutive_fails', 0) + 1
    
    def get_health_stats(self) -> Dict:
        """Get health statistics for all instances"""
        stats = {}
        for instance, health in cobalt_health.items():
            total = health['success'] + health['fail']
            stats[instance] = {
                'success_rate': f"{health['success'] / total * 100:.1f}%" if total > 0 else 'N/A',
                'total_requests': total,
                'avg_time': f"{health['avg_time']:.2f}s" if health['avg_time'] > 0 else 'N/A',
                **health
            }
        return stats

# Global extractor instance
cobalt = CobaltExtractor()
