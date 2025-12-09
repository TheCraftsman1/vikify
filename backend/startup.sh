#!/bin/bash
# Azure App Service startup script for Vikify Backend

# Install ffmpeg (required for yt-dlp audio conversion)
apt-get update && apt-get install -y ffmpeg

# Start the server with gunicorn
gunicorn --bind=0.0.0.0:$PORT --timeout 600 --workers 2 server:app
