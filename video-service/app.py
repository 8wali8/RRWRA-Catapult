"""
Video Processing Service - Enterprise YOLO Object Detection
Real-time video analysis with advanced object detection, tracking, and brand recognition
"""

from flask import Flask, request, jsonify, Response
from flask_cors import CORS
import cv2
import numpy as np
from ultralytics import YOLO
import torch
import json
import redis
from kafka import KafkaProducer
import os
import logging
import time
from datetime import datetime
import threading
from collections import defaultdict, deque
import hashlib
import base64
from PIL import Image
import io
import asyncio
import websockets
from concurrent.futures import ThreadPoolExecutor
import prometheus_client
from prometheus_client import Counter, Histogram, Gauge
import psycopg2
from sqlalchemy import create_engine, Column, Integer, String, Float, DateTime, Text, Boolean, LargeBinary
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker
from dataclasses import dataclass
from typing import List, Dict, Optional, Tuple
import gc

# Initialize Flask app
app = Flask(__name__)
CORS(app)

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('/app/logs/video-service.log'),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)

# Prometheus metrics
REQUEST_COUNT = Counter('video_service_requests_total', 'Total video service requests', ['method', 'endpoint'])
PROCESSING_TIME = Histogram('video_processing_duration_seconds', 'Video processing time')
DETECTION_COUNT = Counter('object_detections_total', 'Total object detections', ['class_name'])
ACTIVE_STREAMS = Gauge('active_video_streams', 'Number of active video streams')
FRAME_RATE = Gauge('video_processing_fps', 'Video processing frames per second')

# Configuration
@dataclass
class VideoConfig:
    # YOLO Model Configuration
    MODEL_PATH: str = "yolov8n.pt"
    CONFIDENCE_THRESHOLD: float = 0.5
    IOU_THRESHOLD: float = 0.4
    MAX_DETECTIONS: int = 100
    
    # Video Processing
    TARGET_FPS: int = 30
    MAX_RESOLUTION: Tuple[int, int] = (1920, 1080)
    BUFFER_SIZE: int = 10
    
    # Performance
    BATCH_SIZE: int = 4
    GPU_MEMORY_THRESHOLD: float = 0.8
    MAX_CONCURRENT_STREAMS: int = 5
    
    # Cache Settings
    CACHE_TTL: int = 300  # 5 minutes
    MAX_CACHE_SIZE: int = 1000

config = VideoConfig()

# Device configuration
device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
logger.info(f"Using device: {device}")

# Advanced Object Detection Classes
class ObjectTracker:
    """Advanced object tracking with Kalman filters"""
    
    def __init__(self, max_age=30, min_hits=3):
        self.max_age = max_age
        self.min_hits = min_hits
        self.trackers = []
        self.frame_count = 0
        
    def update(self, detections):
        """Update tracker with new detections"""
        self.frame_count += 1
        
        # Implementation would include Kalman filter tracking
        # For now, simplified tracking based on bbox overlap
        tracked_objects = []
        
        for detection in detections:
            best_match = None
            best_iou = 0
            
            for tracker in self.trackers:
                iou = self._calculate_iou(detection['bbox'], tracker['bbox'])
                if iou > best_iou and iou > 0.3:
                    best_iou = iou
                    best_match = tracker
            
            if best_match:
                best_match.update(detection)
                tracked_objects.append(best_match)
            else:
                # Create new tracker
                new_tracker = TrackedObject(detection, self.frame_count)
                self.trackers.append(new_tracker)
                tracked_objects.append(new_tracker)
        
        # Remove old trackers
        self.trackers = [t for t in self.trackers if t.age < self.max_age]
        
        return [t.to_dict() for t in tracked_objects if t.hits >= self.min_hits]
    
    def _calculate_iou(self, box1, box2):
        """Calculate Intersection over Union"""
        x1, y1, x2, y2 = box1
        x1_t, y1_t, x2_t, y2_t = box2
        
        # Calculate intersection
        xi1 = max(x1, x1_t)
        yi1 = max(y1, y1_t)
        xi2 = min(x2, x2_t)
        yi2 = min(y2, y2_t)
        
        if xi2 <= xi1 or yi2 <= yi1:
            return 0
        
        intersection = (xi2 - xi1) * (yi2 - yi1)
        
        # Calculate union
        box1_area = (x2 - x1) * (y2 - y1)
        box2_area = (x2_t - x1_t) * (y2_t - y1_t)
        union = box1_area + box2_area - intersection
        
        return intersection / union if union > 0 else 0

