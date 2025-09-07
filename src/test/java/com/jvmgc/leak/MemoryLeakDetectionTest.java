package com.jvmgc.leak;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 메모리 누수 패턴 분석 테스트
 * 
 * 테스트 목표:
 * - 일반적인 메모리 누수 패턴 감지 및 해결
 * - WebSocket 연결 관리 시나리오 시뮬레이션
 * - 메모리 사용량 추이 분석
 * - 누수 해결 전후 비교
 * 
 * 테스트 시나리오:
 * 1. 리스너 해제하지 않는 패턴
 * 2. 캐시에서 만료된 엔트리 정리하지 않는 패턴
 * 3. 스레드 로컬 변수 정리하지 않는 패턴
 * 4. 순환 참조로 인한 누수 패턴
 */
@Component
@Slf4j
public class MemoryLeakDetectionTest {
    
    private final MemoryMXBean memoryBean;
    
    public MemoryLeakDetectionTest() {
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        log.info("MemoryLeakDetectionTest 초기화 완료");
    }
    
    /**
     * 세션 관리에서의 메모리 누수 테스트
     * - 세션 생성/해제 시뮬레이션
     * - 누수가 있는 구현 vs 최적화된 구현 비교
     */
    @Test
    @DisplayName("세션 관리 메모리 누수 테스트")
    public void testMemoryLeakInSessionManagement() {
        log.info("=== 세션 관리 메모리 누수 테스트 시작 ===");
        
        // 1. 메모리 누수가 있는 구현 테스트
        log.info("1. 누수가 있는 세션 관리자 테스트");
        MemoryUsageReport leakyReport = testLeakySessionManager();
        
        // 메모리 정리 및 GC
        System.gc();
        waitForGC();
        
        // 2. 최적화된 구현 테스트
        log.info("2. 최적화된 세션 관리자 테스트");
        MemoryUsageReport optimizedReport = testOptimizedSessionManager();
        
        // 3. 결과 비교 및 검증
        log.info("=== 테스트 결과 비교 ===");
        logMemoryReport("누수 있는 구현", leakyReport);
        logMemoryReport("최적화된 구현", optimizedReport);
        
        // 검증: 최적화된 구현이 더 나은 메모리 효율성을 보여야 함
        assertThat(optimizedReport.isMemoryLeakDetected()).isFalse();
        assertThat(optimizedReport.getMemoryEfficiency()).isGreaterThan(0.8); // 80% 이상 효율
        
        // 누수가 있는 구현은 메모리 효율성이 낮아야 함
        if (leakyReport.isMemoryLeakDetected()) {
            log.info("✅ 메모리 누수 감지 성공");
        }
        
        log.info("=== 세션 관리 메모리 누수 테스트 완료 ===");
    }
    
    /**
     * 캐시 메모리 누수 테스트
     * - 만료된 엔트리 정리하지 않는 패턴
     */
    @Test
    @DisplayName("캐시 메모리 누수 테스트")
    public void testCacheMemoryLeak() {
        log.info("=== 캐시 메모리 누수 테스트 시작 ===");
        
        // 1. 누수가 있는 캐시 테스트
        log.info("1. 누수가 있는 캐시 테스트");
        MemoryUsageReport leakyCacheReport = testLeakyCache();
        
        System.gc();
        waitForGC();
        
        // 2. 자동 정리 캐시 테스트
        log.info("2. 자동 정리 캐시 테스트");
        MemoryUsageReport cleanCacheReport = testSelfCleaningCache();
        
        // 결과 비교
        log.info("=== 캐시 테스트 결과 비교 ===");
        logMemoryReport("누수 있는 캐시", leakyCacheReport);
        logMemoryReport("자동 정리 캐시", cleanCacheReport);
        
        // 검증
        assertThat(cleanCacheReport.getMemoryEfficiency()).isGreaterThan(leakyCacheReport.getMemoryEfficiency());
        
        log.info("=== 캐시 메모리 누수 테스트 완료 ===");
    }
    
