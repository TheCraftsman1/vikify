"""
Anti-Block Measures for Vikify
User agent rotation, rate limiting, proxy support
"""
import random
import time
import asyncio
import aiohttp
from typing import Optional, Dict, List
from collections import defaultdict
import threading

# ============================================================================
# USER AGENT ROTATION
# ============================================================================

USER_AGENTS = [
    # Chrome on Windows
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/118.0.0.0 Safari/537.36",
    # Chrome on Mac
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/119.0.0.0 Safari/537.36",
    # Firefox on Windows
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:120.0) Gecko/20100101 Firefox/120.0",
    # Firefox on Mac
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:121.0) Gecko/20100101 Firefox/121.0",
    # Safari on Mac
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Safari/605.1.15",
    # Edge on Windows
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0",
    # Chrome on Linux
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    # Android Chrome
    "Mozilla/5.0 (Linux; Android 14; SM-S918B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.144 Mobile Safari/537.36",
    "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.6099.144 Mobile Safari/537.36",
    # iOS Safari
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Mobile/15E148 Safari/604.1",
]

class UserAgentManager:
    """Rotates user agents to avoid detection"""
    
    def __init__(self):
        self.agents = USER_AGENTS.copy()
        self.index = 0
        self._lock = threading.Lock()
    
    def get_random(self) -> str:
        """Get a random user agent"""
        return random.choice(self.agents)
    
    def get_next(self) -> str:
        """Get next user agent in rotation"""
        with self._lock:
            agent = self.agents[self.index]
            self.index = (self.index + 1) % len(self.agents)
            return agent
    
    def get_headers(self, extra: Dict = None) -> Dict:
        """Get full headers with rotated user agent"""
        headers = {
            "User-Agent": self.get_random(),
            "Accept": "application/json, text/html, */*",
            "Accept-Language": "en-US,en;q=0.9",
            "Accept-Encoding": "gzip, deflate, br",
            "Connection": "keep-alive",
            "Sec-Fetch-Dest": "empty",
            "Sec-Fetch-Mode": "cors",
            "Sec-Fetch-Site": "cross-site",
        }
        if extra:
            headers.update(extra)
        return headers


# ============================================================================
# RATE LIMITER
# ============================================================================