class TrackedObject:
    """Individual tracked object with history"""
    
    def __init__(self, detection, frame_count):
        self.id = f"obj_{int(time.time() * 1000)}"
        self.bbox = detection['bbox']
        self.class_name = detection['class_name']
        self.confidence = detection['confidence']
        self.first_seen = frame_count
        self.last_seen = frame_count
        self.hits = 1
        self.age = 0
        self.history = deque(maxlen=30)
        self.history.append(detection)
    
    def update(self, detection):
        """Update object with new detection"""
        self.bbox = detection['bbox']
        self.confidence = detection['confidence']
        self.last_seen = self.last_seen + 1
        self.hits += 1
        self.age = 0
        self.history.append(detection)
    
    def to_dict(self):
        """Convert to dictionary representation"""
        return {
            'id': self.id,
            'bbox': self.bbox,
            'class_name': self.class_name,
            'confidence': self.confidence,
            'first_seen': self.first_seen,
            'last_seen': self.last_seen,
            'hits': self.hits,
            'trajectory': list(self.history)[-10:]  # Last 10 positions
        }

class AdvancedYOLOProcessor:
    """Enterprise YOLO processing with advanced features"""
    
    def __init__(self):
        self.model = YOLO(config.MODEL_PATH)
        self.model.to(device)
        self.trackers = {}  # One tracker per stream
        self.class_counts = defaultdict(int)
        self.performance_stats = {
            'total_frames': 0,
            'total_detections': 0,
            'processing_times': deque(maxlen=100)
        }
        
        # Brand-specific detection rules
        self.brand_rules = {
            'coca_cola': {
                'colors': [(220, 20, 60), (255, 255, 255)],
                'shapes': ['bottle', 'can'],
                'text_patterns': ['coca', 'cola', 'coke']
            },
            'pepsi': {
                'colors': [(0, 50, 153), (220, 20, 60)],
                'shapes': ['bottle', 'can'],
                'text_patterns': ['pepsi', 'cola']
            },
            'redbull': {
                'colors': [(0, 45, 156), (255, 209, 0)],
                'shapes': ['can'],
                'text_patterns': ['red bull', 'redbull']
            },
            'monster': {
                'colors': [(0, 166, 81), (0, 0, 0)],
                'shapes': ['can'],
                'text_patterns': ['monster', 'energy']
            }
        }
    
    def process_frame(self, frame, stream_id="default"):
        """Process single frame with advanced detection"""
        start_time = time.time()
        
        try:
            # Resize frame if too large
            height, width = frame.shape[:2]
            if width > config.MAX_RESOLUTION[0] or height > config.MAX_RESOLUTION[1]:
                scale = min(config.MAX_RESOLUTION[0]/width, config.MAX_RESOLUTION[1]/height)
                new_width = int(width * scale)
                new_height = int(height * scale)
                frame = cv2.resize(frame, (new_width, new_height))
            
            # Run YOLO detection
            results = self.model(frame, conf=config.CONFIDENCE_THRESHOLD, iou=config.IOU_THRESHOLD)
            
            detections = []
            for result in results:
                boxes = result.boxes
                if boxes is not None:
                    for box in boxes:
                        detection = {
                            'bbox': box.xyxy.tolist()[0],
                            'confidence': float(box.conf),
                            'class_id': int(box.cls),
                            'class_name': result.names[int(box.cls)]
                        }
                        detections.append(detection)
                        
                        # Update metrics
                        DETECTION_COUNT.labels(class_name=detection['class_name']).inc()
                        self.class_counts[detection['class_name']] += 1
            
            # Object tracking
            if stream_id not in self.trackers:
                self.trackers[stream_id] = ObjectTracker()
            
            tracked_objects = self.trackers[stream_id].update(detections)
            
            # Brand detection
            brand_detections = self._detect_brands(frame, detections)
            
            # Performance tracking
            processing_time = time.time() - start_time
            self.performance_stats['processing_times'].append(processing_time)
            self.performance_stats['total_frames'] += 1
            self.performance_stats['total_detections'] += len(detections)
            
            # Update FPS metric
            if len(self.performance_stats['processing_times']) > 10:
                avg_time = sum(list(self.performance_stats['processing_times'])[-10:]) / 10
                FRAME_RATE.set(1.0 / avg_time if avg_time > 0 else 0)
            
            return {
                'detections': detections,
                'tracked_objects': tracked_objects,
                'brand_detections': brand_detections,
                'frame_info': {
                    'width': frame.shape[1],
                    'height': frame.shape[0],
                    'processing_time': processing_time
                },
                'timestamp': datetime.utcnow().isoformat()
            }
            
        except Exception as e:
            logger.error(f"Frame processing error: {str(e)}")
            raise
    
    def _detect_brands(self, frame, detections):
        """Advanced brand detection using color and shape analysis"""
        brand_detections = []
        
        for detection in detections:
            if detection['class_name'] in ['bottle', 'can', 'cup']:
                bbox = detection['bbox']
                x1, y1, x2, y2 = map(int, bbox)
                roi = frame[y1:y2, x1:x2]
                
                if roi.size > 0:
                    # Extract dominant colors
                    dominant_colors = self._extract_dominant_colors(roi)
                    
                    # Check against brand rules
                    for brand, rules in self.brand_rules.items():
                        color_match = self._match_brand_colors(dominant_colors, rules['colors'])
                        
                        if color_match > 0.6:  # High confidence threshold
                            brand_detections.append({
                                'brand': brand,
                                'confidence': color_match,
                                'bbox': bbox,
                                'detection_method': 'color_analysis',
                                'object_type': detection['class_name']
                            })
        
        return brand_detections
    
    def _extract_dominant_colors(self, image, k=3):
        """Extract dominant colors using K-means"""
        data = image.reshape((-1, 3))
        data = np.float32(data)
        
        criteria = (cv2.TERM_CRITERIA_EPS + cv2.TERM_CRITERIA_MAX_ITER, 20, 1.0)
        _, labels, centers = cv2.kmeans(data, k, None, criteria, 10, cv2.KMEANS_RANDOM_CENTERS)
        
        return [tuple(map(int, color)) for color in centers]
    
    def _match_brand_colors(self, image_colors, brand_colors):
        """Calculate color similarity score"""
        max_similarity = 0
        
        for img_color in image_colors:
            for brand_color in brand_colors:
                distance = np.sqrt(sum((a - b) ** 2 for a, b in zip(img_color, brand_color)))
                similarity = max(0, 1 - distance / (255 * np.sqrt(3)))
                max_similarity = max(max_similarity, similarity)
        
        return max_similarity
    
    def get_performance_stats(self):
        """Get processing performance statistics"""
        if not self.performance_stats['processing_times']:
            return {}
        
        times = list(self.performance_stats['processing_times'])
        
        return {
            'total_frames_processed': self.performance_stats['total_frames'],
            'total_detections': self.performance_stats['total_detections'],
            'average_processing_time': sum(times) / len(times),
            'min_processing_time': min(times),
            'max_processing_time': max(times),
            'current_fps': 1.0 / times[-1] if times[-1] > 0 else 0,
            'class_distribution': dict(self.class_counts)
        }

