"""
Alternative API Sources for Vikify
Additional services for audio extraction
"""
import aiohttp
import asyncio
import re
import json
from typing import Optional, Dict
import sys
import os

# Add parent to path for imports
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from utils.anti_block import user_agents, rate_limiter

# ============================================================================
# SAVETUBE API
# ============================================================================

async def try_savetube(video_id: str) -> Optional[str]:
    """
    Extract audio using SaveTube API
    Fast and reliable alternative
    """
    try:
        url = f"https://api.savetube.me/info?url=https://www.youtube.com/watch?v={video_id}"
        
        if not await rate_limiter.wait_if_needed('savetube'):
            return None
        
        rate_limiter.record_request('savetube')
        
        timeout = aiohttp.ClientTimeout(total=8)
        async with aiohttp.ClientSession(timeout=timeout) as session:
            async with session.get(url, headers=user_agents.get_headers()) as resp:
                if resp.status != 200:
                    rate_limiter.record_error('savetube')
                    return None
                
                data = await resp.json()
                
                # Look for audio formats
                formats = data.get('formats', [])
                audio_formats = [f for f in formats if f.get('acodec') != 'none' and f.get('vcodec') == 'none']
                
                if audio_formats:
                    # Sort by quality
                    audio_formats.sort(key=lambda x: x.get('abr', 0), reverse=True)
                    url = audio_formats[0].get('url')
                    if url:
                        rate_limiter.record_success('savetube')
                        print(f"[SaveTube] ✅ Got audio URL")
                        return url
                
                rate_limiter.record_error('savetube')
                return None
                
    except Exception as e:
        rate_limiter.record_error('savetube')
        print(f"[SaveTube] Error: {e}")
        return None


# ============================================================================
# Y2MATE-STYLE API
# ============================================================================

async def try_y2mate(video_id: str) -> Optional[str]:
    """
    Extract audio using y2mate-style API
    Reverse-engineered public API
    """
    try:
        # Step 1: Analyze video
        analyze_url = "https://www.y2mate.com/mates/analyzeV2/ajax"
        
        if not await rate_limiter.wait_if_needed('y2mate'):
            return None
        
        rate_limiter.record_request('y2mate')
        
        timeout = aiohttp.ClientTimeout(total=10)
        async with aiohttp.ClientSession(timeout=timeout) as session:
            # Analyze request
            async with session.post(
                analyze_url,
                data={
                    'k_query': f'https://www.youtube.com/watch?v={video_id}',
                    'k_page': 'home',
                    'hl': 'en',
                    'q_auto': '1'
                },
                headers=user_agents.get_headers({
                    'Content-Type': 'application/x-www-form-urlencoded',
                    'Origin': 'https://www.y2mate.com',
                    'Referer': 'https://www.y2mate.com/'
                })
            ) as resp:
                if resp.status != 200:
                    rate_limiter.record_error('y2mate')
                    return None
                
                data = await resp.json()
                
                if data.get('status') != 'ok':
                    rate_limiter.record_error('y2mate')
                    return None
                
                # Get audio links
                links = data.get('links', {})
                mp3_links = links.get('mp3', {})
                
                if mp3_links:
                    # Get highest quality
                    for quality in ['320', '256', '192', '128']:
                        if quality in mp3_links:
                            link_data = mp3_links[quality]
                            k = link_data.get('k')
                            
                            if k:
                                # Step 2: Convert
                                convert_url = "https://www.y2mate.com/mates/convertV2/index"
                                async with session.post(
                                    convert_url,
                                    data={
                                        'vid': video_id,
                                        'k': k
                                    },
                                    headers=user_agents.get_headers({
                                        'Content-Type': 'application/x-www-form-urlencoded'
                                    })
                                ) as conv_resp:
                                    if conv_resp.status == 200:
                                        conv_data = await conv_resp.json()
                                        if conv_data.get('status') == 'ok':
                                            audio_url = conv_data.get('dlink')
                                            if audio_url:
                                                rate_limiter.record_success('y2mate')
                                                print(f"[Y2Mate] ✅ Got audio URL")
                                                return audio_url
                
                rate_limiter.record_error('y2mate')
                return None
                
    except Exception as e:
        rate_limiter.record_error('y2mate')
        print(f"[Y2Mate] Error: {e}")
        return None


# ============================================================================
# LOADER.TO API
# ============================================================================

async def try_loader(video_id: str) -> Optional[str]:
    """
    Extract audio using Loader.to API
    """
    try:
        api_url = f"https://api.loader.to/v2/download"
        
        if not await rate_limiter.wait_if_needed('loader'):
            return None
        
        rate_limiter.record_request('loader')
        
        timeout = aiohttp.ClientTimeout(total=10)
        async with aiohttp.ClientSession(timeout=timeout) as session:
            async with session.post(
                api_url,
                json={
                    'url': f'https://www.youtube.com/watch?v={video_id}',
                    'format': 'mp3',
                    'quality': '320'
                },
                headers=user_agents.get_headers({
                    'Content-Type': 'application/json'
                })
            ) as resp:
                if resp.status != 200:
                    rate_limiter.record_error('loader')
                    return None
                
                data = await resp.json()
                
                if data.get('success'):
                    audio_url = data.get('download_url')
                    if audio_url:
                        rate_limiter.record_success('loader')
                        print(f"[Loader] ✅ Got audio URL")
                        return audio_url
                
                rate_limiter.record_error('loader')
                return None
                
    except Exception as e:
        rate_limiter.record_error('loader')
        print(f"[Loader] Error: {e}")
        return None


# ============================================================================
# COMBINED ALTERNATIVE SOURCES
# ============================================================================

async def try_alternative_sources(video_id: str) -> Optional[str]:
    """
    Try all alternative sources in parallel
    Returns first successful result
    """
    # Create tasks for all alternative sources
    tasks = [
        asyncio.create_task(try_savetube(video_id)),
        asyncio.create_task(try_y2mate(video_id)),
        asyncio.create_task(try_loader(video_id)),
    ]
    
    # Race all tasks
    done, pending = await asyncio.wait(
        tasks,
        return_when=asyncio.FIRST_COMPLETED,
        timeout=8.0
    )
    
    # Check completed tasks
    for task in done:
        try:
            result = task.result()
            if result:
                # Cancel pending
                for p in pending:
                    p.cancel()
                return result
        except Exception:
            continue
    
    # Wait a bit more for remaining
    if pending:
        try:
            done2, pending2 = await asyncio.wait(pending, timeout=3.0)
            for task in done2:
                try:
                    result = task.result()
                    if result:
                        for p in pending2:
                            p.cancel()
                        return result
                except:
                    continue
            
            for p in pending2:
                p.cancel()
        except:
            pass
    
    return None


def get_alternative_sources_stats() -> Dict:
    """Get stats for alternative sources"""
    return {
        'savetube': rate_limiter.requests.get('savetube', []),
        'y2mate': rate_limiter.requests.get('y2mate', []),
        'loader': rate_limiter.requests.get('loader', []),
    }
