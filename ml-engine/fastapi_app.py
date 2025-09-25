"""
Enhanced ML Engine with FastAPI and Async Processing
Performance improvements and modern async architecture
"""

from fastapi import FastAPI, HTTPException, BackgroundTasks
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
import asyncio
import uvloop  # High-performance event loop
from contextlib import asynccontextmanager
import torch
from transformers import pipeline
import numpy as np
from typing import List, Dict, Optional, Union
import logging
from datetime import datetime
import redis.asyncio as redis
from sqlalchemy.ext.asyncio import create_async_engine, AsyncSession
from sqlalchemy.orm import sessionmaker
import aiofiles
import httpx
from concurrent.futures import ThreadPoolExecutor
import multiprocessing as mp

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Pydantic Models for Request/Response
class SentimentRequest(BaseModel):
    text: str = Field(..., min_length=1, max_length=1000)
    include_emotions: bool = True
    
class SentimentResponse(BaseModel):
    sentiment: str
    confidence: float
    emotions: Optional[Dict[str, float]] = None
    processing_time: float

class BatchSentimentRequest(BaseModel):
    texts: List[str] = Field(..., max_items=100)
    include_emotions: bool = True

class SponsorDetectionRequest(BaseModel):
    image_data: str  # Base64 encoded
    confidence_threshold: float = 0.5

# Global variables for models and connections
models = {}
redis_client = None
db_session = None
thread_pool = None

@asynccontextmanager
async def lifespan(app: FastAPI):
    """Manage application lifecycle"""
    # Startup
    logger.info("Starting ML Engine with FastAPI...")
    await load_models()
    await setup_connections()
    
    yield
    
    # Shutdown
    logger.info("Shutting down ML Engine...")
    await cleanup_resources()

# Initialize FastAPI with lifespan
app = FastAPI(
    title="StreamSense ML Engine",
    description="High-performance ML microservice with async processing",
    version="2.0.0",
    lifespan=lifespan
)

# Add CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

async def load_models():
    """Load ML models asynchronously"""
    global models, thread_pool
    
    # Use process pool for CPU-intensive model loading
    thread_pool = ThreadPoolExecutor(max_workers=mp.cpu_count())
    
    try:
        # Load models in parallel
        sentiment_task = asyncio.create_task(
            asyncio.get_event_loop().run_in_executor(
                thread_pool, 
                load_sentiment_model
            )
        )
        
        emotion_task = asyncio.create_task(
            asyncio.get_event_loop().run_in_executor(
                thread_pool,
                load_emotion_model
            )
        )
        
        yolo_task = asyncio.create_task(
            asyncio.get_event_loop().run_in_executor(
                thread_pool,
                load_yolo_model
            )
        )
        
        # Wait for all models to load
        models['sentiment'] = await sentiment_task
        models['emotion'] = await emotion_task  
        models['yolo'] = await yolo_task
        
        logger.info("All models loaded successfully")
        
    except Exception as e:
        logger.error(f"Error loading models: {e}")
        raise

def load_sentiment_model():
    """Load sentiment analysis model"""
    return pipeline(
        "sentiment-analysis",
        model="cardiffnlp/twitter-roberta-base-sentiment-latest",
        device=0 if torch.cuda.is_available() else -1,
        batch_size=32,
        max_length=512,
        truncation=True
    )

def load_emotion_model():
    """Load emotion detection model"""
    return pipeline(
        "text-classification",
        model="j-hartmann/emotion-english-distilroberta-base",
        device=0 if torch.cuda.is_available() else -1,
        return_all_scores=True,
        batch_size=16
    )

def load_yolo_model():
    """Load YOLO object detection model"""
    from ultralytics import YOLO
    return YOLO('yolov8n.pt')

async def setup_connections():
    """Setup async database and Redis connections"""
    global redis_client, db_session
    
    # Async Redis
    redis_client = redis.from_url(
        "redis://redis:6379",
        encoding="utf-8",
        decode_responses=True,
        max_connections=20
    )
    
    # Async PostgreSQL
    engine = create_async_engine(
        "postgresql+asyncpg://postgres:password@postgres:5432/streaming_analytics",
        pool_size=20,
        max_overflow=30,
        pool_pre_ping=True
    )
    db_session = sessionmaker(engine, class_=AsyncSession)

async def cleanup_resources():
    """Cleanup resources on shutdown"""
    if redis_client:
        await redis_client.close()
    if thread_pool:
        thread_pool.shutdown(wait=True)

@app.get("/health")
async def health_check():
    """Health check endpoint"""
    return {
        "status": "healthy",
        "timestamp": datetime.utcnow().isoformat(),
        "models_loaded": len(models),
        "gpu_available": torch.cuda.is_available()
    }