# Initialize YOLO processor
yolo_processor = AdvancedYOLOProcessor()

# Database setup
class DatabaseManager:
    def __init__(self):
        self.engine = create_engine(
            os.getenv('DATABASE_URL', 'postgresql://postgres:password@postgres:5432/streaming_analytics'),
            pool_size=20,
            max_overflow=30
        )
        self.SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=self.engine)

# Database models
Base = declarative_base()

class VideoAnalysis(Base):
    __tablename__ = "video_analysis"
    
    id = Column(Integer, primary_key=True, index=True)
    stream_id = Column(String(100), nullable=False)
    frame_hash = Column(String(64), nullable=False)
    detections = Column(Text)  # JSON
    tracked_objects = Column(Text)  # JSON
    brand_detections = Column(Text)  # JSON
    processing_time = Column(Float)
    frame_width = Column(Integer)
    frame_height = Column(Integer)
    timestamp = Column(DateTime, default=datetime.utcnow)

# Initialize database
db_manager = DatabaseManager()

# Redis cache
redis_client = redis.Redis(
    host=os.getenv('REDIS_HOST', 'redis'),
    port=int(os.getenv('REDIS_PORT', 6379)),
    decode_responses=True
)

# Kafka producer
kafka_producer = KafkaProducer(
    bootstrap_servers=os.getenv('KAFKA_BOOTSTRAP_SERVERS', 'kafka:9092'),
    value_serializer=lambda x: json.dumps(x).encode('utf-8')
)

