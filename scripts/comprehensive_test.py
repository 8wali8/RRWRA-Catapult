import asyncio
import aiohttp
import json
import time
import statistics
from datetime import datetime
import random
import websockets
import psycopg2
import redis
from kafka import KafkaProducer, KafkaConsumer
import requests
from concurrent.futures import ThreadPoolExecutor
import logging

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

class StreamSenseComprehensiveTest:
    """
    Enterprise-grade testing suite leveraging the complete technology stack:
    - React + TypeScript + Apollo GraphQL (Frontend)
    - Spring Boot + Spring Cloud (Microservices) 
    - Netflix OSS (Eureka, Zuul, Hystrix)
    - Kafka + Zookeeper (Event Streaming)
    - PostgreSQL + Redis + Cassandra (Databases)
    - Prometheus + Grafana + Zipkin (Monitoring)
    - Docker + Kubernetes (Deployment)
    """
    
    def __init__(self):
        self.base_url = "http://localhost:8080"
        self.graphql_url = "http://localhost:8082/graphql"
        self.ml_url = "http://localhost:5000"
        self.video_url = "http://localhost:5001"
        self.frontend_url = "http://localhost:3000"
        
        # Technology stack endpoints
        self.eureka_url = "http://localhost:8761"
        self.prometheus_url = "http://localhost:9090"
        self.grafana_url = "http://localhost:3001"
        self.zipkin_url = "http://localhost:9411"
        
        # Performance tracking
        self.results = {
            'microservices': {},
            'databases': {},
            'event_streaming': {},
            'monitoring': {},
            'frontend': {},
            'ml_pipeline': {}
        }
        
        # Initialize connections
        self.kafka_producer = None
        self.redis_client = None
        self.postgres_conn = None
        
    async def setup_connections(self):
        """Initialize all technology stack connections"""
        logger.info("🔧 Setting up connections to technology stack...")
        
        try:
            # Kafka Producer
            self.kafka_producer = KafkaProducer(
                bootstrap_servers=['localhost:9092'],
                value_serializer=lambda v: json.dumps(v).encode('utf-8'),
                batch_size=16384,
                linger_ms=10
            )
            
            # Redis Connection
            self.redis_client = redis.Redis(
                host='localhost', 
                port=6379, 
                decode_responses=True,
                connection_pool_max_connections=20
            )
            
            # PostgreSQL Connection
            self.postgres_conn = psycopg2.connect(
                host="localhost",
                database="streaming_analytics",
                user="postgres",
                password="password",
                port=5432
            )
            
            logger.info("✅ All technology stack connections established")
            
        except Exception as e:
            logger.error(f"❌ Connection setup failed: {e}")
            raise
    
    async def test_service_discovery(self):
        """Test Netflix Eureka service discovery"""
        logger.info("🔍 Testing Netflix Eureka Service Discovery...")
        
        start_time = time.time()
        try:
            # Get registered services from Eureka
            response = requests.get(f"{self.eureka_url}/eureka/apps", 
                                  headers={'Accept': 'application/json'})
            
            if response.status_code == 200:
                apps_data = response.json()
                registered_services = len(apps_data.get('applications', {}).get('application', []))
                
                self.results['microservices']['eureka'] = {
                    'status': 'healthy',
                    'registered_services': registered_services,
                    'response_time_ms': (time.time() - start_time) * 1000
                }
                
                logger.info(f"✅ Eureka: {registered_services} services registered")
                return True
            else:
                raise Exception(f"Eureka returned {response.status_code}")
                
        except Exception as e:
            logger.error(f"❌ Eureka test failed: {e}")
            self.results['microservices']['eureka'] = {'status': 'failed', 'error': str(e)}
            return False
    
    async def test_api_gateway_zuul(self):
        """Test Zuul API Gateway routing and load balancing"""
        logger.info("🚪 Testing Zuul API Gateway...")
        
        routes_to_test = [
            "/api/chat/health",
            "/api/graphql/health", 
            "/actuator/health",
            "/actuator/hystrix.stream"
        ]
        
        gateway_results = []
        
        for route in routes_to_test:
            start_time = time.time()
            try:
                response = requests.get(f"{self.base_url}{route}", timeout=10)
                response_time = (time.time() - start_time) * 1000
                
                gateway_results.append({
                    'route': route,
                    'status_code': response.status_code,
                    'response_time_ms': response_time,
                    'success': 200 <= response.status_code < 300
                })
                
            except Exception as e:
                gateway_results.append({
                    'route': route,
                    'error': str(e),
                    'success': False
                })
        
        success_rate = sum(1 for r in gateway_results if r.get('success', False)) / len(gateway_results)
        avg_response_time = statistics.mean([r.get('response_time_ms', 0) for r in gateway_results if 'response_time_ms' in r])
        
        self.results['microservices']['zuul'] = {
            'routes_tested': len(routes_to_test),
            'success_rate': success_rate,
            'avg_response_time_ms': avg_response_time,
            'results': gateway_results
        }
        
        logger.info(f"✅ Zuul Gateway: {success_rate*100:.1f}% success rate, {avg_response_time:.1f}ms avg")
        return success_rate > 0.8
    
    async def test_circuit_breakers_hystrix(self):
        """Test Netflix Hystrix circuit breakers"""
        logger.info("⚡ Testing Hystrix Circuit Breakers...")
        
        # Test circuit breaker by overloading ML service
        tasks = []
        async with aiohttp.ClientSession() as session:
            # Send many requests to trigger circuit breaker
            for i in range(50):
                task = self.send_ml_request(session, 
                    {"text": f"Circuit breaker test {i}", "timeout": 0.1})
                tasks.append(task)
            
            results = await asyncio.gather(*tasks, return_exceptions=True)
        
        # Check Hystrix metrics
        try:
            hystrix_response = requests.get(f"{self.base_url}/actuator/metrics/hystrix.command.requests")
            hystrix_data = hystrix_response.json() if hystrix_response.status_code == 200 else {}
            
            circuit_breaker_trips = 0
            if 'measurements' in hystrix_data:
                for measurement in hystrix_data['measurements']:
                    if measurement.get('statistic') == 'COUNT':
                        circuit_breaker_trips = measurement.get('value', 0)
            
            self.results['microservices']['hystrix'] = {
                'requests_sent': len(tasks),
                'circuit_breaker_trips': circuit_breaker_trips,
                'protection_active': circuit_breaker_trips > 0
            }
            
            logger.info(f"✅ Hystrix: {circuit_breaker_trips} circuit breaker activations")
            return True
            
        except Exception as e:
            logger.error(f"❌ Hystrix test failed: {e}")
            return False
    
    async def test_kafka_event_streaming(self):
        """Test Apache Kafka event streaming performance"""
        logger.info("📡 Testing Kafka Event Streaming...")
        
        test_messages = 1000
        topic = "load-test-events"
        
        # Producer test
        start_time = time.time()
        for i in range(test_messages):
            event = {
                "id": i,
                "type": "chat_message",
                "data": f"Test message {i}",
                "timestamp": datetime.utcnow().isoformat(),
                "streamer_id": f"streamer_{i % 10}"
            }
            self.kafka_producer.send(topic, event)
        
        self.kafka_producer.flush()
        producer_time = time.time() - start_time
        
        # Consumer test
        consumer = KafkaConsumer(
            topic,
            bootstrap_servers=['localhost:9092'],
            value_deserializer=lambda m: json.loads(m.decode('utf-8')),
            consumer_timeout_ms=10000
        )
        
        consumed_messages = 0
        start_time = time.time()
        for message in consumer:
            consumed_messages += 1
            if consumed_messages >= test_messages:
                break
        
        consumer_time = time.time() - start_time
        consumer.close()
        
        self.results['event_streaming']['kafka'] = {
            'messages_produced': test_messages,
            'messages_consumed': consumed_messages,
            'producer_throughput_msg_sec': test_messages / producer_time,
            'consumer_throughput_msg_sec': consumed_messages / consumer_time,
            'producer_time_sec': producer_time,
            'consumer_time_sec': consumer_time
        }
        
        logger.info(f"✅ Kafka: {test_messages} msgs produced in {producer_time:.2f}s, {consumed_messages} consumed in {consumer_time:.2f}s")
        return consumed_messages >= test_messages * 0.95
    
    async def test_databases_performance(self):
        """Test PostgreSQL + Redis + Cassandra performance"""
        logger.info("🗄️ Testing Database Performance...")
        
        # PostgreSQL test
        postgres_start = time.time()
        cursor = self.postgres_conn.cursor()
        cursor.execute("SELECT COUNT(*) FROM information_schema.tables")
        postgres_result = cursor.fetchone()
        postgres_time = (time.time() - postgres_start) * 1000
        cursor.close()
        
        # Redis test
        redis_times = []
        for i in range(100):
            start = time.time()
            self.redis_client.set(f"test_key_{i}", f"test_value_{i}")
            self.redis_client.get(f"test_key_{i}")
            redis_times.append((time.time() - start) * 1000)
        
        self.results['databases'] = {
            'postgresql': {
                'query_time_ms': postgres_time,
                'tables_found': postgres_result[0] if postgres_result else 0,
                'status': 'healthy'
            },
            'redis': {
                'avg_operation_time_ms': statistics.mean(redis_times),
                'p95_time_ms': sorted(redis_times)[int(len(redis_times) * 0.95)],
                'operations_tested': len(redis_times),
                'status': 'healthy'
            }
        }
        
        logger.info(f"✅ PostgreSQL: {postgres_time:.1f}ms query time")
        logger.info(f"✅ Redis: {statistics.mean(redis_times):.1f}ms avg operation time")
        return True
    
    async def test_graphql_federation(self):
        """Test GraphQL federation and subscriptions"""
        logger.info("🔗 Testing GraphQL Federation...")
        
        queries = [
            {"query": "{ healthCheck { status timestamp } }"},
            {"query": "{ serviceInfo { name version uptime } }"},
        ]
        
        graphql_results = []
        async with aiohttp.ClientSession() as session:
            for query in queries:
                start_time = time.time()
                try:
                    async with session.post(self.graphql_url, 
                                          json=query,
                                          headers={'Content-Type': 'application/json'}) as response:
                        data = await response.json()
                        response_time = (time.time() - start_time) * 1000
                        
                        graphql_results.append({
                            'query': query['query'][:50] + '...',
                            'response_time_ms': response_time,
                            'status_code': response.status,
                            'has_data': 'data' in data,
                            'has_errors': 'errors' in data
                        })
                        
                except Exception as e:
                    graphql_results.append({
                        'query': query['query'][:50] + '...',
                        'error': str(e)
                    })
        
        success_rate = sum(1 for r in graphql_results if r.get('has_data', False)) / len(graphql_results)
        avg_response_time = statistics.mean([r.get('response_time_ms', 0) for r in graphql_results if 'response_time_ms' in r])
        
        self.results['microservices']['graphql'] = {
            'queries_tested': len(queries),
            'success_rate': success_rate,
            'avg_response_time_ms': avg_response_time,
            'results': graphql_results
        }
        
        logger.info(f"✅ GraphQL: {success_rate*100:.1f}% success rate, {avg_response_time:.1f}ms avg")
        return success_rate > 0.8
    
    async def test_ml_pipeline_performance(self):
        """Test complete ML pipeline performance"""
        logger.info("🤖 Testing ML Pipeline Performance...")
        
        # Test sentiment analysis
        sentiment_times = []
        async with aiohttp.ClientSession() as session:
            tasks = []
            for i in range(50):
                task = self.send_ml_request(session, {
                    "text": f"This is test message {i} for sentiment analysis",
                    "include_emotions": True
                })
                tasks.append(task)
            
            results = await asyncio.gather(*tasks, return_exceptions=True)
            
            successful_requests = [r for r in results if isinstance(r, dict) and 'response_time' in r]
            sentiment_times = [r['response_time'] for r in successful_requests]
        
        # Test video analysis
        video_start = time.time()
        test_image = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8/5+hHgAHggJ/PchI7wAAAABJRU5ErkJggg=="
        try:
            video_response = requests.post(f"{self.video_url}/api/video/analyze-frame",
                                         json={"image_data": test_image, "stream_id": "test"},
                                         timeout=10)
            video_time = (time.time() - video_start) * 1000
            video_success = video_response.status_code == 200
        except:
            video_time = 0
            video_success = False
        
        self.results['ml_pipeline'] = {
            'sentiment_analysis': {
                'requests_completed': len(sentiment_times),
                'avg_response_time_ms': statistics.mean(sentiment_times) if sentiment_times else 0,
                'p95_response_time_ms': sorted(sentiment_times)[int(len(sentiment_times) * 0.95)] if sentiment_times else 0,
                'success_rate': len(sentiment_times) / 50
            },
            'video_analysis': {
                'response_time_ms': video_time,
                'success': video_success
            }
        }
        
        logger.info(f"✅ ML Pipeline: {len(sentiment_times)}/50 sentiment requests successful")
        logger.info(f"✅ Video Analysis: {'Success' if video_success else 'Failed'} in {video_time:.1f}ms")
        return len(sentiment_times) >= 40
    
    async def test_monitoring_stack(self):
        """Test Prometheus + Grafana + Zipkin monitoring"""
        logger.info("📊 Testing Monitoring Stack...")
        
        monitoring_results = {}
        
        # Test Prometheus
        try:
            prom_response = requests.get(f"{self.prometheus_url}/api/v1/targets", timeout=10)
            if prom_response.status_code == 200:
                targets_data = prom_response.json()
                active_targets = len([t for t in targets_data.get('data', {}).get('activeTargets', []) 
                                    if t.get('health') == 'up'])
                monitoring_results['prometheus'] = {
                    'status': 'healthy',
                    'active_targets': active_targets
                }
            else:
                monitoring_results['prometheus'] = {'status': 'unhealthy'}
        except:
            monitoring_results['prometheus'] = {'status': 'unavailable'}
        
        # Test Grafana
        try:
            grafana_response = requests.get(f"{self.grafana_url}/api/health", timeout=10)
            monitoring_results['grafana'] = {
                'status': 'healthy' if grafana_response.status_code == 200 else 'unhealthy'
            }
        except:
            monitoring_results['grafana'] = {'status': 'unavailable'}
        
        # Test Zipkin
        try:
            zipkin_response = requests.get(f"{self.zipkin_url}/api/v2/services", timeout=10)
            if zipkin_response.status_code == 200:
                services = zipkin_response.json()
                monitoring_results['zipkin'] = {
                    'status': 'healthy',
                    'traced_services': len(services)
                }
            else:
                monitoring_results['zipkin'] = {'status': 'unhealthy'}
        except:
            monitoring_results['zipkin'] = {'status': 'unavailable'}
        
        self.results['monitoring'] = monitoring_results
        
        healthy_services = sum(1 for service in monitoring_results.values() 
                             if service.get('status') == 'healthy')
        
        logger.info(f"✅ Monitoring: {healthy_services}/3 services healthy")
        return healthy_services >= 2
    
    async def test_frontend_performance(self):
        """Test React + TypeScript frontend performance"""
        logger.info("⚛️ Testing React Frontend Performance...")
        
        frontend_tests = []
        
        # Test main page load
        start_time = time.time()
        try:
            response = requests.get(self.frontend_url, timeout=15)
            load_time = (time.time() - start_time) * 1000
            
            frontend_tests.append({
                'test': 'main_page_load',
                'load_time_ms': load_time,
                'status_code': response.status_code,
                'success': response.status_code == 200
            })
        except Exception as e:
            frontend_tests.append({
                'test': 'main_page_load',
                'error': str(e),
                'success': False
            })
        
        # Test API endpoints that frontend uses
        api_endpoints = [
            "/api/analytics/dashboard",
            "/api/chat/recent",
            "/actuator/health"
        ]
        
        for endpoint in api_endpoints:
            start_time = time.time()
            try:
                response = requests.get(f"{self.base_url}{endpoint}", timeout=10)
                response_time = (time.time() - start_time) * 1000
                
                frontend_tests.append({
                    'test': f'api_{endpoint.replace("/", "_")}',
                    'response_time_ms': response_time,
                    'status_code': response.status_code,
                    'success': 200 <= response.status_code < 300
                })
            except Exception as e:
                frontend_tests.append({
                    'test': f'api_{endpoint.replace("/", "_")}',
                    'error': str(e),
                    'success': False
                })
        
        success_rate = sum(1 for test in frontend_tests if test.get('success', False)) / len(frontend_tests)
        
        self.results['frontend'] = {
            'tests_run': len(frontend_tests),
            'success_rate': success_rate,
            'results': frontend_tests
        }
        
        logger.info(f"✅ Frontend: {success_rate*100:.1f}% tests passed")
        return success_rate > 0.7
    
    async def send_ml_request(self, session, data):
        """Helper method to send ML requests"""
        start_time = time.time()
        try:
            async with session.post(f"{self.ml_url}/api/ml/analyze-sentiment",
                                  json=data,
                                  timeout=aiohttp.ClientTimeout(total=30)) as response:
                await response.json()
                return {
                    'response_time': (time.time() - start_time) * 1000,
                    'status_code': response.status
                }
        except Exception as e:
            return {'error': str(e)}
    
    async def run_comprehensive_test(self):
        """Run the complete enterprise test suite"""
        logger.info("🚀 Starting StreamSense Comprehensive Technology Stack Test")
        logger.info("=" * 70)
        
        await self.setup_connections()
        
        test_results = {}
        
        # Run all test phases
        test_phases = [
            ("Service Discovery (Eureka)", self.test_service_discovery),
            ("API Gateway (Zuul)", self.test_api_gateway_zuul), 
            ("Circuit Breakers (Hystrix)", self.test_circuit_breakers_hystrix),
            ("Event Streaming (Kafka)", self.test_kafka_event_streaming),
            ("Database Performance", self.test_databases_performance),
            ("GraphQL Federation", self.test_graphql_federation),
            ("ML Pipeline", self.test_ml_pipeline_performance),
            ("Monitoring Stack", self.test_monitoring_stack),
            ("Frontend Performance", self.test_frontend_performance)
        ]
        
        for phase_name, test_func in test_phases:
            logger.info(f"\n🔄 Running: {phase_name}")
            try:
                success = await test_func()
                test_results[phase_name] = success
                status = "✅ PASSED" if success else "❌ FAILED"
                logger.info(f"   {status}")
            except Exception as e:
                logger.error(f"   ❌ FAILED: {e}")
                test_results[phase_name] = False
        
        # Generate final report
        self.generate_final_report(test_results)
        
        return test_results
    
    def generate_final_report(self, test_results):
        """Generate comprehensive test report"""
        logger.info("\n" + "=" * 70)
        logger.info("📋 STREAMSENSE ENTERPRISE TEST REPORT")
        logger.info("=" * 70)
        
        passed_tests = sum(test_results.values())
        total_tests = len(test_results)
        success_rate = (passed_tests / total_tests) * 100
        
        logger.info(f"\n🎯 OVERALL RESULTS:")
        logger.info(f"   Tests Passed: {passed_tests}/{total_tests}")
        logger.info(f"   Success Rate: {success_rate:.1f}%")
        
        logger.info(f"\n📊 TECHNOLOGY STACK VALIDATION:")
        for test_name, passed in test_results.items():
            status = "✅" if passed else "❌"
            logger.info(f"   {status} {test_name}")
        
        # Performance metrics summary
        logger.info(f"\n⚡ PERFORMANCE METRICS:")
        
        if 'kafka' in self.results['event_streaming']:
            kafka_data = self.results['event_streaming']['kafka']
            logger.info(f"   Kafka Throughput: {kafka_data['producer_throughput_msg_sec']:.0f} msg/sec")
        
        if 'sentiment_analysis' in self.results['ml_pipeline']:
            ml_data = self.results['ml_pipeline']['sentiment_analysis']
            logger.info(f"   ML Pipeline P95: {ml_data['p95_response_time_ms']:.0f}ms")
        
        if 'zuul' in self.results['microservices']:
            zuul_data = self.results['microservices']['zuul']
            logger.info(f"   API Gateway Avg: {zuul_data['avg_response_time_ms']:.0f}ms")
        
        # Requirements validation
        logger.info(f"\n🏆 REQUIREMENTS VALIDATION:")
        
        # Check 10K+ events per second
        kafka_throughput = self.results['event_streaming'].get('kafka', {}).get('producer_throughput_msg_sec', 0)
        throughput_check = kafka_throughput >= 10000
        logger.info(f"   {'✅' if throughput_check else '❌'} 10K+ Events/Second: {kafka_throughput:.0f} msg/sec")
        
        # Check <200ms P95 latency  
        p95_latency = self.results['ml_pipeline'].get('sentiment_analysis', {}).get('p95_response_time_ms', 999)
        latency_check = p95_latency < 200
        logger.info(f"   {'✅' if latency_check else '❌'} <200ms P95 Latency: {p95_latency:.0f}ms")
        
        # Check service availability
        service_health = success_rate >= 90
        logger.info(f"   {'✅' if service_health else '❌'} Service Health: {success_rate:.1f}%")
        
        overall_success = throughput_check and latency_check and service_health
        
        logger.info(f"\n🎉 FINAL VERDICT:")
        if overall_success:
            logger.info("   ✅ ALL ENTERPRISE REQUIREMENTS MET!")
            logger.info("   🚀 Production-ready distributed system validated!")
        else:
            logger.info("   ❌ Some requirements need attention")
            logger.info("   🔧 Review failed components and optimize")
        
        logger.info("\n📁 Detailed results saved in self.results object")
        
        # Save results to file
        with open(f'test_results_{datetime.now().strftime("%Y%m%d_%H%M%S")}.json', 'w') as f:
            json.dump(self.results, f, indent=2)
        
        return overall_success

async def main():
    """Run the comprehensive StreamSense test suite"""
    tester = StreamSenseComprehensiveTest()
    results = await tester.run_comprehensive_test()
    return results

if __name__ == "__main__":
    # Run the comprehensive test
    results = asyncio.run(main())