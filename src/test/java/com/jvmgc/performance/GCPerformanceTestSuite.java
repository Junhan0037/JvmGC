package com.jvmgc.performance;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnJre;
import org.junit.jupiter.api.condition.JRE;
import org.springframework.stereotype.Component;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 다양한 GC 알고리즘의 성능을 비교 테스트하는 프레임워크
 * 
 * 테스트 환경: 가상 워크로드를 통한 벤치마크
 * 
 * 지원하는 GC 알고리즘:
 * - Parallel GC: 높은 처리량, 긴 pause time
 * - G1 GC: 균형잡힌 성능, 예측 가능한 pause time
 * - ZGC: 매우 낮은 지연시간 (Java 17+)
 * - Shenandoah GC: 낮은 지연시간 (OpenJDK)
 * 
 * 실행 방법:
 * 1. JVM 옵션을 다르게 설정하여 각 테스트 실행
 * 2. 결과 비교 분석
 * 
 * 예시 JVM 옵션:
 * - Parallel GC: -XX:+UseParallelGC -Xms4g -Xmx4g
 * - G1 GC: -XX:+UseG1GC -Xms4g -Xmx4g -XX:MaxGCPauseMillis=100
 * - ZGC: -XX:+UseZGC -Xms4g -Xmx4g (Java 17+)
 */
@Component
@Slf4j
public class GCPerformanceTestSuite {
    
    private final MemoryMXBean memoryBean;
    private final List<GarbageCollectorMXBean> gcBeans;
    
    public GCPerformanceTestSuite() {
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        
        log.info("GC Performance Test Suite 초기화");
        log.info("현재 사용 중인 GC: {}", 
                gcBeans.stream()
                        .map(GarbageCollectorMXBean::getName)
                        .collect(Collectors.joining(", ")));
    }
    
    /**
     * 현재 JVM에서 사용 중인 GC 알고리즘 성능 테스트
     * - JVM 옵션에 따라 다른 GC가 테스트됨
     */
    @Test
    @DisplayName("현재 GC 알고리즘 성능 테스트")
    public void testCurrentGCPerformance() {
        log.info("=== 현재 GC 알고리즘 성능 테스트 시작 ===");
        
        String gcType = detectCurrentGCType();
        log.info("감지된 GC 타입: {}", gcType);
        
        GCTestResult result = runGCBenchmark(gcType, Duration.ofMinutes(2));
        
        log.info("=== 테스트 결과 ===");
        logTestResult(gcType, result);
        
        // 기본적인 성능 검증
        assertThat(result.getAvgPauseMs()).isLessThan(1000.0); // 1초 미만
        assertThat(result.getThroughputPercent()).isGreaterThan(80.0); // 80% 이상
        assertThat(result.getGcOverheadPercent()).isLessThan(20.0); // 20% 미만
        
        log.info("=== 현재 GC 알고리즘 성능 테스트 완료 ===");
    }
    
    /**
     * Parallel GC 성능 테스트
     * - 높은 처리량을 목표로 하는 GC
     * - 배치 처리에 적합
     */
    @Test
    @DisplayName("Parallel GC 성능 특성 분석")
    public void testParallelGCCharacteristics() {
        log.info("=== Parallel GC 성능 특성 분석 ===");
        
        String currentGC = detectCurrentGCType();
        if (!currentGC.toLowerCase().contains("parallel")) {
            log.warn("현재 GC가 Parallel GC가 아닙니다. 현재 GC: {}", currentGC);
            log.info("Parallel GC로 실행하려면 JVM 옵션 추가: -XX:+UseParallelGC");
        }
        
        GCTestResult result = runGCBenchmark("ParallelGC", Duration.ofMinutes(3));
        
        // Parallel GC 특성 검증
        if (currentGC.toLowerCase().contains("parallel")) {
            // Parallel GC는 처리량이 높지만 pause time이 길 수 있음
            assertThat(result.getThroughputPercent()).isGreaterThan(90.0);
            log.info("✅ Parallel GC 높은 처리량 확인: {}%", String.format("%.2f", result.getThroughputPercent()));
        }
        
        logTestResult("Parallel GC", result);
        analyzeGCCharacteristics("Parallel GC", result);
    }
    