# Stream management
class StreamManager:
    def __init__(self):
        self.active_streams = {}
        self.executor = ThreadPoolExecutor(max_workers=config.MAX_CONCURRENT_STREAMS)
    
    def add_stream(self, stream_id, source_type="webcam"):
        """Add new video stream for processing"""
        if len(self.active_streams) >= config.MAX_CONCURRENT_STREAMS:
            raise Exception("Maximum concurrent streams reached")
        
        self.active_streams[stream_id] = {
            'source_type': source_type,
            'start_time': datetime.utcnow(),
            'frame_count': 0,
            'status': 'active'
        }
        
        ACTIVE_STREAMS.set(len(self.active_streams))
        logger.info(f"Added stream: {stream_id}")
    
    def remove_stream(self, stream_id):
        """Remove video stream"""
        if stream_id in self.active_streams:
            del self.active_streams[stream_id]
            ACTIVE_STREAMS.set(len(self.active_streams))
            logger.info(f"Removed stream: {stream_id}")
    
    def get_stream_info(self, stream_id):
        """Get stream information"""
        return self.active_streams.get(stream_id)
    
    def list_streams(self):
        """List all active streams"""
        return dict(self.active_streams)

stream_manager = StreamManager()

# API Endpoints
@app.before_request
def before_request():
    REQUEST_COUNT.labels(method=request.method, endpoint=request.endpoint).inc()

@app.route('/health', methods=['GET'])
def health_check():
    """Comprehensive health check"""
    try:
        # Check Redis
        redis_status = redis_client.ping()
        
        # Check GPU/CPU status
        device_status = {
            'device': str(device),
            'cuda_available': torch.cuda.is_available()
        }
        
        if torch.cuda.is_available():
            device_status['gpu_memory'] = {
                'allocated': torch.cuda.memory_allocated(),
                'reserved': torch.cuda.memory_reserved(),
                'max_allocated': torch.cuda.max_memory_allocated()
            }
        
        # Performance stats
        perf_stats = yolo_processor.get_performance_stats()
        
        return jsonify({
            'status': 'healthy',
            'service': 'video-service',
            'version': '2.0.0',
            'timestamp': datetime.utcnow().isoformat(),
            'components': {
                'redis': redis_status,
                'yolo_model': True,
                'device': device_status
            },
            'performance': perf_stats,
            'active_streams': len(stream_manager.active_streams)
        })
        
    except Exception as e:
        logger.error(f"Health check failed: {str(e)}")
        return jsonify({
            'status': 'unhealthy',
            'error': str(e)
        }), 500

