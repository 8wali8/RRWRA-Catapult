# Load Testing Script for StreamSense
# Validates 10K+ events/sec with <200ms P95 latency

import asyncio
import aiohttp
import time
import json
import statistics
from datetime import datetime
import random

class StreamSenseLoadTester:
    def __init__(self):
        self.base_url = "http://localhost:8080"
        self.results = {
            'response_times': [],
            'errors': 0,
            'total_requests': 0,
            'events_per_second': 0
        }
    
    async def generate_chat_event(self, session):
        """Generate realistic chat message event"""
        messages = [
            "This stream is amazing!",
            "Love this content",
            "Great gameplay",
            "Nice sponsor integration",
            "This is so entertaining",
            "Awesome stream quality"
        ]
        
        data = {
            "message": random.choice(messages),
            "streamerId": f"streamer_{random.randint(1, 100)}",
            "userId": f"user_{random.randint(1, 10000)}",
            "timestamp": datetime.utcnow().isoformat()
        }
        
        start_time = time.time()
        try:
            async with session.post(f"{self.base_url}/api/chat/message", json=data) as response:
                await response.read()
                response_time = time.time() - start_time
                self.results['response_times'].append(response_time * 1000)  # Convert to ms
                self.results['total_requests'] += 1
        except Exception as e:
            self.results['errors'] += 1
            print(f"Error: {e}")
    
    async def generate_sentiment_event(self, session):
        """Generate sentiment analysis event"""
        data = {
            "text": f"Random sentiment test message {random.randint(1, 1000)}",
            "include_emotions": True
        }
        
        start_time = time.time()
        try:
            async with session.post("http://localhost:5000/api/ml/analyze-sentiment", json=data) as response:
                await response.read()
                response_time = time.time() - start_time
                self.results['response_times'].append(response_time * 1000)
                self.results['total_requests'] += 1
        except Exception as e:
            self.results['errors'] += 1
    
    async def run_load_test(self, target_rps=10000, duration_seconds=60):
        """Run load test targeting 10K+ requests per second"""
        print(f"🚀 Starting load test: {target_rps} RPS for {duration_seconds} seconds")
        
        connector = aiohttp.TCPConnector(limit=1000)
        async with aiohttp.ClientSession(connector=connector) as session:
            start_time = time.time()
            tasks = []
            
            while time.time() - start_time < duration_seconds:
                # Create batch of concurrent requests
                batch_size = min(target_rps // 10, 1000)  # 100ms batches
                
                for _ in range(batch_size):
                    if random.random() < 0.7:  # 70% chat events
                        task = self.generate_chat_event(session)
                    else:  # 30% ML events
                        task = self.generate_sentiment_event(session)
                    tasks.append(task)
                
                # Execute batch
                if len(tasks) >= batch_size:
                    await asyncio.gather(*tasks[:batch_size], return_exceptions=True)
                    tasks = tasks[batch_size:]
                
                # Rate limiting to achieve target RPS
                await asyncio.sleep(0.1)
            
            # Wait for remaining tasks
            if tasks:
                await asyncio.gather(*tasks, return_exceptions=True)
        
        # Calculate results
        total_time = time.time() - start_time
        self.results['events_per_second'] = self.results['total_requests'] / total_time
        
        return self.analyze_results()
    
    def analyze_results(self):
        """Analyze performance results"""
        if not self.results['response_times']:
            return {"error": "No successful requests"}
        
        response_times = sorted(self.results['response_times'])
        
        analysis = {
            'total_requests': self.results['total_requests'],
            'errors': self.results['errors'],
            'events_per_second': round(self.results['events_per_second'], 2),
            'avg_response_time_ms': round(statistics.mean(response_times), 2),
            'p50_latency_ms': round(response_times[len(response_times)//2], 2),
            'p95_latency_ms': round(response_times[int(len(response_times)*0.95)], 2),
            'p99_latency_ms': round(response_times[int(len(response_times)*0.99)], 2),
            'max_response_time_ms': round(max(response_times), 2),
            'error_rate': round((self.results['errors'] / max(self.results['total_requests'], 1)) * 100, 2)
        }
        
        # Validate requirements
        analysis['meets_10k_rps'] = analysis['events_per_second'] >= 10000
        analysis['meets_p95_latency'] = analysis['p95_latency_ms'] <= 200
        analysis['meets_requirements'] = analysis['meets_10k_rps'] and analysis['meets_p95_latency']
        
        return analysis

async def main():
    """Run comprehensive load test"""
    print("StreamSense Performance Validation")
    print("=" * 50)
    
    tester = StreamSenseLoadTester()
    
    # Run load test
    results = await tester.run_load_test(target_rps=12000, duration_seconds=120)
    
    # Print results
    print(f"""
📊 PERFORMANCE TEST RESULTS:
{'='*50}
🔥 Events/Second: {results['events_per_second']} (Target: 10,000+)
⚡ P95 Latency: {results['p95_latency_ms']}ms (Target: <200ms)
📈 P99 Latency: {results['p99_latency_ms']}ms
🎯 Average Latency: {results['avg_response_time_ms']}ms
📋 Total Requests: {results['total_requests']:,}
❌ Errors: {results['errors']} ({results['error_rate']}%)

✅ REQUIREMENTS VALIDATION:
{'='*30}
10K+ Events/Sec: {'✅ PASS' if results['meets_10k_rps'] else '❌ FAIL'}
<200ms P95 Latency: {'✅ PASS' if results['meets_p95_latency'] else '❌ FAIL'}

🏆 OVERALL: {'✅ MEETS ALL REQUIREMENTS' if results['meets_requirements'] else '❌ NEEDS OPTIMIZATION'}
""")

if __name__ == "__main__":
    asyncio.run(main())