class RateLimiter:
    """
    Rate limiter to avoid hitting API limits
    Implements per-domain rate limiting with exponential backoff
    """
    
    def __init__(self):
        self._lock = threading.Lock()
        # Track requests per domain: {domain: [timestamps]}
        self.requests: Dict[str, List[float]] = defaultdict(list)
        # Rate limits per domain (requests per minute)
        self.limits: Dict[str, int] = {
            'default': 30,
            'youtube.com': 10,
            'googlevideo.com': 20,
            'piped': 20,
            'invidious': 20,
            'cobalt': 30,
        }
        # Backoff tracking: {domain: (backoff_until, backoff_seconds)}
        self.backoff: Dict[str, tuple] = {}
    
    def _get_domain_key(self, url_or_name: str) -> str:
        """Extract domain key from URL or use name directly"""
        if 'youtube.com' in url_or_name:
            return 'youtube.com'
        if 'googlevideo.com' in url_or_name:
            return 'googlevideo.com'
        if 'piped' in url_or_name.lower():
            return 'piped'
        if 'invidious' in url_or_name.lower():
            return 'invidious'
        if 'cobalt' in url_or_name.lower():
            return 'cobalt'
        return url_or_name if url_or_name in self.limits else 'default'
    
    def can_request(self, domain: str) -> bool:
        """Check if we can make a request to this domain"""
        key = self._get_domain_key(domain)
        now = time.time()
        
        # Check if in backoff period
        if key in self.backoff:
            backoff_until, _ = self.backoff[key]
            if now < backoff_until:
                return False
            else:
                del self.backoff[key]
        
        # Check rate limit
        with self._lock:
            # Clean old requests (older than 1 minute)
            self.requests[key] = [t for t in self.requests[key] if now - t < 60]
            limit = self.limits.get(key, self.limits['default'])
            return len(self.requests[key]) < limit
    
    def record_request(self, domain: str):
        """Record a request to this domain"""
        key = self._get_domain_key(domain)
        with self._lock:
            self.requests[key].append(time.time())
    
    def record_error(self, domain: str, is_rate_limit: bool = False):
        """Record an error, potentially triggering backoff"""
        key = self._get_domain_key(domain)
        now = time.time()
        
        # Get current backoff or start at 5 seconds
        if key in self.backoff:
            _, current_backoff = self.backoff[key]
            # Exponential backoff, max 5 minutes
            new_backoff = min(current_backoff * 2, 300)
        else:
            new_backoff = 5 if not is_rate_limit else 30
        
        self.backoff[key] = (now + new_backoff, new_backoff)
        print(f"[RateLimiter] {key} backing off for {new_backoff}s")
    
    def record_success(self, domain: str):
        """Record a success, reducing backoff"""
        key = self._get_domain_key(domain)
        if key in self.backoff:
            del self.backoff[key]
    
    async def wait_if_needed(self, domain: str) -> bool:
        """Wait if rate limited, return False if should skip this domain"""
        key = self._get_domain_key(domain)
        
        # Check backoff
        if key in self.backoff:
            backoff_until, _ = self.backoff[key]
            wait_time = backoff_until - time.time()
            if wait_time > 5:  # Don't wait more than 5 seconds
                return False
            if wait_time > 0:
                await asyncio.sleep(wait_time)
        
        # Check rate limit
        if not self.can_request(domain):
            await asyncio.sleep(1)  # Wait 1 second and try again
            if not self.can_request(domain):
                return False
        
        return True
    
    def get_stats(self) -> Dict:
        """Get rate limiter statistics"""
        now = time.time()
        stats = {}
        for domain, timestamps in self.requests.items():
            recent = [t for t in timestamps if now - t < 60]
            limit = self.limits.get(domain, self.limits['default'])
            stats[domain] = {
                'requests_last_minute': len(recent),
                'limit': limit,
                'usage': f"{len(recent) / limit * 100:.1f}%"
            }
        
        # Add backoff info
        for domain, (until, seconds) in self.backoff.items():
            if domain not in stats:
                stats[domain] = {}
            remaining = max(0, until - now)
            stats[domain]['backoff_remaining'] = f"{remaining:.1f}s"
        
        return stats


# ============================================================================
# PROXY MANAGER
# ============================================================================

class ProxyManager:
    """
    Manages proxy rotation for avoiding IP blocks
    Supports HTTP, HTTPS, and SOCKS5 proxies
    """
    
    def __init__(self):
        self._lock = threading.Lock()
        self.proxies: List[Dict] = []
        self.proxy_health: Dict[str, Dict] = {}
        self.current_index = 0
        self.enabled = False
    
    def add_proxy(self, proxy_url: str, region: str = 'unknown'):
        """
        Add a proxy to the pool
        Format: http://user:pass@host:port or socks5://host:port
        """
        with self._lock:
            proxy_id = f"{proxy_url[:50]}..."
            self.proxies.append({
                'url': proxy_url,
                'region': region,
                'id': proxy_id
            })
            self.proxy_health[proxy_id] = {
                'success': 0,
                'fail': 0,
                'last_used': 0,
                'avg_latency': 0
            }
            self.enabled = True
        print(f"[Proxy] Added proxy from {region}")
    
    def add_proxies_from_list(self, proxy_list: List[str], region: str = 'unknown'):
        """Add multiple proxies at once"""
        for proxy in proxy_list:
            if proxy.strip():
                self.add_proxy(proxy.strip(), region)
    
    def get_proxy(self) -> Optional[str]:
        """Get next healthy proxy in rotation"""
        if not self.enabled or not self.proxies:
            return None
        
        with self._lock:
            # Try to find a healthy proxy
            for _ in range(len(self.proxies)):
                proxy = self.proxies[self.current_index]
                self.current_index = (self.current_index + 1) % len(self.proxies)
                
                health = self.proxy_health.get(proxy['id'], {})
                total = health.get('success', 0) + health.get('fail', 0)
                
                # Skip proxies with >70% failure rate (if enough samples)
                if total >= 5:
                    fail_rate = health.get('fail', 0) / total
                    if fail_rate > 0.7:
                        continue
                
                return proxy['url']
        
        return None
    
    def get_proxy_for_aiohttp(self) -> Optional[str]:
        """Get proxy formatted for aiohttp"""
        return self.get_proxy()
    
    def mark_success(self, proxy_url: str, latency: float = 0):
        """Mark a proxy request as successful"""
        proxy_id = f"{proxy_url[:50]}..."
        if proxy_id in self.proxy_health:
            h = self.proxy_health[proxy_id]
            h['success'] += 1
            h['last_used'] = time.time()
            if latency > 0:
                if h['avg_latency'] == 0:
                    h['avg_latency'] = latency
                else:
                    h['avg_latency'] = (h['avg_latency'] * 0.8) + (latency * 0.2)
    
    def mark_failure(self, proxy_url: str):
        """Mark a proxy request as failed"""
        proxy_id = f"{proxy_url[:50]}..."
        if proxy_id in self.proxy_health:
            self.proxy_health[proxy_id]['fail'] += 1
    
    def remove_bad_proxies(self):
        """Remove proxies with high failure rates"""
        with self._lock:
            to_remove = []
            for proxy in self.proxies:
                health = self.proxy_health.get(proxy['id'], {})
                total = health.get('success', 0) + health.get('fail', 0)
                if total >= 10:
                    fail_rate = health.get('fail', 0) / total
                    if fail_rate > 0.8:
                        to_remove.append(proxy)
            
            for proxy in to_remove:
                self.proxies.remove(proxy)
                del self.proxy_health[proxy['id']]
                print(f"[Proxy] Removed bad proxy: {proxy['id']}")
    
    def get_stats(self) -> Dict:
        """Get proxy pool statistics"""
        return {
            'enabled': self.enabled,
            'total_proxies': len(self.proxies),
            'proxies': {
                p['id']: {
                    'region': p['region'],
                    **self.proxy_health.get(p['id'], {})
                }
                for p in self.proxies[:10]  # First 10 only
            }
        }