    /**
     * 리스너 메모리 누수 테스트
     * - 이벤트 리스너 해제하지 않는 패턴
     */
    @Test
    @DisplayName("리스너 메모리 누수 테스트")
    public void testListenerMemoryLeak() {
        log.info("=== 리스너 메모리 누수 테스트 시작 ===");
        
        // 1. 누수가 있는 이벤트 핸들러 테스트
        log.info("1. 누수가 있는 이벤트 핸들러 테스트");
        MemoryUsageReport leakyListenerReport = testLeakyEventHandler();
        
        System.gc();
        waitForGC();
        
        // 2. 적절한 리소스 관리 핸들러 테스트
        log.info("2. 적절한 리소스 관리 핸들러 테스트");
        MemoryUsageReport cleanListenerReport = testCleanEventHandler();
        
        // 결과 비교
        log.info("=== 리스너 테스트 결과 비교 ===");
        logMemoryReport("누수 있는 리스너", leakyListenerReport);
        logMemoryReport("정리되는 리스너", cleanListenerReport);
        
        // 검증
        assertThat(cleanListenerReport.getMemoryEfficiency()).isGreaterThan(leakyListenerReport.getMemoryEfficiency());
        
        log.info("=== 리스너 메모리 누수 테스트 완료 ===");
    }
    
    /**
     * 스레드 로컬 메모리 누수 테스트
     * - ThreadLocal 변수 정리하지 않는 패턴
     */
    @Test
    @DisplayName("스레드 로컬 메모리 누수 테스트")
    public void testThreadLocalMemoryLeak() {
        log.info("=== 스레드 로컬 메모리 누수 테스트 시작 ===");
        
        // 1. 누수가 있는 ThreadLocal 사용
        log.info("1. 누수가 있는 ThreadLocal 테스트");
        MemoryUsageReport leakyThreadLocalReport = testLeakyThreadLocal();
        
        System.gc();
        waitForGC();
        
        // 2. 적절한 ThreadLocal 관리
        log.info("2. 적절한 ThreadLocal 관리 테스트");
        MemoryUsageReport cleanThreadLocalReport = testCleanThreadLocal();
        
        // 결과 비교
        log.info("=== ThreadLocal 테스트 결과 비교 ===");
        logMemoryReport("누수 있는 ThreadLocal", leakyThreadLocalReport);
        logMemoryReport("정리되는 ThreadLocal", cleanThreadLocalReport);
        
        // 검증
        assertThat(cleanThreadLocalReport.getMemoryEfficiency()).isGreaterThan(leakyThreadLocalReport.getMemoryEfficiency());
        
        log.info("=== 스레드 로컬 메모리 누수 테스트 완료 ===");
    }
    
    // === 테스트 구현 메서드들 ===
    
    /**
     * 누수가 있는 세션 관리자 테스트
     */
    private MemoryUsageReport testLeakySessionManager() {
        MemoryUsageMonitor monitor = new MemoryUsageMonitor();
        monitor.startMonitoring();
        
        LeakySessionManager leakyManager = new LeakySessionManager();
        
        // 세션 생성/해제 시뮬레이션
        for (int i = 0; i < 10000; i++) {
            MockWebSocketSession session = new MockWebSocketSession("session_" + i);
            leakyManager.addSession(session);
            
            // 일부 세션만 정리 (메모리 누수 시뮬레이션)
            if (i % 100 == 0 && i > 0) {
                leakyManager.removeSession("session_" + (i - 50));
            }
            
            // 진행률 로깅
            if (i % 2000 == 0) {
                log.debug("누수 세션 관리자 진행률: {}/10000", i);
            }
        }
        
        return monitor.generateReport();
    }
    
    /**
     * 최적화된 세션 관리자 테스트
     */
    private MemoryUsageReport testOptimizedSessionManager() {
        MemoryUsageMonitor monitor = new MemoryUsageMonitor();
        monitor.startMonitoring();
        
        OptimizedSessionManager optimizedManager = new OptimizedSessionManager();
        
        // 동일한 워크로드 실행
        for (int i = 0; i < 10000; i++) {
            MockWebSocketSession session = new MockWebSocketSession("session_" + i);
            optimizedManager.addSession(session);
            
            if (i % 100 == 0 && i > 0) {
                optimizedManager.removeSession("session_" + (i - 50));
            }
            
            if (i % 2000 == 0) {
                log.debug("최적화 세션 관리자 진행률: {}/10000", i);
            }
        }
        
        // 명시적 정리
        optimizedManager.cleanup();
        
        return monitor.generateReport();
    }
    