    /**
     * G1 GC 성능 테스트
     * - 균형잡힌 성능과 예측 가능한 pause time
     * - 웹 애플리케이션에 적합
     */
    @Test
    @DisplayName("G1 GC 성능 특성 분석")
    public void testG1GCCharacteristics() {
        log.info("=== G1 GC 성능 특성 분석 ===");
        
        String currentGC = detectCurrentGCType();
        if (!currentGC.toLowerCase().contains("g1")) {
            log.warn("현재 GC가 G1 GC가 아닙니다. 현재 GC: {}", currentGC);
            log.info("G1 GC로 실행하려면 JVM 옵션 추가: -XX:+UseG1GC -XX:MaxGCPauseMillis=100");
        }
        
        GCTestResult result = runGCBenchmark("G1GC", Duration.ofMinutes(3));
        
        // G1 GC 특성 검증
        if (currentGC.toLowerCase().contains("g1")) {
            // G1 GC는 낮은 지연시간과 높은 처리량의 균형
            assertThat(result.getAvgPauseMs()).isLessThan(200.0); // 200ms 미만
            assertThat(result.getThroughputPercent()).isGreaterThan(95.0); // 95% 이상
            log.info("✅ G1 GC 균형잡힌 성능 확인 - Pause: {}ms, Throughput: {}%", 
                    String.format("%.2f", result.getAvgPauseMs()), String.format("%.2f", result.getThroughputPercent()));
        }
        
        logTestResult("G1 GC", result);
        analyzeGCCharacteristics("G1 GC", result);
    }
    
    /**
     * ZGC 성능 테스트 (Java 17+)
     * - 매우 낮은 지연시간 목표
     * - 실시간 애플리케이션에 적합
     */
    @Test
    @DisplayName("ZGC 성능 특성 분석 (Java 17+)")
    @EnabledOnJre({JRE.JAVA_17, JRE.JAVA_18, JRE.JAVA_19, JRE.JAVA_20, JRE.JAVA_21})
    public void testZGCCharacteristics() {
        log.info("=== ZGC 성능 특성 분석 ===");
        
        String currentGC = detectCurrentGCType();
        if (!currentGC.toLowerCase().contains("zgc")) {
            log.warn("현재 GC가 ZGC가 아닙니다. 현재 GC: {}", currentGC);
            log.info("ZGC로 실행하려면 JVM 옵션 추가: -XX:+UseZGC");
        }
        
        GCTestResult result = runGCBenchmark("ZGC", Duration.ofMinutes(3));
        
        // ZGC 특성 검증
        if (currentGC.toLowerCase().contains("zgc")) {
            // ZGC는 매우 낮은 지연시간 목표 (10ms 미만)
            assertThat(result.getAvgPauseMs()).isLessThan(10.0);
            assertThat(result.getThroughputPercent()).isGreaterThan(98.0);
            log.info("✅ ZGC 초저지연 성능 확인 - Pause: {}ms, Throughput: {}%", 
                    String.format("%.2f", result.getAvgPauseMs()), String.format("%.2f", result.getThroughputPercent()));
        }
        
        logTestResult("ZGC", result);
        analyzeGCCharacteristics("ZGC", result);
    }
    
    /**
     * 메모리 압박 상황에서의 GC 성능 테스트
     * - 힙 사용률 80% 이상에서의 GC 동작 분석
     */
    @Test
    @DisplayName("메모리 압박 상황 GC 성능 테스트")
    public void testGCUnderMemoryPressure() {
        log.info("=== 메모리 압박 상황 GC 성능 테스트 ===");
        
        String gcType = detectCurrentGCType();
        
        // 메모리 압박 상황 생성
        List<byte[]> memoryPressure = new ArrayList<>();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        long targetUsage = (long) (heapUsage.getMax() * 0.7); // 70%까지 채움
        
        log.info("메모리 압박 상황 생성 중... 목표: {}MB", targetUsage / (1024 * 1024));
        
        while (memoryBean.getHeapMemoryUsage().getUsed() < targetUsage) {
            memoryPressure.add(new byte[1024 * 1024]); // 1MB씩 할당
        }
        
        log.info("메모리 압박 상황 생성 완료 - 현재 사용량: {}MB", 
                memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024));
        
