"""
Fast API Sources for Vikify
Piped and Invidious instances with auto-discovery
"""
import aiohttp
import asyncio
import sys
import os
from typing import Optional, List

# Add parent to path for imports
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from utils.anti_block import user_agents, rate_limiter
from sources.instance_discovery import discovery, ensure_initialized

# Fallback Piped instances (used before discovery)
PIPED_INSTANCES = [
    'https://pipedapi.kavin.rocks',
    'https://pipedapi.adminforge.de',
    'https://api.piped.privacydev.net',
    'https://pipedapi.darkness.services',
]

# Fallback Invidious instances
INVIDIOUS_INSTANCES = [
    'https://inv.nadeko.net',
    'https://invidious.nerdvpn.de',
    'https://invidious.jing.rocks',
    'https://yt.artemislena.eu',
]

# Instance health tracking
instance_health = {}

def _get_health(instance: str) -> dict:
    if instance not in instance_health:
        instance_health[instance] = {'success': 0, 'fail': 0, 'consecutive_fails': 0}
    return instance_health[instance]

def _mark_success(instance: str):
    h = _get_health(instance)
    h['success'] += 1
    h['consecutive_fails'] = 0

def _mark_failure(instance: str):
    h = _get_health(instance)
    h['fail'] += 1
    h['consecutive_fails'] += 1

async def try_piped(video_id: str, instance: str) -> Optional[str]:
    """Try to get audio URL from Piped instance"""
    # Skip instances with too many failures
    health = _get_health(instance)
    if health['consecutive_fails'] >= 3:
        return None
    
    try:
        # Check rate limit
        if not await rate_limiter.wait_if_needed('piped'):
            return None
        
        rate_limiter.record_request('piped')
        
        url = f"{instance}/streams/{video_id}"
        timeout = aiohttp.ClientTimeout(total=6)
        
        headers = user_agents.get_headers()
        
        async with aiohttp.ClientSession(timeout=timeout) as session:
            async with session.get(url, headers=headers) as resp:
                if resp.status == 429:
                    rate_limiter.record_error('piped', is_rate_limit=True)
                    _mark_failure(instance)
                    return None
                
                if resp.status != 200:
                    _mark_failure(instance)
                    return None
                
                data = await resp.json()
                
                # Get audio streams
                audio_streams = data.get('audioStreams', [])
                if not audio_streams:
                    _mark_failure(instance)
                    return None
                
                # Sort by bitrate (highest first)
                audio_streams.sort(key=lambda x: x.get('bitrate', 0), reverse=True)
                
                # Return best audio URL
                best = audio_streams[0]
                audio_url = best.get('url')
                
                if audio_url:
                    _mark_success(instance)
                    rate_limiter.record_success('piped')
                    return audio_url
                
                return None
                
    except Exception as e:
        _mark_failure(instance)
        rate_limiter.record_error('piped')
        print(f"[Piped] {instance} error: {e}")
        return None

async def try_invidious(video_id: str, instance: str) -> Optional[str]:
    """Try to get audio URL from Invidious instance"""
    # Skip instances with too many failures
    health = _get_health(instance)
    if health['consecutive_fails'] >= 3:
        return None
    
    try:
        # Check rate limit
        if not await rate_limiter.wait_if_needed('invidious'):
            return None
        
        rate_limiter.record_request('invidious')
        
        url = f"{instance}/api/v1/videos/{video_id}"
        timeout = aiohttp.ClientTimeout(total=6)
        
        headers = user_agents.get_headers()
        
        async with aiohttp.ClientSession(timeout=timeout) as session:
            async with session.get(url, headers=headers) as resp:
                if resp.status == 429:
                    rate_limiter.record_error('invidious', is_rate_limit=True)
                    _mark_failure(instance)
                    return None
                
                if resp.status != 200:
                    _mark_failure(instance)
                    return None
                
                data = await resp.json()
                
                # Get adaptive formats (includes audio-only)
                formats = data.get('adaptiveFormats', [])
                
                # Filter for audio-only formats
                audio_formats = [f for f in formats if f.get('type', '').startswith('audio/')]
                
                if not audio_formats:
                    _mark_failure(instance)
                    return None
                
                # Sort by bitrate
                audio_formats.sort(key=lambda x: int(x.get('bitrate', 0)), reverse=True)
                
                # Return best audio URL
                best = audio_formats[0]
                audio_url = best.get('url')
                
                if audio_url:
                    _mark_success(instance)
                    rate_limiter.record_success('invidious')
                    return audio_url
                
                return None
                
    except Exception as e:
        _mark_failure(instance)
        rate_limiter.record_error('invidious')
        print(f"[Invidious] {instance} error: {e}")
        return None

async def try_fast_sources(video_id: str) -> Optional[str]:
    """
    Race all fast sources and return first successful result
    Uses auto-discovered instances when available
    Args:
        video_id: YouTube video ID
    Returns:
        Audio stream URL or None
    """
    # Try to use discovered instances
    await ensure_initialized()
    
    piped_urls = discovery.get_piped_urls(5) or PIPED_INSTANCES
    invidious_urls = discovery.get_invidious_urls(5) or INVIDIOUS_INSTANCES
    
    # Create tasks for all sources
    tasks = []
    
    # Add Piped tasks
    for instance in piped_urls:
        tasks.append(asyncio.create_task(try_piped(video_id, instance)))
    
    # Add Invidious tasks
    for instance in invidious_urls:
        tasks.append(asyncio.create_task(try_invidious(video_id, instance)))
    
    # Race all tasks - return first successful result
    done, pending = await asyncio.wait(
        tasks,
        return_when=asyncio.FIRST_COMPLETED,
        timeout=4.0
    )
    
    # Check completed tasks for successful result
    for task in done:
        try:
            result = task.result()
            if result:
                # Cancel pending tasks
                for p in pending:
                    p.cancel()
                print(f"[FastAPIs] ✅ Got URL from racing")
                return result
        except Exception:
            continue
    
    # If first completed didn't succeed, wait for others briefly
    if pending:
        try:
            done2, pending2 = await asyncio.wait(pending, timeout=1.0)
            for task in done2:
                try:
                    result = task.result()
                    if result:
                        for p in pending2:
                            p.cancel()
                        return result
                except Exception:
                    continue
            
            # Cancel remaining
            for p in pending2:
                p.cancel()
        except Exception:
            pass
    
    return None

def get_instances_status() -> dict:
    """Get status of all fast API instances"""
    return {
        'piped': PIPED_INSTANCES,
        'invidious': INVIDIOUS_INSTANCES,
        'total_instances': len(PIPED_INSTANCES) + len(INVIDIOUS_INSTANCES)
    }
