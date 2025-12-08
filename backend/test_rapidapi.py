import requests
import json

# Test the RapidAPI Spotify Downloader
RAPIDAPI_KEY = 'db265c87acmsh63e2fe36b9673a3p182b87jsne1fdbe07fddc'
RAPIDAPI_HOST = 'spotify-downloader9.p.rapidapi.com'

# Test with a known Spotify track ID
test_track_id = '3n3Ppam7vgaVa1iaRUc9Lp'  # "Mr. Brightside" by The Killers
spotify_url = f'https://open.spotify.com/track/{test_track_id}'

print(f"Testing RapidAPI Spotify Downloader...")
print(f"Track URL: {spotify_url}\n")

response = requests.get(
    f'https://{RAPIDAPI_HOST}/downloadSong',
    params={'songId': spotify_url},
    headers={
        'x-rapidapi-key': RAPIDAPI_KEY,
        'x-rapidapi-host': RAPIDAPI_HOST
    },
    timeout=15
)

print(f"Status Code: {response.status_code}\n")

if response.status_code == 200:
    try:
        data = response.json()
        
        # Save to file for inspection
        with open('rapidapi_response.json', 'w') as f:
            json.dump(data, f, indent=2)
        
        print("Response saved to rapidapi_response.json")
        print("\nChecking for download link...")
        
        # Check various possible locations for the download link
        if 'data' in data and 'downloadLink' in data['data']:
            print(f"✓ Found download link: {data['data']['downloadLink'][:100]}...")
        elif 'downloadLink' in data:
            print(f"✓ Found download link: {data['downloadLink'][:100]}...")
        elif 'link' in data:
            print(f"✓ Found link: {data['link'][:100]}...")
        else:
            print("✗ No download link found in response")
            print(f"Response keys: {list(data.keys())}")
    except Exception as e:
        print(f"Error parsing response: {e}")
        print(f"Raw response: {response.text[:500]}")
else:
    print(f"Error: {response.text}")
