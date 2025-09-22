# Video Service Dockerfile - Enterprise YOLO Processing
FROM python:3.11-slim

# Set environment variables
ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    DEBIAN_FRONTEND=noninteractive

# Create app user
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Install system dependencies for OpenCV and video processing
RUN apt-get update && apt-get install -y \
    gcc \
    g++ \
    libpq-dev \
    libgl1 \
    libglib2.0-0 \
    libsm6 \
    libxext6 \
    libxrender-dev \
    libgomp1 \
    libgstreamer1.0-dev \
    libgstreamer-plugins-base1.0-dev \
    libgtk-3-dev \
    libavcodec-dev \
    libavformat-dev \
    libswscale-dev \
    libv4l-dev \
    libxvidcore-dev \
    libx264-dev \
    libjpeg-dev \
    libpng-dev \
    libtiff-dev \
    libatlas-base-dev \
    wget \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Set work directory
WORKDIR /app

# Create necessary directories
RUN mkdir -p /app/logs /app/models /app/cache && \
    chown -R appuser:appuser /app

# Copy requirements and install Python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir --upgrade pip && \
    pip install --no-cache-dir -r requirements.txt

# Download YOLO weights
RUN wget -O yolov8n.pt https://github.com/ultralytics/assets/releases/download/v0.0.0/yolov8n.pt && \
    wget -O yolov8s.pt https://github.com/ultralytics/assets/releases/download/v0.0.0/yolov8s.pt && \
    wget -O yolov8m.pt https://github.com/ultralytics/assets/releases/download/v0.0.0/yolov8m.pt

# Copy application code
COPY . .

# Set ownership
RUN chown -R appuser:appuser /app

# Switch to non-root user
USER appuser

# Health check
HEALTHCHECK --interval=30s --timeout=30s --start-period=5s --retries=3 \
    CMD curl -f http://localhost:5001/health || exit 1

# Expose port
EXPOSE 5001

# Run application
CMD ["python", "app.py"]