    /**
     * 누수가 있는 캐시 테스트
     */
    private MemoryUsageReport testLeakyCache() {
        MemoryUsageMonitor monitor = new MemoryUsageMonitor();
        monitor.startMonitoring();
        
        LeakyCache cache = new LeakyCache();
        
        // 대량의 캐시 엔트리 생성
        for (int i = 0; i < 50000; i++) {
            String key = "key_" + i;
            String value = "value_" + i + "_" + System.currentTimeMillis();
            cache.put(key, value, 1); // 1초 TTL
            
            if (i % 10000 == 0) {
                log.debug("누수 캐시 진행률: {}/50000", i);
            }
        }
        
        // TTL이 지났지만 정리되지 않음
        try {
            Thread.sleep(2000); // 2초 대기
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        return monitor.generateReport();
    }
    
    /**
     * 자동 정리 캐시 테스트
     */
    private MemoryUsageReport testSelfCleaningCache() {
        MemoryUsageMonitor monitor = new MemoryUsageMonitor();
        monitor.startMonitoring();
        
        SelfCleaningCache cache = new SelfCleaningCache();
        
        // 동일한 워크로드
        for (int i = 0; i < 50000; i++) {
            String key = "key_" + i;
            String value = "value_" + i + "_" + System.currentTimeMillis();
            cache.put(key, value, 1); // 1초 TTL
            
            if (i % 10000 == 0) {
                log.debug("자동 정리 캐시 진행률: {}/50000", i);
                cache.cleanup(); // 주기적 정리
            }
        }
        
        // 최종 정리
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        cache.cleanup();
        
        return monitor.generateReport();
    }
    
    /**
     * 누수가 있는 이벤트 핸들러 테스트
     */
    private MemoryUsageReport testLeakyEventHandler() {
        MemoryUsageMonitor monitor = new MemoryUsageMonitor();
        monitor.startMonitoring();
        
        BadEventHandler handler = new BadEventHandler();
        
        // 대량의 리스너 등록
        for (int i = 0; i < 10000; i++) {
            MockEventListener listener = new MockEventListener("listener_" + i);
            handler.addListener(listener);
            
            if (i % 2000 == 0) {
                log.debug("누수 이벤트 핸들러 진행률: {}/10000", i);
            }
        }
        
        // 리스너 해제하지 않음 (메모리 누수)
        
        return monitor.generateReport();
    }
    
    /**
     * 적절한 리소스 관리 핸들러 테스트
     */
    private MemoryUsageReport testCleanEventHandler() {
        MemoryUsageMonitor monitor = new MemoryUsageMonitor();
        monitor.startMonitoring();
        
        GoodEventHandler handler = new GoodEventHandler();
        List<MockEventListener> listeners = new ArrayList<>();
        
        // 동일한 워크로드
        for (int i = 0; i < 10000; i++) {
            MockEventListener listener = new MockEventListener("listener_" + i);
            handler.addListener(listener);
            listeners.add(listener);
            
            if (i % 2000 == 0) {
                log.debug("정리되는 이벤트 핸들러 진행률: {}/10000", i);
            }
        }
        
        // 적절한 리스너 해제
        for (MockEventListener listener : listeners) {
            handler.removeListener(listener);
        }
        handler.cleanup();
        
        return monitor.generateReport();
    }
    
    /**
     * 누수가 있는 ThreadLocal 테스트
     */
    private MemoryUsageReport testLeakyThreadLocal() {
        MemoryUsageMonitor monitor = new MemoryUsageMonitor();
        monitor.startMonitoring();
        
        LeakyThreadLocalService service = new LeakyThreadLocalService();
        
        // 여러 스레드에서 ThreadLocal 사용
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            final int threadId = i;
            Thread thread = new Thread(() -> {
                for (int j = 0; j < 100; j++) {
                    service.processData("data_" + threadId + "_" + j);
                }
                // ThreadLocal 정리하지 않음
            });
            threads.add(thread);
            thread.start();
        }
        
        // 모든 스레드 완료 대기
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        return monitor.generateReport();
    }
    
    /**
     * 적절한 ThreadLocal 관리 테스트
     */
    private MemoryUsageReport testCleanThreadLocal() {
        MemoryUsageMonitor monitor = new MemoryUsageMonitor();
        monitor.startMonitoring();
        
        CleanThreadLocalService service = new CleanThreadLocalService();
        
        // 동일한 워크로드
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            final int threadId = i;
            Thread thread = new Thread(() -> {
                try {
                    for (int j = 0; j < 100; j++) {
                        service.processData("data_" + threadId + "_" + j);
                    }
                } finally {
                    service.cleanup(); // ThreadLocal 정리
                }
            });
            threads.add(thread);
            thread.start();
        }
        
