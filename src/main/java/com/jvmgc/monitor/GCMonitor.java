package com.jvmgc.monitor;

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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 실시간 GC 모니터링 도구
 * 
 * 기능:
 * - 실시간 메모리 사용량 모니터링
 * - GC 통계 수집 및 분석
 * - 성능 이상 감지 및 알림
 * - 주기적 리포트 생성
 * 
 * 사용 방법:
 * ```java
 * GCMonitor monitor = new GCMonitor();
 * monitor.startMonitoring(5); // 5초마다 모니터링
 * 
 * // 현재 상태 확인
 * monitor.printCurrentGCStatus();
 * 
 * // 모니터링 중지
 * monitor.stopMonitoring();
 * ```
 */
@Component
@Slf4j
public class GCMonitor {
    
    /**
     * 메모리 관리 빈
     */
    private final MemoryMXBean memoryBean;
    
    /**
     * GC 관리 빈 목록
     */
    private final List<GarbageCollectorMXBean> gcBeans;
    
    /**
     * 모니터링 스케줄러
     */
    private ScheduledExecutorService scheduler;
    
    /**
     * 모니터링 활성화 상태
     */
    private volatile boolean isMonitoring = false;
    
    /**
     * 이전 GC 통계 (변화량 계산용)
     */
    private Map<String, GCSnapshot> previousGCStats;
    
    /**
     * 모니터링 시작 시간
     */
    private long monitoringStartTime;
    
    /**
     * 알림 임계치 설정
     */
    private final MonitoringThresholds thresholds;
    
    public GCMonitor() {
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        this.previousGCStats = new HashMap<>();
        this.thresholds = new MonitoringThresholds();
        
        log.info("GCMonitor 초기화 완료");
        log.info("사용 가능한 GC: {}", 
                gcBeans.stream()
                        .map(GarbageCollectorMXBean::getName)
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("없음"));
    }
    