@app.post("/api/ml/analyze-sentiment", response_model=SentimentResponse)
async def analyze_sentiment(request: SentimentRequest) -> SentimentResponse:
    """Async sentiment analysis with improved performance"""
    start_time = asyncio.get_event_loop().time()
    
    try:
        # Check cache first
        cache_key = f"sentiment:{hash(request.text)}"
        cached_result = await redis_client.get(cache_key)
        
        if cached_result:
            import json
            result = json.loads(cached_result)
            result['processing_time'] = asyncio.get_event_loop().time() - start_time
            return SentimentResponse(**result)
        
        # Run inference in thread pool
        sentiment_result = await asyncio.get_event_loop().run_in_executor(
            thread_pool,
            models['sentiment'],
            request.text
        )
        
        emotion_result = None
        if request.include_emotions:
            emotion_result = await asyncio.get_event_loop().run_in_executor(
                thread_pool,
                models['emotion'],
                request.text
            )
        
        # Process results
        sentiment = sentiment_result[0]
        emotions = {}
        if emotion_result:
            emotions = {
                item['label']: item['score'] 
                for item in emotion_result[0]
            }
        
        result = {
            "sentiment": sentiment['label'],
            "confidence": sentiment['score'],
            "emotions": emotions,
            "processing_time": asyncio.get_event_loop().time() - start_time
        }
        
        # Cache result
        await redis_client.setex(
            cache_key,
            3600,  # 1 hour TTL
            json.dumps(result, default=str)
        )
        
        return SentimentResponse(**result)
        
    except Exception as e:
        logger.error(f"Error in sentiment analysis: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/ml/batch-analyze")
async def batch_analyze_sentiment(request: BatchSentimentRequest):
    """High-performance batch sentiment analysis"""
    start_time = asyncio.get_event_loop().time()
    
    try:
        # Process in batches for optimal memory usage
        batch_size = 32
        results = []
        
        for i in range(0, len(request.texts), batch_size):
            batch = request.texts[i:i + batch_size]
            
            # Run batch inference
            sentiment_results = await asyncio.get_event_loop().run_in_executor(
                thread_pool,
                models['sentiment'],
                batch
            )
            
            emotion_results = None
            if request.include_emotions:
                emotion_results = await asyncio.get_event_loop().run_in_executor(
                    thread_pool,
                    models['emotion'],
                    batch
                )
            
            # Process batch results
            for j, sentiment in enumerate(sentiment_results):
                emotions = {}
                if emotion_results:
                    emotions = {
                        item['label']: item['score']
                        for item in emotion_results[j]
                    }
                
                results.append({
                    "text": batch[j],
                    "sentiment": sentiment['label'],
                    "confidence": sentiment['score'],
                    "emotions": emotions
                })
        
        processing_time = asyncio.get_event_loop().time() - start_time
        
        return {
            "results": results,
            "total_processed": len(request.texts),
            "processing_time": processing_time,
            "throughput": len(request.texts) / processing_time
        }
        
    except Exception as e:
        logger.error(f"Error in batch analysis: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/api/ml/detect-sponsor")
async def detect_sponsor(request: SponsorDetectionRequest):
    """Async sponsor detection with improved performance"""
    start_time = asyncio.get_event_loop().time()
    
    try:
        # Decode base64 image
        import base64
        from PIL import Image
        import io
        
        image_bytes = base64.b64decode(request.image_data)
        image = Image.open(io.BytesIO(image_bytes))
        
        # Run YOLO inference
        results = await asyncio.get_event_loop().run_in_executor(
            thread_pool,
            models['yolo'],
            image
        )
        
        # Process detections
        detections = []
        for result in results:
            for box in result.boxes:
                if box.conf > request.confidence_threshold:
                    detections.append({
                        "class": int(box.cls),
                        "confidence": float(box.conf),
                        "bbox": box.xyxy.tolist()[0]
                    })
        
        processing_time = asyncio.get_event_loop().time() - start_time
        
        return {
            "detections": detections,
            "processing_time": processing_time,
            "image_size": image.size
        }
        
    except Exception as e:
        logger.error(f"Error in sponsor detection: {e}")
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    import uvicorn
    
    # Use uvloop for better async performance
    asyncio.set_event_loop_policy(uvloop.EventLoopPolicy())
    
    uvicorn.run(
        "fastapi_app:app",
        host="0.0.0.0",
        port=5000,
        workers=1,  # Single worker due to model loading
        loop="uvloop",
        log_level="info",
        access_log=True
    )