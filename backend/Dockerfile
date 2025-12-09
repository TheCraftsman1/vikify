FROM python:3.11-slim

# Install ffmpeg for yt-dlp audio processing
RUN apt-get update && apt-get install -y ffmpeg && rm -rf /var/lib/apt/lists/*

# Set working directory
WORKDIR /app

# Copy requirements first for caching
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY . .

# Expose port
EXPOSE 5000

# Run with gunicorn - use shell form to expand $PORT
CMD gunicorn --bind 0.0.0.0:${PORT:-5000} --timeout 600 --workers 2 server:app