        // 메모리 압박 상황에서 워크로드 실행
        GCTestResult result = runGCBenchmark(gcType + "_MemoryPressure", Duration.ofMinutes(2));
        
        // 메모리 정리
        memoryPressure.clear();
        System.gc();
        
        logTestResult(gcType + " (메모리 압박)", result);
        
        // 메모리 압박 상황에서도 기본적인 성능 유지 검증
        assertThat(result.getGcOverheadPercent()).isLessThan(50.0); // 50% 미만
        
        log.info("=== 메모리 압박 상황 GC 성능 테스트 완료 ===");
    }
    
    /**
     * 동시성 워크로드에서의 GC 성능 테스트
     * - 멀티스레드 환경에서의 GC 영향 분석
     */
    @Test
    @DisplayName("동시성 워크로드 GC 성능 테스트")
    public void testGCWithConcurrentWorkload() {
        log.info("=== 동시성 워크로드 GC 성능 테스트 ===");
        
        String gcType = detectCurrentGCType();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        
        try {
            GCTestResult result = runConcurrentGCBenchmark(gcType, executor, Duration.ofMinutes(2));
            
            logTestResult(gcType + " (동시성)", result);
            
            // 동시성 환경에서의 기본 성능 검증
            assertThat(result.getThroughputPercent()).isGreaterThan(70.0); // 70% 이상
            
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        log.info("=== 동시성 워크로드 GC 성능 테스트 완료 ===");
    }
    
    /**
     * GC 벤치마크 실행
     * @param gcType GC 타입 (로깅용)
     * @param duration 테스트 지속 시간
     * @return 테스트 결과
     */
    private GCTestResult runGCBenchmark(String gcType, Duration duration) {
        log.info("{} 벤치마크 시작 - 지속시간: {}분", gcType, duration.toMinutes());
        
        // 워크로드 시뮬레이터 생성
        WorkloadSimulator simulator = new WorkloadSimulator();
        GCMetricsCollector collector = new GCMetricsCollector();
        
        // 메트릭 수집 시작
        collector.startCollection();
        
        // 워크로드 실행
        long startTime = System.currentTimeMillis();
        simulator.runWorkload(duration);
        long endTime = System.currentTimeMillis();
        
        // 결과 수집
        GCTestResult result = collector.getResults(endTime - startTime);
        
        log.info("{} 벤치마크 완료", gcType);
        return result;
    }
    
    /**
     * 동시성 GC 벤치마크 실행
     */
    private GCTestResult runConcurrentGCBenchmark(String gcType, ExecutorService executor, Duration duration) {
        log.info("{} 동시성 벤치마크 시작", gcType);
        
        GCMetricsCollector collector = new GCMetricsCollector();
        collector.startCollection();
        
        long startTime = System.currentTimeMillis();
        
        // 여러 스레드에서 동시에 워크로드 실행
        List<CompletableFuture<Void>> futures = IntStream.range(0, 8)
                .mapToObj(i -> CompletableFuture.runAsync(() -> {
                    WorkloadSimulator simulator = new WorkloadSimulator();
                    simulator.runWorkload(duration);
                }, executor))
                .collect(Collectors.toList());
        
        // 모든 작업 완료 대기
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        long endTime = System.currentTimeMillis();
        
        return collector.getResults(endTime - startTime);
    }
    
    /**
     * 현재 사용 중인 GC 타입 감지
     */
    private String detectCurrentGCType() {
        List<String> gcNames = gcBeans.stream()
                .map(GarbageCollectorMXBean::getName)
                .collect(Collectors.toList());
        
        String combinedNames = String.join(" ", gcNames).toLowerCase();
        
        if (combinedNames.contains("zgc")) {
            return "ZGC";
        } else if (combinedNames.contains("g1")) {
            return "G1GC";
        } else if (combinedNames.contains("parallel")) {
            return "ParallelGC";
        } else if (combinedNames.contains("shenandoah")) {
            return "ShenandoahGC";
        } else if (combinedNames.contains("serial")) {
            return "SerialGC";
        } else {
            return "Unknown (" + String.join(", ", gcNames) + ")";
        }
    }
    
    /**
     * 테스트 결과 로깅
     */
    private void logTestResult(String gcType, GCTestResult result) {
        log.info("=== {} 테스트 결과 ===", gcType);
        log.info("평균 GC Pause Time: {}ms", String.format("%.2f", result.getAvgPauseMs()));
        log.info("최대 GC Pause Time: {}ms", String.format("%.2f", result.getMaxPauseMs()));
        log.info("시간당 GC 횟수: {}", result.getGcCountPerHour());
        log.info("GC 오버헤드: {}%", String.format("%.2f", result.getGcOverheadPercent()));
        log.info("처리량: {}%", String.format("%.2f", result.getThroughputPercent()));
        log.info("총 테스트 시간: {}ms", result.getTotalTestTimeMs());
        log.info("총 GC 시간: {}ms", result.getTotalGcTimeMs());
    }
    
    /**
     * GC 특성 분석 및 권장사항 제시
     */
    private void analyzeGCCharacteristics(String gcType, GCTestResult result) {
        log.info("=== {} 특성 분석 ===", gcType);
        
        // 지연시간 분석
        if (result.getAvgPauseMs() < 10) {
            log.info("✅ 매우 낮은 지연시간 - 실시간 애플리케이션에 적합");
        } else if (result.getAvgPauseMs() < 100) {
            log.info("✅ 낮은 지연시간 - 웹 애플리케이션에 적합");
        } else if (result.getAvgPauseMs() < 500) {
            log.info("ℹ️ 보통 지연시간 - 일반적인 서버 애플리케이션에 적합");
        } else {
            log.warn("⚠️ 높은 지연시간 - 배치 처리에만 적합");
        }
        
        // 처리량 분석
        if (result.getThroughputPercent() > 98) {
            log.info("✅ 매우 높은 처리량");
        } else if (result.getThroughputPercent() > 95) {
            log.info("✅ 높은 처리량");
        } else if (result.getThroughputPercent() > 90) {
            log.info("ℹ️ 보통 처리량");
        } else {
            log.warn("⚠️ 낮은 처리량 - 튜닝 필요");
        }
        
        // 권장사항
        log.info("=== {} 권장사항 ===", gcType);
        switch (gcType.toLowerCase()) {
            case "parallelgc":
                log.info("• 배치 처리, 높은 처리량이 중요한 애플리케이션에 적합");
                log.info("• 지연시간보다 처리량을 우선시하는 경우 사용");
                break;
            case "g1gc":
                log.info("• 웹 애플리케이션, 균형잡힌 성능이 필요한 경우 적합");
                log.info("• 힙 크기가 4GB 이상인 경우 권장");
                break;
            case "zgc":
                log.info("• 실시간 시스템, 매우 낮은 지연시간이 필요한 경우 적합");
                log.info("• 대용량 힙(수십 GB 이상)에서 효과적");
                break;
        }
    }
    
    // === 내부 클래스들 ===
    
    /**
     * GC 테스트 결과를 담는 클래스
     */
    @Data
    public static class GCTestResult {
        private final double avgPauseMs;
        private final double maxPauseMs;
        private final int gcCountPerHour;
        private final double gcOverheadPercent;
        private final double throughputPercent;
        private final long totalTestTimeMs;
        private final long totalGcTimeMs;
        
        public GCTestResult(double avgPauseMs, double maxPauseMs, int gcCountPerHour, 
                           double gcOverheadPercent, double throughputPercent,
                           long totalTestTimeMs, long totalGcTimeMs) {
            this.avgPauseMs = avgPauseMs;
            this.maxPauseMs = maxPauseMs;
            this.gcCountPerHour = gcCountPerHour;
            this.gcOverheadPercent = gcOverheadPercent;
            this.throughputPercent = throughputPercent;
            this.totalTestTimeMs = totalTestTimeMs;
            this.totalGcTimeMs = totalGcTimeMs;
        }
    }
    
    /**
     * 워크로드 시뮬레이터 - 실제 애플리케이션 패턴 모방
     */
    static class WorkloadSimulator {
        private final Random random = new Random();
        
        public void runWorkload(Duration duration) {
            long endTime = System.currentTimeMillis() + duration.toMillis();
            
            while (System.currentTimeMillis() < endTime) {
                // 다양한 크기의 객체 생성 (실제 애플리케이션 패턴)
                simulateWebRequest();
                simulateDataProcessing();
                simulateCacheOperations();
                
                try {
                    Thread.sleep(1); // 1ms 간격
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        
        private void simulateWebRequest() {
            // HTTP 요청 처리 시뮬레이션 - 소량의 단기 객체
            Map<String, Object> requestData = new HashMap<>();
            requestData.put("userId", UUID.randomUUID().toString());
            requestData.put("timestamp", System.currentTimeMillis());
            requestData.put("data", generateRandomData(100 + random.nextInt(500)));
            // 요청 처리 후 객체는 GC 대상이 됨
        }
        
        private void simulateDataProcessing() {
            // 데이터 처리 시뮬레이션 - 중간 크기 객체들
            List<String> data = IntStream.range(0, 50 + random.nextInt(100))
                    .mapToObj(i -> "data_" + i + "_" + System.nanoTime())
                    .collect(Collectors.toList());
            
            // 데이터 변환 작업
            data.stream()
                    .filter(s -> s.length() > 10)
                    .map(String::toUpperCase)
                    .collect(Collectors.toSet());
            // 처리 완료 후 GC 대상
        }
        
        private void simulateCacheOperations() {
            // 캐시 연산 시뮬레이션 - 일부는 장수명 객체
            Map<String, byte[]> cache = new HashMap<>();
            
            for (int i = 0; i < 10; i++) {
                String key = "cache_key_" + random.nextInt(1000);
                byte[] value = generateRandomData(512 + random.nextInt(1024));
                cache.put(key, value);
            }
            
            // 일부 캐시 엔트리는 더 오래 유지됨 (실제 캐시 동작 모방)
            if (random.nextDouble() < 0.1) { // 10% 확률로 장기 보관
                // 실제로는 글로벌 캐시에 저장되어 더 오래 살아남음
            }
        }
        
        private byte[] generateRandomData(int size) {
            byte[] data = new byte[size];
            random.nextBytes(data);
            return data;
        }
    }
    
    /**
     * GC 메트릭 수집기
     */
    static class GCMetricsCollector {
        private final List<GarbageCollectorMXBean> gcBeans;
        private Map<String, Long> initialGCCounts;
        private Map<String, Long> initialGCTimes;
        
        public GCMetricsCollector() {
            this.gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        }
        
        public void startCollection() {
            initialGCCounts = new HashMap<>();
            initialGCTimes = new HashMap<>();
            
            for (GarbageCollectorMXBean gcBean : gcBeans) {
                initialGCCounts.put(gcBean.getName(), gcBean.getCollectionCount());
                initialGCTimes.put(gcBean.getName(), gcBean.getCollectionTime());
            }
        }
        
        public GCTestResult getResults(long totalTestTimeMs) {
            long totalGCCount = 0;
            long totalGCTime = 0;
            long maxPauseTime = 0;
            
            for (GarbageCollectorMXBean gcBean : gcBeans) {
                String gcName = gcBean.getName();
                long countDelta = gcBean.getCollectionCount() - initialGCCounts.get(gcName);
                long timeDelta = gcBean.getCollectionTime() - initialGCTimes.get(gcName);
                
                totalGCCount += countDelta;
                totalGCTime += timeDelta;
                
                // 평균 pause time 계산 (대략적)
                if (countDelta > 0) {
                    long avgPause = timeDelta / countDelta;
                    maxPauseTime = Math.max(maxPauseTime, avgPause);
                }
            }
            
            // 메트릭 계산
            double avgPauseMs = totalGCCount > 0 ? (double) totalGCTime / totalGCCount : 0;
            double maxPauseMs = maxPauseTime;
            int gcCountPerHour = (int) (totalGCCount * 3600000 / totalTestTimeMs);
            double gcOverheadPercent = totalTestTimeMs > 0 ? 
                    (double) totalGCTime / totalTestTimeMs * 100 : 0;
            double throughputPercent = 100.0 - gcOverheadPercent;
            
            return new GCTestResult(avgPauseMs, maxPauseMs, gcCountPerHour, 
                                  gcOverheadPercent, throughputPercent,
                                  totalTestTimeMs, totalGCTime);
        }
    }
}