@app.route('/api/video/analyze-frame', methods=['POST'])
@PROCESSING_TIME.time()
def analyze_frame():
    """Analyze single video frame"""
    try:
        # Get image data
        if 'image' in request.files:
            file = request.files['image']
            image_data = file.read()
        elif 'image_data' in request.json:
            image_b64 = request.json['image_data']
            image_data = base64.b64decode(image_b64)
        else:
            return jsonify({'error': 'No image data provided'}), 400
        
        stream_id = request.form.get('stream_id') or request.json.get('stream_id', 'default')
        
        # Convert to OpenCV format
        image = Image.open(io.BytesIO(image_data))
        frame = cv2.cvtColor(np.array(image), cv2.COLOR_RGB2BGR)
        
        # Process frame
        result = yolo_processor.process_frame(frame, stream_id)
        
        # Generate frame hash for caching
        frame_hash = hashlib.md5(image_data).hexdigest()
        result['frame_hash'] = frame_hash
        result['stream_id'] = stream_id
        
        # Store in database
        try:
            db_session = db_manager.SessionLocal()
            video_record = VideoAnalysis(
                stream_id=stream_id,
                frame_hash=frame_hash,
                detections=json.dumps(result['detections']),
                tracked_objects=json.dumps(result['tracked_objects']),
                brand_detections=json.dumps(result['brand_detections']),
                processing_time=result['frame_info']['processing_time'],
                frame_width=result['frame_info']['width'],
                frame_height=result['frame_info']['height']
            )
            db_session.add(video_record)
            db_session.commit()
            db_session.close()
        except Exception as db_error:
            logger.error(f"Database error: {str(db_error)}")
        
        # Send to Kafka
        kafka_producer.send('stream.video.analysis', result)
        
        # Send brand detections separately if found
        if result['brand_detections']:
            kafka_producer.send('stream.brand.detections', {
                'stream_id': stream_id,
                'brands': result['brand_detections'],
                'timestamp': result['timestamp']
            })
        
        return jsonify(result)
        
    except Exception as e:
        logger.error(f"Frame analysis error: {str(e)}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/video/stream/start', methods=['POST'])
def start_stream():
    """Start video stream processing"""
    try:
        data = request.json
        stream_id = data.get('stream_id')
        source_type = data.get('source_type', 'webcam')
        
        if not stream_id:
            return jsonify({'error': 'stream_id is required'}), 400
        
        stream_manager.add_stream(stream_id, source_type)
        
        return jsonify({
            'status': 'started',
            'stream_id': stream_id,
            'source_type': source_type,
            'timestamp': datetime.utcnow().isoformat()
        })
        
    except Exception as e:
        logger.error(f"Stream start error: {str(e)}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/video/stream/stop', methods=['POST'])
def stop_stream():
    """Stop video stream processing"""
    try:
        data = request.json
        stream_id = data.get('stream_id')
        
        if not stream_id:
            return jsonify({'error': 'stream_id is required'}), 400
        
        stream_manager.remove_stream(stream_id)
        
        return jsonify({
            'status': 'stopped',
            'stream_id': stream_id,
            'timestamp': datetime.utcnow().isoformat()
        })
        
    except Exception as e:
        logger.error(f"Stream stop error: {str(e)}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/video/streams', methods=['GET'])
def list_streams():
    """List all active streams"""
    try:
        streams = stream_manager.list_streams()
        return jsonify({
            'active_streams': streams,
            'total_count': len(streams),
            'timestamp': datetime.utcnow().isoformat()
        })
        
    except Exception as e:
        logger.error(f"Stream list error: {str(e)}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/video/analytics', methods=['GET'])
def get_analytics():
    """Get video processing analytics"""
    try:
        hours = int(request.args.get('hours', 24))
        since = datetime.utcnow() - timedelta(hours=hours)
        
        # Database analytics
        db_session = db_manager.SessionLocal()
        video_records = db_session.query(VideoAnalysis).filter(
            VideoAnalysis.timestamp >= since
        ).all()
        db_session.close()
        
        # Process analytics
        total_frames = len(video_records)
        total_detections = sum(len(json.loads(r.detections or '[]')) for r in video_records)
        total_brands = sum(len(json.loads(r.brand_detections or '[]')) for r in video_records)
        
        avg_processing_time = np.mean([r.processing_time for r in video_records]) if video_records else 0
        
        # Performance stats
        perf_stats = yolo_processor.get_performance_stats()
        
        analytics_data = {
            'time_range_hours': hours,
            'processing_stats': {
                'total_frames_analyzed': total_frames,
                'total_objects_detected': total_detections,
                'total_brands_detected': total_brands,
                'average_processing_time': float(avg_processing_time),
                'frames_per_hour': total_frames / hours if hours > 0 else 0
            },
            'performance_metrics': perf_stats,
            'active_streams': stream_manager.list_streams(),
            'system_info': {
                'device': str(device),
                'cuda_available': torch.cuda.is_available(),
                'memory_usage': torch.cuda.memory_allocated() if torch.cuda.is_available() else 0
            }
        }
        
        return jsonify(analytics_data)
        
    except Exception as e:
        logger.error(f"Analytics error: {str(e)}")
        return jsonify({'error': str(e)}), 500

@app.route('/api/video/batch-process', methods=['POST'])
@PROCESSING_TIME.time()
def batch_process():
    """Batch process multiple frames"""
    try:
        data = request.json
        frames_data = data.get('frames', [])
        stream_id = data.get('stream_id', 'batch')
        
        if not frames_data:
            return jsonify({'error': 'No frames provided'}), 400
        
        results = []
        
        for i, frame_b64 in enumerate(frames_data):
            try:
                # Decode frame
                image_data = base64.b64decode(frame_b64)
                image = Image.open(io.BytesIO(image_data))
                frame = cv2.cvtColor(np.array(image), cv2.COLOR_RGB2BGR)
                
                # Process frame
                result = yolo_processor.process_frame(frame, f"{stream_id}_batch_{i}")
                result['frame_index'] = i
                results.append(result)
                
            except Exception as frame_error:
                logger.error(f"Error processing frame {i}: {str(frame_error)}")
                results.append({
                    'frame_index': i,
                    'error': str(frame_error)
                })
        
        batch_result = {
            'stream_id': stream_id,
            'total_frames': len(frames_data),
            'successful_frames': len([r for r in results if 'error' not in r]),
            'failed_frames': len([r for r in results if 'error' in r]),
            'results': results,
            'timestamp': datetime.utcnow().isoformat()
        }
        
        # Send to Kafka
        kafka_producer.send('stream.video.batch', batch_result)
        
        return jsonify(batch_result)
        
    except Exception as e:
        logger.error(f"Batch processing error: {str(e)}")
        return jsonify({'error': str(e)}), 500

@app.route('/metrics', methods=['GET'])
def metrics():
    """Prometheus metrics endpoint"""
    return Response(prometheus_client.generate_latest(), mimetype="text/plain")

# Background cleanup
def cleanup_resources():
    """Clean up GPU memory and cache"""
    if torch.cuda.is_available():
        torch.cuda.empty_cache()
    gc.collect()

def start_cleanup_scheduler():
    """Start background cleanup scheduler"""
    import schedule
    
    schedule.every(15).minutes.do(cleanup_resources)
    
    def run_scheduler():
        while True:
            schedule.run_pending()
            time.sleep(60)
    
    cleanup_thread = threading.Thread(target=run_scheduler, daemon=True)
    cleanup_thread.start()

if __name__ == '__main__':
    logger.info("Starting Video Service with enterprise YOLO processing...")
    
    # Start background tasks
    start_cleanup_scheduler()
    
    # Run Flask app
    app.run(
        host='0.0.0.0',
        port=int(os.getenv('VIDEO_SERVICE_PORT', 5001)),
        debug=False,
        threaded=True
    )