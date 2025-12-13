"""
Instance Discovery for Vikify
Auto-discovers and validates Piped/Invidious/Cobalt instances
"""
import aiohttp
import asyncio
import time
from typing import List, Dict, Optional
import random

# ============================================================================
# INSTANCE LISTS SOURCES
# ============================================================================

PIPED_INSTANCES_API = "https://piped-instances.kavin.rocks/"
INVIDIOUS_INSTANCES_API = "https://api.invidious.io/instances.json"

# Fallback hardcoded instances (updated December 2024)
FALLBACK_PIPED = [
    "https://pipedapi.kavin.rocks",
    "https://pipedapi.adminforge.de",
    "https://api.piped.privacydev.net",
    "https://pipedapi.darkness.services",
    "https://pipedapi.drgns.space",
]

FALLBACK_INVIDIOUS = [
    "https://inv.nadeko.net",
    "https://invidious.nerdvpn.de", 
    "https://invidious.jing.rocks",
    "https://yt.artemislena.eu",
    "https://invidious.privacydev.net",
]

FALLBACK_COBALT = [
    "https://cobalt.canine.tools",
    "https://cobalt.wukko.me",
    "https://api.cobalt.tools",
]


class InstanceDiscovery:
    """Auto-discovers and validates streaming instances"""
    
    def __init__(self):
        self.piped_instances: List[Dict] = []
        self.invidious_instances: List[Dict] = []
        self.cobalt_instances: List[Dict] = []
        self.last_refresh = 0
        self.refresh_interval = 3600  # 1 hour
        self._initialized = False
    
    async def initialize(self):
        """Initialize with discovered instances"""
        if self._initialized and time.time() - self.last_refresh < self.refresh_interval:
            return
        
        print("[Discovery] Refreshing instance lists...")
        
        # Discover in parallel
        results = await asyncio.gather(
            self._discover_piped(),
            self._discover_invidious(),
            self._discover_cobalt(),
            return_exceptions=True
        )
        
        self._initialized = True
        self.last_refresh = time.time()
        
        print(f"[Discovery] Found: {len(self.piped_instances)} Piped, "
              f"{len(self.invidious_instances)} Invidious, "
              f"{len(self.cobalt_instances)} Cobalt")
    
    async def _discover_piped(self) -> List[Dict]:
        """Discover Piped instances from official API"""
        try:
            async with aiohttp.ClientSession(
                timeout=aiohttp.ClientTimeout(total=10)
            ) as session:
                async with session.get(PIPED_INSTANCES_API) as resp:
                    if resp.status == 200:
                        instances = await resp.json()
                        
                        # Filter working API instances
                        valid = []
                        for inst in instances:
                            api_url = inst.get('api_url', '')
                            if api_url and not inst.get('cdn', False):
                                valid.append({
                                    'url': api_url.rstrip('/'),
                                    'name': inst.get('name', 'Unknown'),
                                    'region': inst.get('locations', 'Unknown'),
                                    'uptime': inst.get('uptime', 0),
                                })
                        
                        # Sort by uptime and take top 10
                        valid.sort(key=lambda x: x['uptime'], reverse=True)
                        self.piped_instances = valid[:10]
                        return self.piped_instances
        except Exception as e:
            print(f"[Discovery] Piped discovery error: {e}")
        
        # Fallback to hardcoded
        self.piped_instances = [{'url': u, 'name': 'Fallback', 'region': 'Unknown'} 
                                for u in FALLBACK_PIPED]
        return self.piped_instances
    
    async def _discover_invidious(self) -> List[Dict]:
        """Discover Invidious instances from official API"""
        try:
            async with aiohttp.ClientSession(
                timeout=aiohttp.ClientTimeout(total=10)
            ) as session:
                async with session.get(INVIDIOUS_INSTANCES_API) as resp:
                    if resp.status == 200:
                        data = await resp.json()
                        
                        # Filter working API instances
                        valid = []
                        for item in data:
                            if len(item) >= 2:
                                url, info = item[0], item[1]
                                if info.get('api', False) and info.get('type') == 'https':
                                    stats = info.get('stats', {})
                                    valid.append({
                                        'url': f"https://{url}",
                                        'name': url,
                                        'region': info.get('region', 'Unknown'),
                                        'users': stats.get('usage', {}).get('users', {}).get('total', 0),
                                    })
                        
                        # Sort by user count (more users = more reliable)
                        valid.sort(key=lambda x: x['users'], reverse=True)
                        self.invidious_instances = valid[:10]
                        return self.invidious_instances
        except Exception as e:
            print(f"[Discovery] Invidious discovery error: {e}")
        
        # Fallback to hardcoded
        self.invidious_instances = [{'url': u, 'name': 'Fallback', 'region': 'Unknown'} 
                                    for u in FALLBACK_INVIDIOUS]
        return self.invidious_instances
    
    async def _discover_cobalt(self) -> List[Dict]:
        """Cobalt instances (mostly hardcoded as no public API)"""
        # Test hardcoded instances
        valid = []
        for url in FALLBACK_COBALT:
            try:
                async with aiohttp.ClientSession(
                    timeout=aiohttp.ClientTimeout(total=5)
                ) as session:
                    async with session.get(f"{url}/") as resp:
                        if resp.status == 200:
                            data = await resp.json()
                            if 'cobalt' in data:
                                valid.append({
                                    'url': url,
                                    'name': data.get('cobalt', {}).get('url', url),
                                    'version': data.get('cobalt', {}).get('version', 'unknown'),
                                })
            except:
                pass
        
        if valid:
            self.cobalt_instances = valid
        else:
            self.cobalt_instances = [{'url': u, 'name': 'Fallback'} for u in FALLBACK_COBALT]
        
        return self.cobalt_instances
    
    def get_piped_urls(self, count: int = 5) -> List[str]:
        """Get random Piped API URLs"""
        if not self.piped_instances:
            return FALLBACK_PIPED[:count]
        instances = self.piped_instances[:count]
        random.shuffle(instances)
        return [i['url'] for i in instances]
    
    def get_invidious_urls(self, count: int = 5) -> List[str]:
        """Get random Invidious URLs"""
        if not self.invidious_instances:
            return FALLBACK_INVIDIOUS[:count]
        instances = self.invidious_instances[:count]
        random.shuffle(instances)
        return [i['url'] for i in instances]
    
    def get_cobalt_urls(self, count: int = 3) -> List[str]:
        """Get Cobalt URLs"""
        if not self.cobalt_instances:
            return FALLBACK_COBALT[:count]
        return [i['url'] for i in self.cobalt_instances[:count]]
    
    def get_all_stats(self) -> Dict:
        """Get discovery statistics"""
        return {
            'piped': {
                'count': len(self.piped_instances),
                'instances': [{'url': i['url'], 'region': i.get('region')} 
                             for i in self.piped_instances[:5]]
            },
            'invidious': {
                'count': len(self.invidious_instances),
                'instances': [{'url': i['url'], 'region': i.get('region')} 
                             for i in self.invidious_instances[:5]]
            },
            'cobalt': {
                'count': len(self.cobalt_instances),
                'instances': [{'url': i['url']} for i in self.cobalt_instances]
            },
            'last_refresh': self.last_refresh,
            'next_refresh': self.last_refresh + self.refresh_interval
        }


# Global instance
discovery = InstanceDiscovery()


async def ensure_initialized():
    """Ensure discovery is initialized"""
    if not discovery._initialized:
        await discovery.initialize()
