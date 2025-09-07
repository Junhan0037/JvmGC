package com.jvmgc.analyzer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * GC 메트릭 수집 및 분석 도구
 * 
 * 목적:
 * - 실시간 GC 성능 모니터링
 * - 메모리 사용 패턴 분석
 * - GC 튜닝을 위한 데이터 수집
 * 
 * 필요한 Dependencies:
 * - JUnit 5: 테스트 프레임워크
 * - Spring Boot Test: @Component 어노테이션 지원
 */
@Component
@Slf4j
public class GCAnalyzer {
    
    /**
     * 메모리 관리 빈 - 힙 메모리 정보 수집용
     */
    private final MemoryMXBean memoryBean;
    
    /**
     * GC 관리 빈 목록 - 각 GC의 성능 정보 수집용
     */
    private final List<GarbageCollectorMXBean> gcBeans;
    
    public GCAnalyzer() {
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        
        log.info("GCAnalyzer 초기화 완료");
        log.info("사용 가능한 GC: {}", 
                gcBeans.stream()
                        .map(GarbageCollectorMXBean::getName)
                        .collect(Collectors.joining(", ")));
    }
    
    /**
     * GC 영향 분석 실행
     * - 메모리 집약적 연산 전후의 GC 메트릭 비교
     * - GC 성능 영향 정량적 측정
     */
    public void analyzeGCImpact() {
        log.info("=== GC 영향 분석 시작 ===");
        
        // 1. 초기 GC 메트릭 수집
        log.info("초기 GC 메트릭 수집 중...");
        GCMetrics beforeMetrics = collectGCMetrics();
        logGCMetrics("분석 전", beforeMetrics);
        
        // 2. 메모리 집약적 연산 실행
        log.info("메모리 집약적 연산 실행 중...");
        long startTime = System.currentTimeMillis();
        runMemoryIntensiveOperations();
        long operationTime = System.currentTimeMillis() - startTime;
        
        // 3. 연산 후 GC 메트릭 수집
        log.info("연산 후 GC 메트릭 수집 중...");
        GCMetrics afterMetrics = collectGCMetrics();
        logGCMetrics("분석 후", afterMetrics);
        
        // 4. 결과 분석 및 리포트 생성
        log.info("결과 분석 중...");
        GCAnalysisReport report = analyzeResults(beforeMetrics, afterMetrics, operationTime);
        logAnalysisReport(report);
        
        log.info("=== GC 영향 분석 완료 ===");
    }
    
    /**
     * 현재 GC 메트릭 수집
     * @return 수집된 GC 메트릭 정보
     */
    private GCMetrics collectGCMetrics() {
        // 힙 메모리 사용량 수집
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
        
        // GC 통계 수집
        Map<String, GCStats> gcStats = new HashMap<>();
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            String gcName = gcBean.getName();
            long collectionCount = gcBean.getCollectionCount();
            long collectionTime = gcBean.getCollectionTime();
            
            gcStats.put(gcName, new GCStats(collectionCount, collectionTime));
        }
        