        // 모든 스레드 완료 대기
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        
        return monitor.generateReport();
    }
    
    /**
     * 메모리 리포트 로깅
     */
    private void logMemoryReport(String testName, MemoryUsageReport report) {
        log.info("=== {} 결과 ===", testName);
        log.info("메모리 누수 감지: {}", report.isMemoryLeakDetected());
        log.info("메모리 효율성: {:.2f}%", report.getMemoryEfficiency() * 100);
        log.info("누수율: {:.2f}%", report.getLeakRate() * 100);
        log.info("최대 메모리 사용량: {}", formatBytes(report.getMaxMemoryUsed()));
        log.info("평균 메모리 사용량: {}", formatBytes(report.getAvgMemoryUsed()));
    }
    
    /**
     * GC 완료까지 대기
     */
    private void waitForGC() {
        try {
            Thread.sleep(500); // GC 완료를 위한 대기
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 바이트 포맷팅
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
    
    // === 테스트용 구현체들 ===
    
    /**
     * 누수가 있는 세션 관리자
     */
    static class LeakySessionManager {
        private final List<MockWebSocketSession> sessions = new ArrayList<>();
        
        public void addSession(MockWebSocketSession session) {
            sessions.add(session); // 제거 로직 부족
        }
        
        public void removeSession(String sessionId) {
            // 비효율적인 제거 (일부만 제거됨)
            sessions.removeIf(s -> s.getId().equals(sessionId));
        }
    }
    
    /**
     * 최적화된 세션 관리자
     */
    static class OptimizedSessionManager {
        private final Map<String, WeakReference<MockWebSocketSession>> sessions = 
                new ConcurrentHashMap<>();
        
        public void addSession(MockWebSocketSession session) {
            sessions.put(session.getId(), new WeakReference<>(session));
        }
        
        public void removeSession(String sessionId) {
            sessions.remove(sessionId);
            cleanupExpiredReferences();
        }
        
        private void cleanupExpiredReferences() {
            sessions.entrySet().removeIf(entry -> entry.getValue().get() == null);
        }
        
        public void cleanup() {
            sessions.clear();
        }
    }
    
    /**
     * Mock WebSocket 세션
     */
    @Data
    static class MockWebSocketSession {
        private final String id;
        private final long createdTime = System.currentTimeMillis();
        private final byte[] data = new byte[1024]; // 1KB 데이터
        
        public MockWebSocketSession(String id) {
            this.id = id;
        }
    }
    
    /**
     * 누수가 있는 캐시
     */
    static class LeakyCache {
        private final Map<String, CacheEntry> cache = new HashMap<>();
        
        public void put(String key, String value, long ttlSeconds) {
            cache.put(key, new CacheEntry(value, ttlSeconds));
        }
        
        public String get(String key) {
            CacheEntry entry = cache.get(key);
            return entry != null ? entry.getValue() : null;
        }
        
        @Data
        static class CacheEntry {
            private final String value;
            private final long expiryTime;
            
            public CacheEntry(String value, long ttlSeconds) {
                this.value = value;
                this.expiryTime = System.currentTimeMillis() + (ttlSeconds * 1000);
            }
            
            public boolean isExpired() {
                return System.currentTimeMillis() > expiryTime;
            }
        }
    }
    
    /**
     * 자동 정리 캐시
     */
    static class SelfCleaningCache {
        private final Map<String, LeakyCache.CacheEntry> cache = new HashMap<>();
        
        public void put(String key, String value, long ttlSeconds) {
            cache.put(key, new LeakyCache.CacheEntry(value, ttlSeconds));
        }
        
        public String get(String key) {
            LeakyCache.CacheEntry entry = cache.get(key);
            if (entry != null && entry.isExpired()) {
                cache.remove(key);
                return null;
            }
            return entry != null ? entry.getValue() : null;
        }
        
        public void cleanup() {
            cache.entrySet().removeIf(entry -> entry.getValue().isExpired());
        }
    }
    
    /**
     * 누수가 있는 이벤트 핸들러
     */
    static class BadEventHandler {
        private final List<MockEventListener> listeners = new ArrayList<>();
        
        public void addListener(MockEventListener listener) {
            listeners.add(listener);
            // 리스너를 제거하는 메서드가 없어 메모리 누수 발생
        }
    }
    
    /**
     * 적절한 리소스 관리 핸들러
     */
    static class GoodEventHandler {
        private final List<MockEventListener> listeners = new ArrayList<>();
        
        public void addListener(MockEventListener listener) {
            listeners.add(listener);
        }
        
        public void removeListener(MockEventListener listener) {
            listeners.remove(listener);
        }
        
        public void cleanup() {
            listeners.clear();
        }
    }
    
    /**
     * Mock 이벤트 리스너
     */
    @Data
    static class MockEventListener {
        private final String name;
        private final byte[] data = new byte[512]; // 512B 데이터
    }
    
    /**
     * 누수가 있는 ThreadLocal 서비스
     */
    static class LeakyThreadLocalService {
        private final ThreadLocal<Map<String, Object>> threadLocalData = new ThreadLocal<>();
        
        public void processData(String data) {
            Map<String, Object> localData = threadLocalData.get();
            if (localData == null) {
                localData = new HashMap<>();
                threadLocalData.set(localData);
            }
            localData.put(data, new byte[1024]); // 1KB 데이터 추가
            // ThreadLocal 정리하지 않음
        }
    }
    
    /**
     * 적절한 ThreadLocal 관리 서비스
     */
    static class CleanThreadLocalService {
        private final ThreadLocal<Map<String, Object>> threadLocalData = new ThreadLocal<>();
        
        public void processData(String data) {
            Map<String, Object> localData = threadLocalData.get();
            if (localData == null) {
                localData = new HashMap<>();
                threadLocalData.set(localData);
            }
            localData.put(data, new byte[1024]);
        }
        
        public void cleanup() {
            threadLocalData.remove(); // ThreadLocal 정리
        }
    }
    
    /**
     * 메모리 사용량 모니터
     */
    static class MemoryUsageMonitor {
        private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        private final List<Long> memorySnapshots = new ArrayList<>();
        private long startTime;
        
        public void startMonitoring() {
            startTime = System.currentTimeMillis();
            // 초기 메모리 상태 기록
            recordMemorySnapshot();
        }
        
        private void recordMemorySnapshot() {
            MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
            memorySnapshots.add(heapUsage.getUsed());
        }
        
        public MemoryUsageReport generateReport() {
            recordMemorySnapshot(); // 최종 스냅샷
            
            long maxMemory = memorySnapshots.stream().mapToLong(Long::longValue).max().orElse(0);
            long minMemory = memorySnapshots.stream().mapToLong(Long::longValue).min().orElse(0);
            double avgMemory = memorySnapshots.stream().mapToLong(Long::longValue).average().orElse(0);
            
            // 메모리 증가 추세 분석
            long initialMemory = memorySnapshots.get(0);
            long finalMemory = memorySnapshots.get(memorySnapshots.size() - 1);
            double memoryGrowth = finalMemory - initialMemory;
            
            // 누수 감지 로직
            boolean leakDetected = memoryGrowth > (initialMemory * 0.5); // 50% 이상 증가 시 누수 의심
            double leakRate = memoryGrowth > 0 ? memoryGrowth / (double) initialMemory : 0;
            double efficiency = 1.0 - Math.min(leakRate, 1.0);
            
            return MemoryUsageReport.builder()
                    .memoryLeakDetected(leakDetected)
                    .leakRate(leakRate)
                    .memoryEfficiency(efficiency)
                    .maxMemoryUsed(maxMemory)
                    .minMemoryUsed(minMemory)
                    .avgMemoryUsed((long) avgMemory)
                    .initialMemory(initialMemory)
                    .finalMemory(finalMemory)
                    .memoryGrowth(memoryGrowth)
                    .testDurationMs(System.currentTimeMillis() - startTime)
                    .build();
        }
    }
    
    /**
     * 메모리 사용량 리포트
     */
    @lombok.Builder
    @Data
    static class MemoryUsageReport {
        private boolean memoryLeakDetected;
        private double leakRate;
        private double memoryEfficiency;
        private long maxMemoryUsed;
        private long minMemoryUsed;
        private long avgMemoryUsed;
        private long initialMemory;
        private long finalMemory;
        private double memoryGrowth;
        private long testDurationMs;
    }
}
