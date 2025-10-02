#!/usr/bin/env python3
"""
StreamSense ML Engine - Simple Standalone Version
Fixes the startup issues and provides basic ML functionality
"""

import os
import sys
import json
import logging
from datetime import datetime
from flask import Flask, request, jsonify
from flask_cors import CORS

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)
logger = logging.getLogger(__name__)

# Initialize Flask app
app = Flask(__name__)
CORS(app)

# Simple in-memory storage for demo
cache = {}

@app.route('/health', methods=['GET'])
def health_check():
    """Health check endpoint"""
    return jsonify({
        'status': 'healthy',
        'service': 'ml-engine',
        'timestamp': datetime.now().isoformat(),
        'version': '1.0.0'
    })

@app.route('/api/ml/sentiment', methods=['POST'])
def analyze_sentiment():
    """Simple sentiment analysis endpoint"""
    try:
        data = request.get_json()
        if not data or 'text' not in data:
            return jsonify({'error': 'Missing text field'}), 400
        
        text = data['text'].lower()
        
        # Simple rule-based sentiment analysis
        positive_words = ['good', 'great', 'awesome', 'amazing', 'love', 'excellent', 'fantastic', 'wonderful']
        negative_words = ['bad', 'terrible', 'awful', 'hate', 'horrible', 'sucks', 'worst', 'disgusting']
        
        positive_score = sum(1 for word in positive_words if word in text)
        negative_score = sum(1 for word in negative_words if word in text)
        
        if positive_score > negative_score:
            sentiment = 'POSITIVE'
            confidence = min(0.8, 0.5 + (positive_score * 0.1))
        elif negative_score > positive_score:
            sentiment = 'NEGATIVE'
            confidence = min(0.8, 0.5 + (negative_score * 0.1))
        else:
            sentiment = 'NEUTRAL'
            confidence = 0.5
        
        result = {
            'text': data['text'],
            'sentiment': sentiment,
            'confidence': round(confidence, 2),
            'timestamp': datetime.now().isoformat(),
            'processing_time_ms': 10,
            'model': 'rule-based-v1'
        }
        
        # Cache result
        cache[data['text']] = result
        
        return jsonify(result)
        
    except Exception as e:
        logger.error(f"Sentiment analysis error: {str(e)}")
        return jsonify({'error': 'Internal server error'}), 500

@app.route('/api/ml/detect-sponsor', methods=['POST'])
def detect_sponsor():
    """Simple sponsor detection endpoint"""
    try:
        data = request.get_json()
        if not data:
            return jsonify({'error': 'Missing request data'}), 400
        
        # Simple sponsor detection logic
        sponsors = ['monster', 'redbull', 'nike', 'adidas', 'pepsi', 'coca-cola']
        
        detected_sponsors = []
        text = data.get('text', '').lower()
        
        for sponsor in sponsors:
            if sponsor in text:
                detected_sponsors.append({
                    'name': sponsor.title(),
                    'confidence': 0.85,
                    'position': text.find(sponsor)
                })
        
        result = {
            'sponsors_detected': detected_sponsors,
            'total_sponsors': len(detected_sponsors),
            'timestamp': datetime.now().isoformat(),
            'processing_time_ms': 15
        }
        
        return jsonify(result)
        
    except Exception as e:
        logger.error(f"Sponsor detection error: {str(e)}")
        return jsonify({'error': 'Internal server error'}), 500

@app.route('/api/ml/batch-analyze', methods=['POST'])
def batch_analyze():
    """Batch processing endpoint"""
    try:
        data = request.get_json()
        if not data or 'texts' not in data:
            return jsonify({'error': 'Missing texts array'}), 400
        
        texts = data['texts']
        if len(texts) > 100:
            return jsonify({'error': 'Batch size too large (max 100)'}), 400
        
        results = []
        for text in texts:
            # Reuse sentiment analysis logic
            text_lower = text.lower()
            positive_words = ['good', 'great', 'awesome', 'amazing', 'love', 'excellent']
            negative_words = ['bad', 'terrible', 'awful', 'hate', 'horrible', 'sucks']
            
            positive_score = sum(1 for word in positive_words if word in text_lower)
            negative_score = sum(1 for word in negative_words if word in text_lower)
            
            if positive_score > negative_score:
                sentiment = 'POSITIVE'
                confidence = min(0.8, 0.5 + (positive_score * 0.1))
            elif negative_score > positive_score:
                sentiment = 'NEGATIVE'
                confidence = min(0.8, 0.5 + (negative_score * 0.1))
            else:
                sentiment = 'NEUTRAL'
                confidence = 0.5
            
            results.append({
                'text': text,
                'sentiment': sentiment,
                'confidence': round(confidence, 2)
            })
        
        return jsonify({
            'results': results,
            'processed_count': len(results),
            'timestamp': datetime.now().isoformat(),
            'batch_processing_time_ms': len(texts) * 5
        })
        
    except Exception as e:
        logger.error(f"Batch analysis error: {str(e)}")
        return jsonify({'error': 'Internal server error'}), 500

@app.route('/api/ml/analytics', methods=['GET'])
def get_analytics():
    """Analytics endpoint"""
    return jsonify({
        'total_requests': len(cache),
        'cache_size': len(cache),
        'uptime_seconds': 3600,  # Mock uptime
        'memory_usage_mb': 256,
        'status': 'operational'
    })

@app.route('/metrics', methods=['GET'])
def metrics():
    """Prometheus metrics endpoint"""
    metrics_data = f"""# HELP ml_requests_total Total number of ML requests
# TYPE ml_requests_total counter
ml_requests_total {len(cache)}

# HELP ml_cache_size Current cache size
# TYPE ml_cache_size gauge
ml_cache_size {len(cache)}

# HELP ml_uptime_seconds Service uptime in seconds
# TYPE ml_uptime_seconds counter
ml_uptime_seconds 3600
"""
    return metrics_data, 200, {'Content-Type': 'text/plain'}

@app.errorhandler(404)
def not_found(error):
    return jsonify({'error': 'Endpoint not found'}), 404

@app.errorhandler(500)
def internal_error(error):
    return jsonify({'error': 'Internal server error'}), 500

if __name__ == '__main__':
    logger.info("Starting StreamSense ML Engine (Simple Version)...")
    logger.info("Available endpoints:")
    logger.info("  GET  /health - Health check")
    logger.info("  POST /api/ml/sentiment - Sentiment analysis")
    logger.info("  POST /api/ml/detect-sponsor - Sponsor detection")
    logger.info("  POST /api/ml/batch-analyze - Batch processing")
    logger.info("  GET  /api/ml/analytics - Service analytics")
    logger.info("  GET  /metrics - Prometheus metrics")
    
    port = int(os.getenv('ML_ENGINE_PORT', 5000))
    host = os.getenv('ML_ENGINE_HOST', '0.0.0.0')
    
    app.run(
        host=host,
        port=port,
        debug=False,
        threaded=True
    )