        return GCMetrics.builder()
                .timestamp(System.currentTimeMillis())
                .heapUsed(heapUsage.getUsed())
                .heapMax(heapUsage.getMax())
                .heapCommitted(heapUsage.getCommitted())
                .nonHeapUsed(nonHeapUsage.getUsed())
                .nonHeapMax(nonHeapUsage.getMax())
                .gcStats(gcStats)
                .build();
    }
    
    /**
     * 메모리 집약적 연산 실행
     * - 대용량 객체 생성 및 해제를 통한 GC 유발
     * - 다양한 크기의 객체 생성으로 실제 애플리케이션 패턴 모방
     */
    private void runMemoryIntensiveOperations() {
        log.debug("메모리 집약적 연산 시작");
        
        // Phase 1: 소량의 대형 객체 생성 (Old Generation 타겟)
        log.debug("Phase 1: 대형 객체 생성");
        for (int i = 0; i < 100; i++) {
            List<String> largeList = IntStream.range(0, 10_000)
                    .mapToObj(j -> "large_item_" + j + "_" + System.nanoTime())
                    .collect(Collectors.toList());
            
            // 일부 객체는 참조 유지 (Old Generation으로 승격 유도)
            if (i % 10 == 0) {
                // 10%는 잠시 참조 유지
                try {
                    Thread.sleep(1);
                    // largeList 참조를 유지하여 GC 지연
                    log.trace("Large list size: {}", largeList.size());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            // 나머지 90%는 즉시 GC 대상이 됨
        }
        
        // Phase 2: 대량의 소형 객체 생성 (Young Generation 타겟)
        log.debug("Phase 2: 소형 객체 대량 생성");
        for (int i = 0; i < 10_000; i++) {
            List<String> smallList = IntStream.range(0, 100)
                    .mapToObj(j -> "small_item_" + j)
                    .collect(Collectors.toList());
            
            // 의도적으로 참조 해제하여 GC 유발
            // smallList는 루프 종료 시 자동으로 GC 대상이 됨
            if (i % 1000 == 0) {
                log.trace("Small list batch {} completed, size: {}", i / 1000, smallList.size());
            }
        }
        
        // Phase 3: 문자열 연산 집약적 작업
        log.debug("Phase 3: 문자열 연산 집약적 작업");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 50_000; i++) {
            sb.append("문자열_").append(i).append("_").append(System.nanoTime());
            
            // 주기적으로 StringBuilder 초기화 (메모리 해제)
            if (i % 1000 == 0) {
                String result = sb.toString(); // 문자열 생성
                sb.setLength(0); // 버퍼 초기화
                // result는 GC 대상이 됨
                log.trace("String batch completed, length: {}", result.length());
            }
        }
        
        // Phase 4: Map/Set 컬렉션 연산
        log.debug("Phase 4: 컬렉션 연산");
        Map<String, Object> tempMap = new HashMap<>();
        for (int i = 0; i < 10_000; i++) {
            tempMap.put("key_" + i, "value_" + i + "_" + System.currentTimeMillis());
            
            // 주기적으로 맵 정리
            if (i % 1000 == 0) {
                tempMap.clear();
            }
        }
        
        log.debug("메모리 집약적 연산 완료");
    }
    
    /**
     * GC 분석 결과 생성
     * @param before 연산 전 메트릭
     * @param after 연산 후 메트릭
     * @param operationTimeMs 연산 수행 시간 (밀리초)
     * @return 분석 리포트
     */
    private GCAnalysisReport analyzeResults(GCMetrics before, GCMetrics after, long operationTimeMs) {
        // 메모리 사용량 변화 계산
        long heapUsedDelta = after.getHeapUsed() - before.getHeapUsed();
        double heapUsageRatioBefore = (double) before.getHeapUsed() / before.getHeapMax();
        double heapUsageRatioAfter = (double) after.getHeapUsed() / after.getHeapMax();
        
        // GC 통계 변화 계산
        long totalGCCountDelta = 0;
        long totalGCTimeDelta = 0;
        Map<String, GCStatsDelta> gcDeltas = new HashMap<>();
        
        for (String gcName : before.getGcStats().keySet()) {
            GCStats beforeStats = before.getGcStats().get(gcName);
            GCStats afterStats = after.getGcStats().get(gcName);
            
            if (beforeStats != null && afterStats != null) {
                long countDelta = afterStats.getCollectionCount() - beforeStats.getCollectionCount();
                long timeDelta = afterStats.getCollectionTime() - beforeStats.getCollectionTime();
                
                totalGCCountDelta += countDelta;
                totalGCTimeDelta += timeDelta;
                
                gcDeltas.put(gcName, new GCStatsDelta(countDelta, timeDelta));
            }
        }
        
        // GC 오버헤드 계산 (GC 시간 / 전체 시간)
        double gcOverheadPercent = operationTimeMs > 0 ? 
                (double) totalGCTimeDelta / operationTimeMs * 100 : 0;
        
        return GCAnalysisReport.builder()
                .operationTimeMs(operationTimeMs)
                .heapUsedDelta(heapUsedDelta)
                .heapUsageRatioBefore(heapUsageRatioBefore)
                .heapUsageRatioAfter(heapUsageRatioAfter)
                .totalGCCount(totalGCCountDelta)
                .totalGCTimeMs(totalGCTimeDelta)
                .gcOverheadPercent(gcOverheadPercent)
                .gcDeltas(gcDeltas)
                .build();
    }
    
    /**
     * GC 메트릭 로깅
     */
    private void logGCMetrics(String phase, GCMetrics metrics) {
        log.info("=== {} GC 메트릭 ===", phase);
        log.info("힙 메모리 사용량: {} / {} ({}%)", 
                formatBytes(metrics.getHeapUsed()),
                formatBytes(metrics.getHeapMax()),
                String.format("%.2f", (double) metrics.getHeapUsed() / metrics.getHeapMax() * 100));
        
        log.info("Non-Heap 메모리 사용량: {} / {}", 
                formatBytes(metrics.getNonHeapUsed()),
                formatBytes(metrics.getNonHeapMax()));
        
        for (Map.Entry<String, GCStats> entry : metrics.getGcStats().entrySet()) {
            GCStats stats = entry.getValue();
            log.info("{}: 실행 횟수 {}, 총 시간 {}ms", 
                    entry.getKey(), 
                    stats.getCollectionCount(), 
                    stats.getCollectionTime());
        }
    }
    
    /**
     * 분석 리포트 로깅
     */
    private void logAnalysisReport(GCAnalysisReport report) {
        log.info("=== GC 분석 리포트 ===");
        log.info("연산 수행 시간: {}ms", report.getOperationTimeMs());
        log.info("힙 메모리 변화: {}", formatBytes(report.getHeapUsedDelta()));
        log.info("힙 사용률 변화: {}% → {}%", 
                String.format("%.2f", report.getHeapUsageRatioBefore() * 100),
                String.format("%.2f", report.getHeapUsageRatioAfter() * 100));
        log.info("총 GC 실행 횟수: {}", report.getTotalGCCount());
        log.info("총 GC 시간: {}ms", report.getTotalGCTimeMs());
        log.info("GC 오버헤드: {}%", String.format("%.2f", report.getGcOverheadPercent()));
        
        // GC별 상세 정보
        for (Map.Entry<String, GCStatsDelta> entry : report.getGcDeltas().entrySet()) {
            GCStatsDelta delta = entry.getValue();
            log.info("{}: +{} 회, +{}ms", 
                    entry.getKey(), 
                    delta.getCountDelta(), 
                    delta.getTimeDelta());
        }
        
        // 성능 평가
        evaluatePerformance(report);
    }
    
    /**
     * 성능 평가 및 권장사항 제시
     */
    private void evaluatePerformance(GCAnalysisReport report) {
        log.info("=== 성능 평가 ===");
        
        // GC 오버헤드 평가
        if (report.getGcOverheadPercent() > 10) {
            log.warn("⚠️ GC 오버헤드가 높습니다 ({}%). 힙 크기 증가 또는 GC 튜닝을 고려하세요.", 
                    String.format("%.2f", report.getGcOverheadPercent()));
        } else if (report.getGcOverheadPercent() > 5) {
            log.info("ℹ️ GC 오버헤드가 보통 수준입니다 ({})%.", 
                    String.format("%.2f", report.getGcOverheadPercent()));
        } else {
            log.info("✅ GC 오버헤드가 낮습니다 ({})%.", 
                    String.format("%.2f", report.getGcOverheadPercent()));
        }
        
        // 힙 사용률 평가
        if (report.getHeapUsageRatioAfter() > 0.8) {
            log.warn("⚠️ 힙 사용률이 높습니다 ({}%). OutOfMemoryError 위험이 있습니다.", 
                    String.format("%.2f", report.getHeapUsageRatioAfter() * 100));
        } else if (report.getHeapUsageRatioAfter() > 0.6) {
            log.info("ℹ️ 힙 사용률이 보통 수준입니다 ({})%.", 
                    String.format("%.2f", report.getHeapUsageRatioAfter() * 100));
        } else {
            log.info("✅ 힙 사용률이 안정적입니다 ({})%.", 
                    String.format("%.2f", report.getHeapUsageRatioAfter() * 100));
        }
        
        // 권장사항
        log.info("=== 권장사항 ===");
        if (report.getGcOverheadPercent() > 10) {
            log.info("• 힙 크기 증가: -Xmx 값을 늘려보세요");
            log.info("• GC 알고리즘 변경: G1GC 또는 ZGC 사용을 고려하세요");
            log.info("• 코드 최적화: 불필요한 객체 생성을 줄이세요");
        }
        
        if (report.getHeapUsageRatioAfter() > 0.8) {
            log.info("• 메모리 누수 검사: 힙 덤프 분석을 수행하세요");
            log.info("• 객체 생명주기 검토: 장기간 참조되는 객체를 확인하세요");
        }
    }
    
    /**
     * 바이트 단위를 읽기 쉬운 형태로 포맷
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
    
    /**
     * 현재 GC 상태를 간단히 출력하는 유틸리티 메서드
     */
    public void printCurrentGCStatus() {
        GCMetrics current = collectGCMetrics();
        logGCMetrics("현재", current);
    }
    
    // === 내부 데이터 클래스들 ===
    
    /**
     * GC 메트릭 정보를 담는 클래스
     */
    @Builder
    @Data
    public static class GCMetrics {
        private long timestamp;
        private long heapUsed;
        private long heapMax;
        private long heapCommitted;
        private long nonHeapUsed;
        private long nonHeapMax;
        private Map<String, GCStats> gcStats;
    }
    
    /**
     * 개별 GC의 통계 정보
     */
    @AllArgsConstructor
    @Data
    public static class GCStats {
        private long collectionCount;
        private long collectionTime;
    }
    
    /**
     * GC 통계 변화량
     */
    @AllArgsConstructor
    @Data
    public static class GCStatsDelta {
        private long countDelta;
        private long timeDelta;
    }
    
    /**
     * GC 분석 리포트
     */
    @Builder
    @Data
    public static class GCAnalysisReport {
        private long operationTimeMs;
        private long heapUsedDelta;
        private double heapUsageRatioBefore;
        private double heapUsageRatioAfter;
        private long totalGCCount;
        private long totalGCTimeMs;
        private double gcOverheadPercent;
        private Map<String, GCStatsDelta> gcDeltas;
    }
}
