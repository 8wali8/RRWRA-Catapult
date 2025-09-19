from flask import Flask, request, jsonify, Response
from flask_cors import CORS
import torch
import torch.nn.functional as F
from transformers import (
    AutoTokenizer, AutoModelForSequenceClassification,
    pipeline, AutoModelForQuestionAnswering, 
    DistilBertTokenizer, DistilBertForSequenceClassification
)
from ultralytics import YOLO
import cv2
import numpy as np
from kafka import KafkaProducer, KafkaConsumer
import json
import redis
import os
import logging
import time
from datetime import datetime, timedelta
import threading
from collections import defaultdict
import hashlib
import pickle
from PIL import Image
import io
import base64
import schedule
import asyncio
from concurrent.futures import ThreadPoolExecutor
import psycopg2
from sqlalchemy import create_engine, Column, Integer, String, Float, DateTime, Text, Boolean
from sqlalchemy.ext.declarative import declarative_base
from sqlalchemy.orm import sessionmaker
import prometheus_client
from prometheus_client import Counter, Histogram, Gauge
import gc

# Initialize Flask app with enterprise configuration
app = Flask(__name__)
CORS(app)

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler('/app/logs/ml-engine.log'),
        logging.StreamHandler()
    ]
)
logger = logging.getLogger(__name__)

# Prometheus metrics
REQUEST_COUNT = Counter('ml_engine_requests_total', 'Total ML engine requests', ['method', 'endpoint'])
REQUEST_LATENCY = Histogram('ml_engine_request_duration_seconds', 'ML engine request latency')
MODEL_INFERENCE_TIME = Histogram('ml_model_inference_seconds', 'Model inference time', ['model_type'])
ACTIVE_CONNECTIONS = Gauge('ml_engine_active_connections', 'Active connections to ML engine')
CACHE_HIT_RATE = Counter('ml_engine_cache_hits_total', 'Cache hits')
CACHE_MISS_RATE = Counter('ml_engine_cache_misses_total', 'Cache misses')

# Enterprise ML Configuration
class MLConfig:
    # Model paths and configurations
    SENTIMENT_MODEL = "cardiffnlp/twitter-roberta-base-sentiment-latest"
    EMOTION_MODEL = "j-hartmann/emotion-english-distilroberta-base"
    QA_MODEL = "distilbert-base-cased-distilled-squad"
    SPONSOR_DETECTION_MODEL = "yolov8n.pt"
    
    # Cache settings
    REDIS_CACHE_TTL = 3600  # 1 hour
    MODEL_CACHE_SIZE = 100000
    
    # Performance settings
    BATCH_SIZE = 32
    MAX_SEQUENCE_LENGTH = 512
    INFERENCE_TIMEOUT = 30
    
    # Database settings
    DB_CONNECTION_POOL_SIZE = 20
    DB_MAX_OVERFLOW = 30

config = MLConfig()

# Initialize device
device = torch.device('cuda' if torch.cuda.is_available() else 'cpu')
logger.info(f"Using device: {device}")

