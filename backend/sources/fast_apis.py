"""
Fast API Sources for Vikify
Piped and Invidious instances as fallback sources
"""
import aiohttp
import asyncio
from typing import Optional, List

# Piped instances (YouTube frontend with API)
PIPED_INSTANCES = [
    'https://pipedapi.kavin.rocks',
    'https://pipedapi.tokhmi.xyz',
    'https://api.piped.yt',
]

# Invidious instances (YouTube frontend with API)  
INVIDIOUS_INSTANCES = [
    'https://inv.nadeko.net',
    'https://invidious.nerdvpn.de',
]

async def try_piped(video_id: str, instance: str) -> Optional[str]:
    """Try to get audio URL from Piped instance"""
    try:
        url = f"{instance}/streams/{video_id}"
        timeout = aiohttp.ClientTimeout(total=4)
        
        async with aiohttp.ClientSession(timeout=timeout) as session:
            async with session.get(url) as resp:
                if resp.status != 200:
                    return None
                
                data = await resp.json()
                
                # Get audio streams
                audio_streams = data.get('audioStreams', [])
                if not audio_streams:
                    return None
                
                # Sort by bitrate (highest first)
                audio_streams.sort(key=lambda x: x.get('bitrate', 0), reverse=True)
                
                # Return best audio URL
                best = audio_streams[0]
                return best.get('url')
                
    except Exception as e:
        print(f"[Piped] {instance} error: {e}")
        return None

async def try_invidious(video_id: str, instance: str) -> Optional[str]:
    """Try to get audio URL from Invidious instance"""
    try:
        url = f"{instance}/api/v1/videos/{video_id}"
        timeout = aiohttp.ClientTimeout(total=4)
        
        async with aiohttp.ClientSession(timeout=timeout) as session:
            async with session.get(url) as resp:
                if resp.status != 200:
                    return None
                
                data = await resp.json()
                
                # Get adaptive formats (includes audio-only)
                formats = data.get('adaptiveFormats', [])
                
                # Filter for audio-only formats
                audio_formats = [f for f in formats if f.get('type', '').startswith('audio/')]
                
                if not audio_formats:
                    return None
                
                # Sort by bitrate
                audio_formats.sort(key=lambda x: int(x.get('bitrate', 0)), reverse=True)
                
                # Return best audio URL
                best = audio_formats[0]
                return best.get('url')
                
    except Exception as e:
        print(f"[Invidious] {instance} error: {e}")
        return None

async def try_fast_sources(video_id: str) -> Optional[str]:
    """
    Race all fast sources and return first successful result
    Args:
        video_id: YouTube video ID
    Returns:
        Audio stream URL or None
    """
    # Create tasks for all sources
    tasks = []
    
    # Add Piped tasks
    for instance in PIPED_INSTANCES:
        tasks.append(asyncio.create_task(try_piped(video_id, instance)))
    
    # Add Invidious tasks
    for instance in INVIDIOUS_INSTANCES:
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