# ============================================================================
# GLOBAL INSTANCES
# ============================================================================

user_agents = UserAgentManager()
rate_limiter = RateLimiter()
proxy_manager = ProxyManager()


# ============================================================================
# HELPER FUNCTIONS
# ============================================================================

def get_aiohttp_session_kwargs() -> Dict:
    """Get kwargs for creating an aiohttp session with anti-block measures"""
    kwargs = {
        'headers': user_agents.get_headers(),
    }
    
    proxy = proxy_manager.get_proxy_for_aiohttp()
    if proxy:
        kwargs['proxy'] = proxy
    
    return kwargs


async def fetch_with_retry(
    url: str,
    method: str = 'GET',
    max_retries: int = 3,
    timeout: float = 10,
    **kwargs
) -> Optional[Dict]:
    """
    Fetch URL with retry logic, rate limiting, and anti-block measures
    """
    domain = url.split('/')[2] if '://' in url else url
    
    for attempt in range(max_retries):
        # Check rate limit
        if not await rate_limiter.wait_if_needed(domain):
            print(f"[Fetch] Rate limited for {domain}, skipping")
            return None
        
        try:
            rate_limiter.record_request(domain)
            
            # Build headers
            headers = user_agents.get_headers(kwargs.pop('headers', None))
            
            # Get proxy
            proxy = proxy_manager.get_proxy_for_aiohttp()
            
            async with aiohttp.ClientSession(
                timeout=aiohttp.ClientTimeout(total=timeout)
            ) as session:
                async with session.request(
                    method, url,
                    headers=headers,
                    proxy=proxy,
                    **kwargs
                ) as resp:
                    if resp.status == 429:  # Rate limited
                        rate_limiter.record_error(domain, is_rate_limit=True)
                        await asyncio.sleep(2 ** attempt)
                        continue
                    
                    if resp.status >= 400:
                        rate_limiter.record_error(domain)
                        if proxy:
                            proxy_manager.mark_failure(proxy)
                        continue
                    
                    # Success
                    rate_limiter.record_success(domain)
                    if proxy:
                        proxy_manager.mark_success(proxy)
                    
                    return await resp.json()
                    
        except asyncio.TimeoutError:
            rate_limiter.record_error(domain)
            print(f"[Fetch] Timeout for {url} (attempt {attempt + 1})")
        except Exception as e:
            rate_limiter.record_error(domain)
            print(f"[Fetch] Error for {url}: {e}")
    
    return None