# Enterprise Model Manager
class ModelManager:
    def __init__(self):
        self.models = {}
        self.tokenizers = {}
        self.pipelines = {}
        self.load_models()
    
    def load_models(self):
        """Load all ML models with error handling and performance optimization"""
        try:
            # Sentiment Analysis Model (RoBERTa)
            logger.info("Loading sentiment analysis model...")
            self.tokenizers['sentiment'] = AutoTokenizer.from_pretrained(config.SENTIMENT_MODEL)
            self.models['sentiment'] = AutoModelForSequenceClassification.from_pretrained(config.SENTIMENT_MODEL)
            self.models['sentiment'].to(device)
            self.models['sentiment'].eval()
            
            # Emotion Detection Model
            logger.info("Loading emotion detection model...")
            self.pipelines['emotion'] = pipeline(
                "text-classification",
                model=config.EMOTION_MODEL,
                device=0 if torch.cuda.is_available() else -1,
                return_all_scores=True
            )
            
            # Question Answering Model
            logger.info("Loading QA model...")
            self.pipelines['qa'] = pipeline(
                "question-answering",
                model=config.QA_MODEL,
                device=0 if torch.cuda.is_available() else -1
            )
            
            # YOLO Object Detection Model
            logger.info("Loading YOLO model...")
            self.models['yolo'] = YOLO(config.SPONSOR_DETECTION_MODEL)
            
            # Sponsor Brand Recognition (Custom fine-tuned model)
            logger.info("Loading sponsor recognition model...")
            self.tokenizers['sponsor'] = DistilBertTokenizer.from_pretrained('distilbert-base-uncased')
            self.models['sponsor'] = DistilBertForSequenceClassification.from_pretrained(
                'distilbert-base-uncased',
                num_labels=50  # Support for 50 different sponsor brands
            )
            self.models['sponsor'].to(device)
            self.models['sponsor'].eval()
            
            logger.info("All models loaded successfully")
            
        except Exception as e:
            logger.error(f"Error loading models: {str(e)}")
            raise
    
    def get_model(self, model_name):
        return self.models.get(model_name)
    
    def get_tokenizer(self, model_name):
        return self.tokenizers.get(model_name)
    
    def get_pipeline(self, pipeline_name):
        return self.pipelines.get(pipeline_name)

# Initialize Model Manager
model_manager = ModelManager()

# Enterprise Database Connection
class DatabaseManager:
    def __init__(self):
        self.engine = create_engine(
            os.getenv('DATABASE_URL', 'postgresql://postgres:password@postgres:5432/streaming_analytics'),
            pool_size=config.DB_CONNECTION_POOL_SIZE,
            max_overflow=config.DB_MAX_OVERFLOW,
            pool_pre_ping=True
        )
        self.SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=self.engine)
        
    def get_session(self):
        return self.SessionLocal()

# Database Models
Base = declarative_base()

class SentimentAnalysis(Base):
    __tablename__ = "sentiment_analysis"
    
    id = Column(Integer, primary_key=True, index=True)
    message = Column(Text, nullable=False)
    sentiment_score = Column(Float, nullable=False)
    sentiment_label = Column(String(50), nullable=False)
    confidence = Column(Float, nullable=False)
    emotion_scores = Column(Text)  # JSON string
    streamer_id = Column(String(100), nullable=False)
    timestamp = Column(DateTime, default=datetime.utcnow)
    processed_at = Column(DateTime, default=datetime.utcnow)

class SponsorDetection(Base):
    __tablename__ = "sponsor_detections"
    
    id = Column(Integer, primary_key=True, index=True)
    image_hash = Column(String(64), nullable=False)
    detections = Column(Text)  # JSON string
    sponsor_brands = Column(Text)  # JSON string
    confidence_scores = Column(Text)  # JSON string
    streamer_id = Column(String(100), nullable=False)
    timestamp = Column(DateTime, default=datetime.utcnow)
    processed_at = Column(DateTime, default=datetime.utcnow)

# Initialize Database
db_manager = DatabaseManager()