    /**
     * 모니터링 시작
     * @param intervalSeconds 모니터링 간격 (초)
     */
    public void startMonitoring(int intervalSeconds) {
        if (isMonitoring) {
            log.warn("이미 모니터링이 실행 중입니다.");
            return;
        }
        
        log.info("GC 모니터링 시작 - 간격: {}초", intervalSeconds);
        
        this.monitoringStartTime = System.currentTimeMillis();
        this.isMonitoring = true;
        
        // 초기 GC 통계 수집
        collectInitialGCStats();
        
        // 스케줄러 시작
        this.scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "GC-Monitor");
            t.setDaemon(true);
            return t;
        });
        
        scheduler.scheduleAtFixedRate(
                this::performMonitoringCycle,
                intervalSeconds,
                intervalSeconds,
                TimeUnit.SECONDS
        );
        
        log.info("GC 모니터링이 시작되었습니다.");
    }
    
    /**
     * 모니터링 중지
     */
    public void stopMonitoring() {
        if (!isMonitoring) {
            log.warn("모니터링이 실행 중이지 않습니다.");
            return;
        }
        
        log.info("GC 모니터링 중지 중...");
        
        this.isMonitoring = false;
        
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        // 최종 리포트 생성
        generateFinalReport();
        
        log.info("GC 모니터링이 중지되었습니다.");
    }
    
    /**
     * 모니터링 사이클 수행
     */
    private void performMonitoringCycle() {
        try {
            // 현재 상태 수집
            MonitoringSnapshot snapshot = collectCurrentSnapshot();
            
            // 이상 상황 감지
            detectAnomalies(snapshot);
            
            // 주기적 로깅
            logMonitoringInfo(snapshot);
            
            // 이전 통계 업데이트
            updatePreviousStats(snapshot);
            
        } catch (Exception e) {
            log.error("모니터링 사이클 중 오류 발생", e);
        }
    }
    
    /**
     * 현재 모니터링 스냅샷 수집
     */
    private MonitoringSnapshot collectCurrentSnapshot() {
        // 메모리 사용량 수집
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
        
        // GC 통계 수집
        Map<String, GCSnapshot> currentGCStats = new HashMap<>();
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            String gcName = gcBean.getName();
            long collectionCount = gcBean.getCollectionCount();
            long collectionTime = gcBean.getCollectionTime();
            
            currentGCStats.put(gcName, new GCSnapshot(collectionCount, collectionTime));
        }
        
        return MonitoringSnapshot.builder()
                .timestamp(System.currentTimeMillis())
                .heapUsed(heapUsage.getUsed())
                .heapMax(heapUsage.getMax())
                .heapCommitted(heapUsage.getCommitted())
                .nonHeapUsed(nonHeapUsage.getUsed())
                .nonHeapMax(nonHeapUsage.getMax())
                .gcStats(currentGCStats)
                .build();
    }
    
    /**
     * 초기 GC 통계 수집
     */
    private void collectInitialGCStats() {
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            String gcName = gcBean.getName();
            long collectionCount = gcBean.getCollectionCount();
            long collectionTime = gcBean.getCollectionTime();
            
            previousGCStats.put(gcName, new GCSnapshot(collectionCount, collectionTime));
        }
        
        log.info("초기 GC 통계 수집 완료");
    }
    
    /**
     * 이상 상황 감지
     */
    private void detectAnomalies(MonitoringSnapshot snapshot) {
        // 힙 사용률 검사
        double heapUsageRatio = (double) snapshot.getHeapUsed() / snapshot.getHeapMax();
        if (heapUsageRatio > thresholds.getCriticalHeapUsageRatio()) {
            log.error("🚨 심각한 힙 사용률 감지: {}% (임계치: {}%)", 
                    String.format("%.2f", heapUsageRatio * 100), String.format("%.2f", thresholds.getCriticalHeapUsageRatio() * 100));
            // 실제 환경에서는 알림 시스템 연동
        } else if (heapUsageRatio > thresholds.getWarningHeapUsageRatio()) {
            log.warn("⚠️ 높은 힙 사용률 감지: {}% (경고 임계치: {}%)", 
                    String.format("%.2f", heapUsageRatio * 100), String.format("%.2f", thresholds.getWarningHeapUsageRatio() * 100));
        }
        
        // GC 빈도 검사
        for (Map.Entry<String, GCSnapshot> entry : snapshot.getGcStats().entrySet()) {
            String gcName = entry.getKey();
            GCSnapshot current = entry.getValue();
            GCSnapshot previous = previousGCStats.get(gcName);
            
            if (previous != null) {
                long countDelta = current.getCollectionCount() - previous.getCollectionCount();
                long timeDelta = current.getCollectionTime() - previous.getCollectionTime();
                
                // GC 스래싱 감지
                if (countDelta > thresholds.getMaxGCCountPerInterval()) {
                    log.warn("⚠️ GC 스래싱 감지 - {}: {}회 실행 (임계치: {}회)", 
                            gcName, countDelta, thresholds.getMaxGCCountPerInterval());
                }
                
                // 긴 GC pause 감지
                if (countDelta > 0) {
                    double avgPauseMs = (double) timeDelta / countDelta;
                    if (avgPauseMs > thresholds.getMaxAvgGCPauseMs()) {
                        log.warn("⚠️ 긴 GC pause 감지 - {}: 평균 {}ms (임계치: {}ms)", 
                                gcName, String.format("%.2f", avgPauseMs), String.format("%.2f", thresholds.getMaxAvgGCPauseMs()));
                    }
                }
            }
        }
    }
    
    /**
     * 모니터링 정보 로깅
     */
    private void logMonitoringInfo(MonitoringSnapshot snapshot) {
        // 메모리 사용량 로깅
        double heapUsageRatio = (double) snapshot.getHeapUsed() / snapshot.getHeapMax();
        log.info("📊 힙 메모리: {} / {} ({}%)", 
                formatBytes(snapshot.getHeapUsed()),
                formatBytes(snapshot.getHeapMax()),
                String.format("%.2f", heapUsageRatio * 100));
        
        // GC 통계 로깅
        for (Map.Entry<String, GCSnapshot> entry : snapshot.getGcStats().entrySet()) {
            String gcName = entry.getKey();
            GCSnapshot current = entry.getValue();
            GCSnapshot previous = previousGCStats.get(gcName);
            
            if (previous != null) {
                long countDelta = current.getCollectionCount() - previous.getCollectionCount();
                long timeDelta = current.getCollectionTime() - previous.getCollectionTime();
                
                if (countDelta > 0) {
                    double avgPauseMs = (double) timeDelta / countDelta;
                    log.info("🔄 {}: {}회 실행, 총 {}ms, 평균 {}ms", 
                            gcName, countDelta, timeDelta, String.format("%.2f", avgPauseMs));
                }
            }
        }
    }
    
    /**
     * 이전 통계 업데이트
     */
    private void updatePreviousStats(MonitoringSnapshot snapshot) {
        previousGCStats.clear();
        previousGCStats.putAll(snapshot.getGcStats());
    }
    
    /**
     * 현재 GC 상태를 간단히 출력하는 유틸리티 메서드
     */
    public void printCurrentGCStatus() {
        log.info("=== 현재 GC 상태 ===");
        
        // 메모리 사용량
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        log.info("힙 메모리 사용량: {} / {} ({}%)", 
                formatBytes(heapUsage.getUsed()),
                formatBytes(heapUsage.getMax()),
                String.format("%.2f", (double) heapUsage.getUsed() / heapUsage.getMax() * 100));
        
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
        log.info("Non-Heap 메모리 사용량: {} / {}", 
                formatBytes(nonHeapUsage.getUsed()),
                formatBytes(nonHeapUsage.getMax()));
        
        // GC 통계
        log.info("=== GC 통계 ===");
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            log.info("{}: 실행 횟수 {}, 총 시간 {}ms", 
                    gcBean.getName(), 
                    gcBean.getCollectionCount(), 
                    gcBean.getCollectionTime());
        }
    }
    
    /**
     * 상세한 GC 분석 리포트 생성
     */
    public void generateDetailedReport() {
        log.info("=== 상세 GC 분석 리포트 ===");
        
        if (!isMonitoring) {
            log.warn("모니터링이 실행 중이지 않습니다. 현재 상태만 표시합니다.");
            printCurrentGCStatus();
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        long monitoringDurationMs = currentTime - monitoringStartTime;
        double monitoringDurationHours = monitoringDurationMs / (1000.0 * 3600);
        
        log.info("모니터링 기간: {}시간", String.format("%.2f", monitoringDurationHours));
        
        // 현재 메모리 상태
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        double heapUsageRatio = (double) heapUsage.getUsed() / heapUsage.getMax();
        
        log.info("현재 힙 사용률: {}%", String.format("%.2f", heapUsageRatio * 100));
        
        // GC 성능 분석
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            String gcName = gcBean.getName();
            long totalCollections = gcBean.getCollectionCount();
            long totalTime = gcBean.getCollectionTime();
            
            if (totalCollections > 0) {
                double avgPauseMs = (double) totalTime / totalCollections;
                double collectionsPerHour = monitoringDurationHours > 0 ? 
                        totalCollections / monitoringDurationHours : 0;
                double gcOverheadPercent = monitoringDurationMs > 0 ? 
                        (double) totalTime / monitoringDurationMs * 100 : 0;
                
                log.info("=== {} 분석 ===", gcName);
                log.info("  총 실행 횟수: {}", totalCollections);
                log.info("  총 실행 시간: {}ms", totalTime);
                log.info("  평균 pause 시간: {}ms", String.format("%.2f", avgPauseMs));
                log.info("  시간당 실행 횟수: {}회", String.format("%.1f", collectionsPerHour));
                log.info("  GC 오버헤드: {}%", String.format("%.2f", gcOverheadPercent));
                
                // 성능 평가
                evaluateGCPerformance(gcName, avgPauseMs, gcOverheadPercent);
            }
        }
        
        // 권장사항 제시
        provideRecommendations(heapUsageRatio);
    }
    
    /**
     * GC 성능 평가
     */
    private void evaluateGCPerformance(String gcName, double avgPauseMs, double gcOverheadPercent) {
        log.info("  성능 평가:");
        
        // Pause time 평가
        if (avgPauseMs < 10) {
            log.info("    ✅ 매우 낮은 지연시간");
        } else if (avgPauseMs < 100) {
            log.info("    ✅ 낮은 지연시간");
        } else if (avgPauseMs < 500) {
            log.info("    ℹ️ 보통 지연시간");
        } else {
            log.info("    ⚠️ 높은 지연시간");
        }
        
        // GC 오버헤드 평가
        if (gcOverheadPercent < 2) {
            log.info("    ✅ 낮은 GC 오버헤드");
        } else if (gcOverheadPercent < 5) {
            log.info("    ℹ️ 보통 GC 오버헤드");
        } else if (gcOverheadPercent < 10) {
            log.info("    ⚠️ 높은 GC 오버헤드");
        } else {
            log.info("    🚨 매우 높은 GC 오버헤드");
        }
    }
    
    /**
     * 권장사항 제시
     */
    private void provideRecommendations(double heapUsageRatio) {
        log.info("=== 권장사항 ===");
        
        if (heapUsageRatio > 0.8) {
            log.info("• 힙 크기 증가를 고려하세요 (-Xmx 값 증가)");
            log.info("• 메모리 누수 가능성을 확인하세요");
            log.info("• 힙 덤프 분석을 수행하세요");
        }
        
        // GC별 권장사항
        String currentGC = detectCurrentGC();
        switch (currentGC.toLowerCase()) {
            case "parallel":
                log.info("• 지연시간이 중요한 경우 G1GC 사용을 고려하세요");
                break;
            case "g1":
                log.info("• MaxGCPauseMillis 튜닝을 고려하세요");
                log.info("• 힙 크기가 4GB 미만인 경우 Parallel GC 고려");
                break;
            case "zgc":
                log.info("• 대용량 힙에서 최적의 성능을 발휘합니다");
                break;
        }
        
        log.info("• 정기적인 GC 로그 분석을 수행하세요");
        log.info("• 애플리케이션별 GC 튜닝을 고려하세요");
    }
    
    /**
     * 현재 사용 중인 GC 감지
     */
    private String detectCurrentGC() {
        String gcNames = gcBeans.stream()
                .map(GarbageCollectorMXBean::getName)
                .reduce((a, b) -> a + " " + b)
                .orElse("")
                .toLowerCase();
        
        if (gcNames.contains("zgc")) return "ZGC";
        if (gcNames.contains("g1")) return "G1";
        if (gcNames.contains("parallel")) return "Parallel";
        if (gcNames.contains("serial")) return "Serial";
        return "Unknown";
    }
    
    /**
     * 최종 리포트 생성
     */
    private void generateFinalReport() {
        log.info("=== 최종 모니터링 리포트 ===");
        generateDetailedReport();
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
     * 모니터링 상태 확인
     */
    public boolean isMonitoring() {
        return isMonitoring;
    }
    
    // === 내부 데이터 클래스들 ===
    
    /**
     * GC 스냅샷
     */
    @AllArgsConstructor
    @Data
    private static class GCSnapshot {
        private long collectionCount;
        private long collectionTime;
    }
    
    /**
     * 모니터링 스냅샷
     */
    @Builder
    @Data
    private static class MonitoringSnapshot {
        private long timestamp;
        private long heapUsed;
        private long heapMax;
        private long heapCommitted;
        private long nonHeapUsed;
        private long nonHeapMax;
        private Map<String, GCSnapshot> gcStats;
    }
    
    /**
     * 모니터링 임계치 설정
     */
    @Data
    private static class MonitoringThresholds {
        private double warningHeapUsageRatio = 0.8;    // 80%
        private double criticalHeapUsageRatio = 0.9;   // 90%
        private int maxGCCountPerInterval = 10;         // 인터벌당 최대 GC 횟수
        private double maxAvgGCPauseMs = 500.0;         // 최대 평균 pause 시간 (ms)
    }
}
