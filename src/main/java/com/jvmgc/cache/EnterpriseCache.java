package com.jvmgc.cache;

import lombok.extern.slf4j.Slf4j;
import net.openhft.chronicle.map.ChronicleMap;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 대용량 캐시 시스템 설계 및 구현
 * 
 * 설계 목표:
 * - 대용량 데이터 캐싱 (수백만 ~ 수천만 엔트리)
 * - 높은 가용성 및 성능
 * - GC 압박 최소화
 * 
 * 검증 방법:
 * - 부하 테스트를 통한 GC 영향 분석
 * - 메모리 사용량 모니터링
 * - 캐시 히트율 및 성능 측정
 * 
 * 아키텍처:
 * - L1 Cache: On-heap, WeakReference 기반, 빠른 접근
 * - L2 Cache: Off-heap, Chronicle Map 기반, 대용량 저장
 */
@Component
@Slf4j
public class EnterpriseCache {
    
    /**
     * L1 캐시 - On-heap 메모리 사용
     * - WeakReference 사용으로 GC 압박 시 자동 해제
     * - 가장 빠른 접근 속도
     * - 크기 제한: 10,000개 엔트리
     */
    private final Map<String, WeakReference<CacheEntry>> l1Cache;
    
    /**
     * L2 캐시 - Off-heap 메모리 사용
     * - Chronicle Map 기반으로 GC 영향 없음
     * - 대용량 저장 가능
     * - 크기 제한: 10,000,000개 엔트리
     */
    private final ChronicleMap<String, byte[]> l2Cache;
    
    /**
     * 메모리 사용량 모니터링을 위한 MXBean
     */
    private final MemoryMXBean memoryBean;
    
    /**
     * 캐시 성능 메트릭
     */
    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong l1Hits = new AtomicLong(0);
    private final AtomicLong l2Hits = new AtomicLong(0);
    private final AtomicLong evictions = new AtomicLong(0);
    
    /**
     * 백그라운드 작업을 위한 스케줄러
     */
    private final ScheduledExecutorService scheduler;
    
    /**
     * 캐시 설정
     */
    private static final int L1_MAX_SIZE = 10_000;
    private static final int L2_MAX_SIZE = 10_000_000;
    private static final double MEMORY_PRESSURE_THRESHOLD = 0.8; // 80%
    private static final long CLEANUP_INTERVAL_SECONDS = 30;
    