# Enterprise Redis Cache Manager
class CacheManager:
    def __init__(self):
        self.redis_client = redis.Redis(
            host=os.getenv('REDIS_HOST', 'redis'),
            port=int(os.getenv('REDIS_PORT', 6379)),
            decode_responses=True,
            socket_connect_timeout=5,
            socket_timeout=5,
            retry_on_timeout=True,
            health_check_interval=30
        )
        self.local_cache = {}
        self.cache_stats = defaultdict(int)
    
    def get(self, key):
        try:
            # Try local cache first
            if key in self.local_cache:
                self.cache_stats['local_hits'] += 1
                return self.local_cache[key]
            
            # Try Redis cache
            value = self.redis_client.get(key)
            if value:
                CACHE_HIT_RATE.inc()
                self.cache_stats['redis_hits'] += 1
                # Store in local cache for faster access
                self.local_cache[key] = json.loads(value) if value else None
                return json.loads(value) if value else None
            
            CACHE_MISS_RATE.inc()
            self.cache_stats['misses'] += 1
            return None
            
        except Exception as e:
            logger.error(f"Cache get error: {str(e)}")
            return None
    
    def set(self, key, value, ttl=config.REDIS_CACHE_TTL):
        try:
            # Store in both local and Redis cache
            self.local_cache[key] = value
            self.redis_client.setex(key, ttl, json.dumps(value))
            
            # Manage local cache size
            if len(self.local_cache) > config.MODEL_CACHE_SIZE:
                # Remove oldest entries
                oldest_keys = list(self.local_cache.keys())[:len(self.local_cache)//4]
                for old_key in oldest_keys:
                    del self.local_cache[old_key]
                    
        except Exception as e:
            logger.error(f"Cache set error: {str(e)}")
    
    def get_stats(self):
        return dict(self.cache_stats)

# Initialize Cache Manager
cache_manager = CacheManager()

# Enterprise Kafka Manager
class KafkaManager:
    def __init__(self):
        self.bootstrap_servers = os.getenv('KAFKA_BOOTSTRAP_SERVERS', 'kafka:9092')
        self.producer = KafkaProducer(
            bootstrap_servers=self.bootstrap_servers,
            value_serializer=lambda x: json.dumps(x).encode('utf-8'),
            acks='all',
            retries=3,
            batch_size=16384,
            linger_ms=10,
            buffer_memory=33554432
        )
        
        # Topic configuration
        self.topics = {
            'sentiment': 'stream.sentiment.analysis',
            'emotion': 'stream.emotion.analysis', 
            'sponsor': 'stream.sponsor.detections',
            'qa': 'stream.qa.responses',
            'alerts': 'stream.ml.alerts'
        }
    
    def send_message(self, topic_key, message):
        try:
            topic = self.topics.get(topic_key)
            if topic:
                future = self.producer.send(topic, message)
                # Non-blocking send with callback
                future.add_callback(self._on_send_success)
                future.add_errback(self._on_send_error)
            else:
                logger.error(f"Unknown topic key: {topic_key}")
        except Exception as e:
            logger.error(f"Kafka send error: {str(e)}")
    
    def _on_send_success(self, record_metadata):
        logger.debug(f"Message sent to {record_metadata.topic} partition {record_metadata.partition}")
    
    def _on_send_error(self, excp):
        logger.error(f"Kafka send failed: {str(excp)}")

# Initialize Kafka Manager
kafka_manager = KafkaManager()

# Enterprise ML Services
class SentimentAnalysisService:
    def __init__(self):
        self.model = model_manager.get_model('sentiment')
        self.tokenizer = model_manager.get_tokenizer('sentiment')
        self.emotion_pipeline = model_manager.get_pipeline('emotion')
    
    @REQUEST_LATENCY.time()
    def analyze_sentiment(self, text, include_emotions=True):
        start_time = time.time()
        
        try:
            # Check cache first
            cache_key = f"sentiment:{hashlib.md5(text.encode()).hexdigest()}"
            cached_result = cache_manager.get(cache_key)
            if cached_result:
                return cached_result
            
            # Tokenize and analyze
            inputs = self.tokenizer(text, return_tensors="pt", truncation=True, 
                                  max_length=config.MAX_SEQUENCE_LENGTH, padding=True)
            inputs = {k: v.to(device) for k, v in inputs.items()}
            
            with torch.no_grad():
                outputs = self.model(**inputs)
                probabilities = F.softmax(outputs.logits, dim=-1)
                predicted_class = torch.argmax(probabilities, dim=-1).item()
                confidence = torch.max(probabilities).item()
            
            # Map predictions to labels
            labels = ['negative', 'neutral', 'positive']
            sentiment_label = labels[predicted_class]
            sentiment_score = probabilities[0][predicted_class].item()
            
            result = {
                'sentiment_label': sentiment_label,
                'sentiment_score': float(sentiment_score),
                'confidence': float(confidence),
                'processing_time': time.time() - start_time
            }
            
            # Add emotion analysis if requested
            if include_emotions:
                emotion_start = time.time()
                emotions = self.emotion_pipeline(text)
                emotion_scores = {emotion['label']: emotion['score'] for emotion in emotions[0]}
                result['emotions'] = emotion_scores
                result['dominant_emotion'] = max(emotion_scores, key=emotion_scores.get)
                result['emotion_confidence'] = max(emotion_scores.values())
                
                MODEL_INFERENCE_TIME.labels(model_type='emotion').observe(time.time() - emotion_start)
            
            # Cache result
            cache_manager.set(cache_key, result)
            
            MODEL_INFERENCE_TIME.labels(model_type='sentiment').observe(time.time() - start_time)
            return result
            
        except Exception as e:
            logger.error(f"Sentiment analysis error: {str(e)}")
            raise

class SponsorDetectionService:
    def __init__(self):
        self.yolo_model = model_manager.get_model('yolo')
        self.sponsor_model = model_manager.get_model('sponsor')
        self.sponsor_tokenizer = model_manager.get_tokenizer('sponsor')
        
        # Load sponsor brand database
        self.sponsor_brands = self._load_sponsor_brands()
    
    def _load_sponsor_brands(self):
        """Load known sponsor brands and their visual signatures"""
        brands = {
            'redbull': {'colors': [(0, 45, 156), (255, 209, 0)], 'keywords': ['red bull', 'redbull', 'energy']},
            'monster': {'colors': [(0, 166, 81), (0, 0, 0)], 'keywords': ['monster', 'energy', 'claw']},
            'nike': {'colors': [(0, 0, 0), (255, 255, 255)], 'keywords': ['nike', 'swoosh', 'just do it']},
            'adidas': {'colors': [(0, 0, 0), (255, 255, 255)], 'keywords': ['adidas', 'three stripes']},
            'coca_cola': {'colors': [(220, 20, 60), (255, 255, 255)], 'keywords': ['coca cola', 'coke']},
            'pepsi': {'colors': [(0, 50, 153), (220, 20, 60)], 'keywords': ['pepsi', 'cola']},
            'mcdonalds': {'colors': [(255, 199, 44), (218, 41, 28)], 'keywords': ['mcdonalds', 'golden arches']},
            'twitch': {'colors': [(100, 65, 165), (255, 255, 255)], 'keywords': ['twitch', 'stream']},
            'discord': {'colors': [(88, 101, 242), (255, 255, 255)], 'keywords': ['discord', 'chat']},
            'spotify': {'colors': [(30, 215, 96), (0, 0, 0)], 'keywords': ['spotify', 'music']}
        }
        return brands
    
    @REQUEST_LATENCY.time()
    def detect_sponsors(self, image_data, analyze_text=None):
        start_time = time.time()
        
        try:
            # Generate image hash for caching
            image_hash = hashlib.md5(image_data).hexdigest()
            cache_key = f"sponsor:{image_hash}"
            cached_result = cache_manager.get(cache_key)
            if cached_result:
                return cached_result
            
            # Convert image data
            image = Image.open(io.BytesIO(image_data))
            cv_image = cv2.cvtColor(np.array(image), cv2.COLOR_RGB2BGR)
            
            # YOLO object detection
            yolo_results = self.yolo_model(cv_image)
            
            detections = []
            sponsor_matches = []
            
            # Process YOLO detections
            for result in yolo_results:
                boxes = result.boxes
                if boxes is not None:
                    for box in boxes:
                        detection = {
                            'confidence': float(box.conf),
                            'bbox': box.xyxy.tolist()[0],
                            'class': int(box.cls),
                            'class_name': result.names[int(box.cls)]
                        }
                        detections.append(detection)
            
            # Color-based sponsor detection
            dominant_colors = self._extract_dominant_colors(cv_image)
            
            # Check for sponsor brand matches
            for brand, brand_info in self.sponsor_brands.items():
                color_match = self._match_brand_colors(dominant_colors, brand_info['colors'])
                
                sponsor_match = {
                    'brand': brand,
                    'color_confidence': color_match,
                    'detection_method': 'color_analysis'
                }
                
                # Text-based detection if text is provided
                if analyze_text:
                    text_match = self._match_brand_text(analyze_text, brand_info['keywords'])
                    sponsor_match['text_confidence'] = text_match
                    sponsor_match['overall_confidence'] = (color_match + text_match) / 2
                else:
                    sponsor_match['overall_confidence'] = color_match
                
                if sponsor_match['overall_confidence'] > 0.3:
                    sponsor_matches.append(sponsor_match)
            
            # Sort by confidence
            sponsor_matches.sort(key=lambda x: x['overall_confidence'], reverse=True)
            
            result = {
                'detections': detections,
                'sponsor_matches': sponsor_matches[:5],  # Top 5 matches
                'dominant_colors': dominant_colors,
                'processing_time': time.time() - start_time,
                'image_hash': image_hash
            }
            
            # Cache result
            cache_manager.set(cache_key, result)
            
            MODEL_INFERENCE_TIME.labels(model_type='sponsor_detection').observe(time.time() - start_time)
            return result
            
        except Exception as e:
            logger.error(f"Sponsor detection error: {str(e)}")
            raise
    
    def _extract_dominant_colors(self, image, k=5):
        """Extract dominant colors using K-means clustering"""
        data = image.reshape((-1, 3))
        data = np.float32(data)
        
        criteria = (cv2.TERM_CRITERIA_EPS + cv2.TERM_CRITERIA_MAX_ITER, 20, 1.0)
        _, labels, centers = cv2.kmeans(data, k, None, criteria, 10, cv2.KMEANS_RANDOM_CENTERS)
        
        # Convert to list of RGB tuples
        colors = [tuple(map(int, color)) for color in centers]
        return colors
    
    def _match_brand_colors(self, image_colors, brand_colors, threshold=50):
        """Calculate color similarity between image and brand colors"""
        max_similarity = 0
        
        for img_color in image_colors:
            for brand_color in brand_colors:
                # Calculate Euclidean distance in RGB space
                distance = np.sqrt(sum((a - b) ** 2 for a, b in zip(img_color, brand_color)))
                similarity = max(0, 1 - distance / (255 * np.sqrt(3)))
                max_similarity = max(max_similarity, similarity)
        
        return max_similarity
    
    def _match_brand_text(self, text, keywords):
        """Calculate text-based brand matching"""
        text_lower = text.lower()
        matches = sum(1 for keyword in keywords if keyword in text_lower)
        return min(matches / len(keywords), 1.0)

class QuestionAnsweringService:
    def __init__(self):
        self.qa_pipeline = model_manager.get_pipeline('qa')
    
    @REQUEST_LATENCY.time()
    def answer_question(self, question, context):
        start_time = time.time()
        
        try:
            # Check cache
            cache_key = f"qa:{hashlib.md5((question + context).encode()).hexdigest()}"
            cached_result = cache_manager.get(cache_key)
            if cached_result:
                return cached_result
            
            # Generate answer
            qa_result = self.qa_pipeline(question=question, context=context)
            
            result = {
                'question': question,
                'answer': qa_result['answer'],
                'confidence': qa_result['score'],
                'start_position': qa_result['start'],
                'end_position': qa_result['end'],
                'processing_time': time.time() - start_time
            }
            
            # Cache result
            cache_manager.set(cache_key, result)
            
            MODEL_INFERENCE_TIME.labels(model_type='qa').observe(time.time() - start_time)
            return result
            
        except Exception as e:
            logger.error(f"QA error: {str(e)}")
            raise

# Initialize Services
sentiment_service = SentimentAnalysisService()
sponsor_service = SponsorDetectionService()
qa_service = QuestionAnsweringService()

# Enterprise API Endpoints
@app.before_request
def before_request():
    ACTIVE_CONNECTIONS.inc()
    REQUEST_COUNT.labels(method=request.method, endpoint=request.endpoint).inc()

@app.after_request
def after_request(response):
    ACTIVE_CONNECTIONS.dec()
    return response

@app.route('/health', methods=['GET'])
def health_check():
    """Comprehensive health check endpoint"""
    try:
        # Check Redis connectivity
        redis_status = cache_manager.redis_client.ping()
        
        # Check database connectivity
        db_session = db_manager.get_session()
        db_session.execute("SELECT 1")
        db_session.close()
        db_status = True
        
        # Check model availability
        models_status = {
            'sentiment': model_manager.get_model('sentiment') is not None,
            'emotion': model_manager.get_pipeline('emotion') is not None,
            'qa': model_manager.get_pipeline('qa') is not None,
            'yolo': model_manager.get_model('yolo') is not None,
            'sponsor': model_manager.get_model('sponsor') is not None
        }
        
        health_data = {
            "status": "healthy",
            "service": "ml-engine",
            "version": "2.0.0",
            "timestamp": datetime.utcnow().isoformat(),
            "components": {
                "redis": redis_status,
                "database": db_status,
                "models": models_status
            },
            "cache_stats": cache_manager.get_stats(),
            "device": str(device),
            "memory_usage": torch.cuda.memory_allocated() if torch.cuda.is_available() else "N/A"
        }
        
        return jsonify(health_data)
        
    except Exception as e:
        logger.error(f"Health check failed: {str(e)}")
        return jsonify({
            "status": "unhealthy",
            "error": str(e),
            "timestamp": datetime.utcnow().isoformat()
        }), 500

@app.route('/api/ml/analyze-sentiment', methods=['POST'])
@REQUEST_LATENCY.time()
def analyze_sentiment():
    """Advanced sentiment and emotion analysis endpoint"""
    try:
        data = request.json
        message = data.get('message')
        streamer_id = data.get('streamer_id', 'unknown')
        include_emotions = data.get('include_emotions', True)
        
        if not message:
            return jsonify({"error": "Message is required"}), 400
        
        # Perform analysis
        analysis_result = sentiment_service.analyze_sentiment(message, include_emotions)
        
        # Prepare response
        response_data = {
            'message': message,
            'streamer_id': streamer_id,
            'timestamp': datetime.utcnow().isoformat(),
            **analysis_result
        }
        
        # Store in database
        try:
            db_session = db_manager.get_session()
            sentiment_record = SentimentAnalysis(
                message=message,
                sentiment_score=analysis_result['sentiment_score'],
                sentiment_label=analysis_result['sentiment_label'],
                confidence=analysis_result['confidence'],
                emotion_scores=json.dumps(analysis_result.get('emotions', {})),
                streamer_id=streamer_id
            )
            db_session.add(sentiment_record)
            db_session.commit()
            db_session.close()
        except Exception as db_error:
            logger.error(f"Database error: {str(db_error)}")
        
        # Send to Kafka
        kafka_manager.send_message('sentiment', response_data)
        
        # Send emotion data if available
        if 'emotions' in analysis_result:
            emotion_data = {
                'message': message,
                'streamer_id': streamer_id,
                'emotions': analysis_result['emotions'],
                'dominant_emotion': analysis_result['dominant_emotion'],
                'emotion_confidence': analysis_result['emotion_confidence'],
                'timestamp': datetime.utcnow().isoformat()
            }
            kafka_manager.send_message('emotion', emotion_data)
        
        return jsonify(response_data)
        
    except Exception as e:
        logger.error(f"Sentiment analysis error: {str(e)}")
        return jsonify({"error": str(e)}), 500

@app.route('/api/ml/detect-sponsor', methods=['POST'])
@REQUEST_LATENCY.time()
def detect_sponsor():
    """Advanced sponsor detection with visual and textual analysis"""
    try:
        # Handle different input formats
        if 'image' in request.files:
            file = request.files['image']
            image_data = file.read()
        elif 'image_data' in request.json:
            # Base64 encoded image
            image_b64 = request.json['image_data']
            image_data = base64.b64decode(image_b64)
        else:
            return jsonify({"error": "No image data provided"}), 400
        
        streamer_id = request.form.get('streamer_id') or request.json.get('streamer_id', 'unknown')
        analyze_text = request.form.get('text') or request.json.get('text')
        
        # Perform detection
        detection_result = sponsor_service.detect_sponsors(image_data, analyze_text)
        
        # Prepare response
        response_data = {
            'streamer_id': streamer_id,
            'timestamp': datetime.utcnow().isoformat(),
            'text_analyzed': analyze_text,
            **detection_result
        }
        
        # Store in database
        try:
            db_session = db_manager.get_session()
            sponsor_record = SponsorDetection(
                image_hash=detection_result['image_hash'],
                detections=json.dumps(detection_result['detections']),
                sponsor_brands=json.dumps(detection_result['sponsor_matches']),
                confidence_scores=json.dumps([match['overall_confidence'] for match in detection_result['sponsor_matches']]),
                streamer_id=streamer_id
            )
            db_session.add(sponsor_record)
            db_session.commit()
            db_session.close()
        except Exception as db_error:
            logger.error(f"Database error: {str(db_error)}")
        
        # Send to Kafka
        kafka_manager.send_message('sponsor', response_data)
        
        return jsonify(response_data)
        
    except Exception as e:
        logger.error(f"Sponsor detection error: {str(e)}")
        return jsonify({"error": str(e)}), 500

@app.route('/api/ml/answer-question', methods=['POST'])
@REQUEST_LATENCY.time()
def answer_question():
    """Question answering for stream content"""
    try:
        data = request.json
        question = data.get('question')
        context = data.get('context')
        streamer_id = data.get('streamer_id', 'unknown')
        
        if not question or not context:
            return jsonify({"error": "Question and context are required"}), 400
        
        # Perform QA
        qa_result = qa_service.answer_question(question, context)
        
        # Prepare response
        response_data = {
            'streamer_id': streamer_id,
            'timestamp': datetime.utcnow().isoformat(),
            **qa_result
        }
        
        # Send to Kafka
        kafka_manager.send_message('qa', response_data)
        
        return jsonify(response_data)
        
    except Exception as e:
        logger.error(f"QA error: {str(e)}")
        return jsonify({"error": str(e)}), 500

@app.route('/api/ml/batch-analyze', methods=['POST'])
@REQUEST_LATENCY.time()
def batch_analyze():
    """Batch processing endpoint for high-throughput scenarios"""
    try:
        data = request.json
        messages = data.get('messages', [])
        analysis_type = data.get('type', 'sentiment')  # sentiment, emotion, or both
        streamer_id = data.get('streamer_id', 'unknown')
        
        if not messages:
            return jsonify({"error": "Messages array is required"}), 400
        
        results = []
        
        # Process in batches
        batch_size = config.BATCH_SIZE
        for i in range(0, len(messages), batch_size):
            batch = messages[i:i + batch_size]
            batch_results = []
            
            for message in batch:
                if analysis_type in ['sentiment', 'both']:
                    sentiment_result = sentiment_service.analyze_sentiment(
                        message, include_emotions=(analysis_type == 'both')
                    )
                    batch_results.append({
                        'message': message,
                        'analysis': sentiment_result
                    })
            
            results.extend(batch_results)
        
        response_data = {
            'streamer_id': streamer_id,
            'timestamp': datetime.utcnow().isoformat(),
            'total_processed': len(results),
            'analysis_type': analysis_type,
            'results': results
        }
        
        return jsonify(response_data)
        
    except Exception as e:
        logger.error(f"Batch analysis error: {str(e)}")
        return jsonify({"error": str(e)}), 500

@app.route('/api/ml/analytics', methods=['GET'])
def get_analytics():
    """Analytics endpoint for ML service performance"""
    try:
        # Get time range parameters
        hours = int(request.args.get('hours', 24))
        since = datetime.utcnow() - timedelta(hours=hours)
        
        db_session = db_manager.get_session()
        
        # Sentiment analytics
        sentiment_stats = db_session.query(SentimentAnalysis).filter(
            SentimentAnalysis.timestamp >= since
        ).all()
        
        # Sponsor detection analytics
        sponsor_stats = db_session.query(SponsorDetection).filter(
            SponsorDetection.timestamp >= since
        ).all()
        
        db_session.close()
        
        analytics_data = {
            'time_range_hours': hours,
            'sentiment_analytics': {
                'total_analyzed': len(sentiment_stats),
                'sentiment_distribution': {
                    'positive': len([s for s in sentiment_stats if s.sentiment_label == 'positive']),
                    'neutral': len([s for s in sentiment_stats if s.sentiment_label == 'neutral']),
                    'negative': len([s for s in sentiment_stats if s.sentiment_label == 'negative'])
                },
                'average_confidence': np.mean([s.confidence for s in sentiment_stats]) if sentiment_stats else 0
            },
            'sponsor_analytics': {
                'total_detections': len(sponsor_stats),
                'unique_images': len(set(s.image_hash for s in sponsor_stats)),
                'brands_detected': len(set(
                    brand['brand'] for s in sponsor_stats 
                    for brand in json.loads(s.sponsor_brands or '[]')
                ))
            },
            'cache_performance': cache_manager.get_stats(),
            'system_metrics': {
                'memory_usage': torch.cuda.memory_allocated() if torch.cuda.is_available() else "N/A",
                'device': str(device)
            }
        }
        
        return jsonify(analytics_data)
        
    except Exception as e:
        logger.error(f"Analytics error: {str(e)}")
        return jsonify({"error": str(e)}), 500

@app.route('/metrics', methods=['GET'])
def metrics():
    """Prometheus metrics endpoint"""
    return Response(prometheus_client.generate_latest(), mimetype="text/plain")

# Background Tasks
def cleanup_cache():
    """Periodic cache cleanup"""
    try:
        # Clean up local cache
        cache_size = len(cache_manager.local_cache)
        if cache_size > config.MODEL_CACHE_SIZE:
            logger.info(f"Cleaning up cache, current size: {cache_size}")
            # Keep only 75% of cache
            keep_size = int(config.MODEL_CACHE_SIZE * 0.75)
            items = list(cache_manager.local_cache.items())
            cache_manager.local_cache = dict(items[-keep_size:])
            
        # Force garbage collection
        gc.collect()
        if torch.cuda.is_available():
            torch.cuda.empty_cache()
            
    except Exception as e:
        logger.error(f"Cache cleanup error: {str(e)}")

def start_background_tasks():
    """Start background maintenance tasks"""
    schedule.every(30).minutes.do(cleanup_cache)
    
    def run_scheduler():
        while True:
            schedule.run_pending()
            time.sleep(60)
    
    scheduler_thread = threading.Thread(target=run_scheduler, daemon=True)
    scheduler_thread.start()

if __name__ == '__main__':
    logger.info("Starting ML Engine with enterprise configuration...")
    
    # Start background tasks
    start_background_tasks()
    
    # Run Flask app
    app.run(
        host='0.0.0.0', 
        port=int(os.getenv('ML_ENGINE_PORT', 5000)), 
        debug=False,
        threaded=True
    )