    public EnterpriseCache() {
        log.info("EnterpriseCache 초기화 시작");
        
        // L1 캐시 초기화 (On-heap)
        this.l1Cache = new ConcurrentHashMap<>(L1_MAX_SIZE);
        log.info("L1 캐시 초기화 완료 - 최대 크기: {}", L1_MAX_SIZE);
        
        // L2 캐시 초기화 (Off-heap)
        try {
            this.l2Cache = ChronicleMap
                    .of(String.class, byte[].class)
                    .entries(L2_MAX_SIZE)
                    .averageKeySize(50)        // 평균 키 크기 50바이트
                    .averageValueSize(1024)    // 평균 값 크기 1KB
                    .create();
            log.info("L2 캐시 초기화 완료 - 최대 크기: {}", L2_MAX_SIZE);
        } catch (IOException e) {
            log.error("L2 캐시 초기화 실패", e);
            throw new RuntimeException("L2 캐시 초기화 실패", e);
        }
        
        // 메모리 모니터링 초기화
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        
        // 백그라운드 스케줄러 초기화
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "EnterpriseCache-Scheduler");
            t.setDaemon(true);
            return t;
        });
        
        // 메모리 압박 모니터링 시작
        scheduleMemoryPressureMonitoring();
        
        // 만료된 엔트리 정리 스케줄링
        scheduleExpiredEntryCleanup();
        
        log.info("EnterpriseCache 초기화 완료");
    }
    
    /**
     * 캐시에서 값 조회
     * - L1 → L2 순서로 검색
     * - 캐시 히트 시 L1으로 승격
     * 
     * @param key 조회할 키
     * @return 캐시된 값 (Optional)
     */
    public Optional<Object> get(String key) {
        if (key == null) {
            return Optional.empty();
        }
        
        // L1 캐시 확인
        WeakReference<CacheEntry> ref = l1Cache.get(key);
        if (ref != null) {
            CacheEntry entry = ref.get();
            if (entry != null && !entry.isExpired()) {
                entry.recordAccess();
                cacheHits.incrementAndGet();
                l1Hits.incrementAndGet();
                log.debug("L1 캐시 히트: {}", key);
                return Optional.of(entry.getValue());
            } else {
                // 만료되었거나 GC된 엔트리 제거
                l1Cache.remove(key);
            }
        }
        
        // L2 캐시 확인
        byte[] data = l2Cache.get(key);
        if (data != null) {
            try {
                Object value = deserialize(data);
                
                // L1으로 승격 (메모리 압박이 심하지 않은 경우에만)
                if (!isMemoryPressureHigh()) {
                    CacheEntry entry = new CacheEntry(value);
                    l1Cache.put(key, new WeakReference<>(entry));
                }
                
                cacheHits.incrementAndGet();
                l2Hits.incrementAndGet();
                log.debug("L2 캐시 히트: {}", key);
                return Optional.of(value);
            } catch (Exception e) {
                log.warn("L2 캐시 역직렬화 실패: {}", key, e);
                l2Cache.remove(key); // 손상된 데이터 제거
            }
        }
        
        // 캐시 미스
        cacheMisses.incrementAndGet();
        log.debug("캐시 미스: {}", key);
        return Optional.empty();
    }
    
    /**
     * 캐시에 값 저장
     * - L1과 L2에 동시 저장
     * - 메모리 압박 시 L1 저장 생략
     * 
     * @param key 저장할 키
     * @param value 저장할 값
     * @param ttlSeconds TTL (초 단위, 0이면 만료되지 않음)
     */
    public void put(String key, Object value, long ttlSeconds) {
        if (key == null || value == null) {
            return;
        }
        
        try {
            // 직렬화
            byte[] serializedValue = serialize(value);
            
            // L2 캐시에 저장 (항상 저장)
            l2Cache.put(key, serializedValue);
            
            // L1 캐시에 저장 (메모리 압박이 심하지 않은 경우에만)
            if (!isMemoryPressureHigh()) {
                CacheEntry entry = new CacheEntry(value, ttlSeconds);
                l1Cache.put(key, new WeakReference<>(entry));
                
                // L1 캐시 크기 제한 확인
                if (l1Cache.size() > L1_MAX_SIZE) {
                    evictFromL1Cache();
                }
            }
            
            log.debug("캐시 저장 완료: {} (TTL: {}s)", key, ttlSeconds);
            
        } catch (Exception e) {
            log.error("캐시 저장 실패: {}", key, e);
        }
    }
    
    /**
     * 캐시에 값 저장 (만료되지 않음)
     */
    public void put(String key, Object value) {
        put(key, value, 0);
    }
    
    /**
     * 캐시에서 키 제거
     * @param key 제거할 키
     * @return 제거 성공 여부
     */
    public boolean remove(String key) {
        if (key == null) {
            return false;
        }
        
        boolean l1Removed = l1Cache.remove(key) != null;
        boolean l2Removed = l2Cache.remove(key) != null;
        
        log.debug("캐시 제거: {} (L1: {}, L2: {})", key, l1Removed, l2Removed);
        return l1Removed || l2Removed;
    }
    
    /**
     * 캐시 전체 정리
     */
    public void clear() {
        log.info("캐시 전체 정리 시작");
        
        l1Cache.clear();
        l2Cache.clear();
        
        // 메트릭 초기화
        cacheHits.set(0);
        cacheMisses.set(0);
        l1Hits.set(0);
        l2Hits.set(0);
        evictions.set(0);
        
        log.info("캐시 전체 정리 완료");
    }
    
    /**
     * 캐시 통계 정보 반환
     */
    public CacheStats getStats() {
        long totalRequests = cacheHits.get() + cacheMisses.get();
        double hitRate = totalRequests > 0 ? (double) cacheHits.get() / totalRequests : 0.0;
        
        return CacheStats.builder()
                .l1Size(l1Cache.size())
                .l2Size(l2Cache.size())
                .totalRequests(totalRequests)
                .cacheHits(cacheHits.get())
                .cacheMisses(cacheMisses.get())
                .l1Hits(l1Hits.get())
                .l2Hits(l2Hits.get())
                .hitRate(hitRate)
                .evictions(evictions.get())
                .memoryUsage(getCurrentMemoryUsage())
                .build();
    }
    
    /**
     * 메모리 압박 모니터링 스케줄링
     */
    private void scheduleMemoryPressureMonitoring() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (isMemoryPressureHigh()) {
                    log.warn("높은 메모리 사용률 감지: {:.2f}%", getCurrentMemoryUsageRatio() * 100);
                    evictExpiredEntries();
                    evictFromL1Cache();
                }
            } catch (Exception e) {
                log.error("메모리 압박 모니터링 중 오류 발생", e);
            }
        }, 0, CLEANUP_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }
    
    /**
     * 만료된 엔트리 정리 스케줄링
     */
    private void scheduleExpiredEntryCleanup() {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                evictExpiredEntries();
            } catch (Exception e) {
                log.error("만료 엔트리 정리 중 오류 발생", e);
            }
        }, CLEANUP_INTERVAL_SECONDS, CLEANUP_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }
    
    /**
     * 만료된 엔트리 제거
     */
    private void evictExpiredEntries() {
        log.debug("만료된 엔트리 정리 시작");
        
        int evictedCount = 0;
        
        // L1 캐시에서 만료된 엔트리 제거
        var iterator = l1Cache.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            WeakReference<CacheEntry> ref = entry.getValue();
            CacheEntry cacheEntry = ref.get();
            
            if (cacheEntry == null || cacheEntry.isExpired()) {
                iterator.remove();
                evictedCount++;
            }
        }
        
        if (evictedCount > 0) {
            evictions.addAndGet(evictedCount);
            log.debug("만료된 엔트리 {} 개 제거", evictedCount);
        }
    }
    
    /**
     * L1 캐시에서 LRU 기반 제거
     */
    private void evictFromL1Cache() {
        if (l1Cache.size() <= L1_MAX_SIZE) {
            return;
        }
        
        log.debug("L1 캐시 크기 제한 초과, LRU 제거 시작");
        
        // 간단한 LRU 구현: 가장 오래된 엔트리들 제거
        int targetSize = (int) (L1_MAX_SIZE * 0.8); // 80%까지 줄임
        int toRemove = l1Cache.size() - targetSize;
        
        l1Cache.entrySet().stream()
                .limit(toRemove)
                .map(Map.Entry::getKey)
                .forEach(l1Cache::remove);
        
        evictions.addAndGet(toRemove);
        log.debug("L1 캐시에서 {} 개 엔트리 제거", toRemove);
    }
    
    /**
     * 메모리 압박 상태 확인
     */
    private boolean isMemoryPressureHigh() {
        return getCurrentMemoryUsageRatio() > MEMORY_PRESSURE_THRESHOLD;
    }
    
    /**
     * 현재 메모리 사용률 반환
     */
    private double getCurrentMemoryUsageRatio() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        return (double) heapUsage.getUsed() / heapUsage.getMax();
    }
    
    /**
     * 현재 메모리 사용량 정보 반환
     */
    private MemoryUsageInfo getCurrentMemoryUsage() {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        return MemoryUsageInfo.builder()
                .used(heapUsage.getUsed())
                .max(heapUsage.getMax())
                .committed(heapUsage.getCommitted())
                .usageRatio(getCurrentMemoryUsageRatio())
                .build();
    }
    
    /**
     * 객체 직렬화 (간단한 구현)
     */
    private byte[] serialize(Object obj) throws Exception {
        // 실제 환경에서는 더 효율적인 직렬화 라이브러리 사용 권장
        // (예: Kryo, FST, Protocol Buffers 등)
        
        if (obj instanceof String) {
            return ((String) obj).getBytes("UTF-8");
        } else if (obj instanceof byte[]) {
            return (byte[]) obj;
        } else {
            // 간단한 toString 기반 직렬화 (데모용)
            return obj.toString().getBytes("UTF-8");
        }
    }
    
    /**
     * 객체 역직렬화 (간단한 구현)
     */
    private Object deserialize(byte[] data) throws Exception {
        // 실제 환경에서는 타입 정보를 포함한 역직렬화 필요
        return new String(data, "UTF-8");
    }
    
    /**
     * 리소스 정리
     */
    public void shutdown() {
        log.info("EnterpriseCache 종료 시작");
        
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        clear();
        l2Cache.close();
        
        log.info("EnterpriseCache 종료 완료");
    }
    
    // === 내부 데이터 클래스들 ===
    
    /**
     * 캐시 통계 정보
     */
    @lombok.Builder
    @lombok.Data
    public static class CacheStats {
        private int l1Size;
        private long l2Size;
        private long totalRequests;
        private long cacheHits;
        private long cacheMisses;
        private long l1Hits;
        private long l2Hits;
        private double hitRate;
        private long evictions;
        private MemoryUsageInfo memoryUsage;
    }
    
    /**
     * 메모리 사용량 정보
     */
    @lombok.Builder
    @lombok.Data
    public static class MemoryUsageInfo {
        private long used;
        private long max;
        private long committed;
        private double usageRatio;
    }
}
