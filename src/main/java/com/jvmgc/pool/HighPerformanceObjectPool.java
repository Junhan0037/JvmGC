package com.jvmgc.pool;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 고성능 객체 풀링 구현
 * 
 * 설계 목표:
 * - 높은 처리량과 낮은 GC 압박
 * - 스레드 안전성 보장
 * - 메트릭 기반 모니터링
 * - 유연한 객체 생성 및 초기화 정책
 * 
 * 검증 방법:
 * - 부하 테스트를 통한 성능 측정
 * - GC 압박 감소 효과 분석
 * - 메모리 사용량 최적화 확인
 * 
 * 사용 예시:
 * ```java
 * // ByteBuffer 풀링
 * HighPerformanceObjectPool<ByteBuffer> bufferPool = 
 *     new HighPerformanceObjectPool<>(
 *         () -> ByteBuffer.allocateDirect(8192),
 *         ByteBuffer::clear,
 *         1000
 *     );
 * 
 * ByteBuffer buffer = bufferPool.acquire();
 * try {
 *     // 버퍼 사용
 * } finally {
 *     bufferPool.release(buffer);
 * }
 * ```
 * 
 * @param <T> 풀링할 객체 타입
 */
@Component
@Slf4j
public class HighPerformanceObjectPool<T> {
    
    /**
     * 객체 풀 - 스레드 안전한 큐 사용
     */
    private final Queue<T> pool;
    
    /**
     * 객체 생성 팩토리
     */
    private final Supplier<T> factory;
    
    /**
     * 객체 초기화 함수 - 재사용 전 상태 리셋
     */
    private final Consumer<T> resetFunction;
    
    /**
     * 현재 풀 크기 - 원자적 연산 보장
     */
    private final AtomicInteger poolSize = new AtomicInteger(0);
    
    /**
     * 최대 풀 크기
     */
    private final int maxPoolSize;
    
    /**
     * 풀 이름 - 로깅 및 메트릭에 사용
     */
    private final String poolName;
    
    // === 메트릭 수집 ===
    
    /**
     * 메트릭 레지스트리
     */
    @SuppressWarnings("unused")
    private final MeterRegistry meterRegistry;
    
    /**
     * 풀 히트 카운터 - 풀에서 객체를 성공적으로 가져온 횟수
     */
    private final Counter poolHits;
    
    /**
     * 풀 미스 카운터 - 풀이 비어서 새로 생성한 횟수
     */
    private final Counter poolMisses;
    
    /**
     * 객체 생성 카운터 - 총 생성된 객체 수
     */
    private final Counter objectsCreated;
    
    /**
     * 객체 반환 카운터 - 풀로 반환된 객체 수
     */
    private final Counter objectsReturned;
    
    /**
     * 객체 폐기 카운터 - 풀이 가득 차서 폐기된 객체 수
     */
    private final Counter objectsDiscarded;
    
    /**
     * 현재 풀 크기 게이지
     */
    @SuppressWarnings("unused")
    private final Gauge poolSizeGauge;
    
    /**
     * 생성자
     * @param factory 객체 생성 팩토리
     * @param resetFunction 객체 초기화 함수
     * @param maxPoolSize 최대 풀 크기
     */
    public HighPerformanceObjectPool(Supplier<T> factory, 
                                   Consumer<T> resetFunction, 
                                   int maxPoolSize) {
        this(factory, resetFunction, maxPoolSize, "DefaultPool", new SimpleMeterRegistry());
    }
    
    /**
     * 생성자 (메트릭 지원)
     * @param factory 객체 생성 팩토리
     * @param resetFunction 객체 초기화 함수
     * @param maxPoolSize 최대 풀 크기
     * @param poolName 풀 이름
     * @param meterRegistry 메트릭 레지스트리
     */
    public HighPerformanceObjectPool(Supplier<T> factory, 
                                   Consumer<T> resetFunction, 
                                   int maxPoolSize,
                                   String poolName,
                                   MeterRegistry meterRegistry) {
        this.factory = factory;
        this.resetFunction = resetFunction;
        this.maxPoolSize = maxPoolSize;
        this.poolName = poolName;
        this.meterRegistry = meterRegistry;
        
        // 스레드 안전한 큐 초기화
        this.pool = new ConcurrentLinkedQueue<>();
        
        // 메트릭 초기화
        this.poolHits = Counter.builder("object_pool_hits_total")
                .description("Number of successful object pool retrievals")
                .tag("pool", poolName)
                .register(meterRegistry);
        
        this.poolMisses = Counter.builder("object_pool_misses_total")
                .description("Number of object pool misses requiring new allocation")
                .tag("pool", poolName)
                .register(meterRegistry);
        
        this.objectsCreated = Counter.builder("object_pool_objects_created_total")
                .description("Total number of objects created")
                .tag("pool", poolName)
                .register(meterRegistry);
        
        this.objectsReturned = Counter.builder("object_pool_objects_returned_total")
                .description("Total number of objects returned to pool")
                .tag("pool", poolName)
                .register(meterRegistry);
        
        this.objectsDiscarded = Counter.builder("object_pool_objects_discarded_total")
                .description("Total number of objects discarded due to full pool")
                .tag("pool", poolName)
                .register(meterRegistry);
        
        this.poolSizeGauge = Gauge.builder("object_pool_size", this, pool -> (double) pool.poolSize.get())
                .description("Current size of the object pool")
                .tag("pool", poolName)
                .register(meterRegistry);
        
        // 풀 사전 워밍업
        warmUpPool();
        
        log.info("HighPerformanceObjectPool '{}' 초기화 완료 - 최대 크기: {}, 초기 크기: {}", 
                poolName, maxPoolSize, poolSize.get());
    }
    
    /**
     * 풀에서 객체 획득
     * - 풀에 객체가 있으면 반환
     * - 풀이 비어있으면 새로 생성
     * 
     * @return 사용 가능한 객체
     */
    public T acquire() {
        T object = pool.poll();
        
        if (object != null) {
            // 풀 히트 - 기존 객체 재사용
            poolSize.decrementAndGet();
            poolHits.increment();
            
            log.debug("풀 '{}' 히트 - 객체 재사용, 남은 풀 크기: {}", poolName, poolSize.get());
            return object;
        }
        
        // 풀 미스 - 새 객체 생성 필요
        poolMisses.increment();
        object = createNewObject();
        
        log.debug("풀 '{}' 미스 - 새 객체 생성", poolName);
        return object;
    }
    
    /**
     * 객체를 풀로 반환
     * - 풀이 가득 차지 않았으면 반환
     * - 풀이 가득 찬 경우 객체 폐기 (GC 대상)
     * 
     * @param object 반환할 객체
     */
    public void release(T object) {
        if (object == null) {
            log.warn("null 객체를 풀 '{}'로 반환하려고 시도", poolName);
            return;
        }
        
        if (poolSize.get() < maxPoolSize) {
            try {
                // 객체 상태 초기화
                resetFunction.accept(object);
                
                // 풀로 반환
                pool.offer(object);
                poolSize.incrementAndGet();
                objectsReturned.increment();
                
                log.debug("객체를 풀 '{}'로 반환 - 현재 풀 크기: {}", poolName, poolSize.get());
                
            } catch (Exception e) {
                log.error("객체 초기화 중 오류 발생 - 풀 '{}', 객체 폐기", poolName, e);
                objectsDiscarded.increment();
            }
        } else {
            // 풀이 가득 참 - 객체 폐기
            objectsDiscarded.increment();
            log.debug("풀 '{}' 가득 참 - 객체 폐기", poolName);
        }
    }
    
    /**
     * 새 객체 생성
     * @return 새로 생성된 객체
     */
    private T createNewObject() {
        try {
            T object = factory.get();
            objectsCreated.increment();
            log.debug("풀 '{}'에서 새 객체 생성", poolName);
            return object;
        } catch (Exception e) {
            log.error("풀 '{}'에서 객체 생성 실패", poolName, e);
            throw new RuntimeException("객체 생성 실패", e);
        }
    }
    
    /**
     * 풀 사전 워밍업
     * - 애플리케이션 시작 시 풀을 미리 채워서 초기 지연시간 감소
     */
    private void warmUpPool() {
        int initialSize = Math.min(maxPoolSize / 2, 10); // 최대 크기의 절반 또는 10개
        
        log.info("풀 '{}' 워밍업 시작 - 목표 크기: {}", poolName, initialSize);
        
        for (int i = 0; i < initialSize; i++) {
            try {
                T object = createNewObject();
                pool.offer(object);
                poolSize.incrementAndGet();
            } catch (Exception e) {
                log.warn("풀 '{}' 워밍업 중 객체 생성 실패 - 인덱스: {}", poolName, i, e);
                break;
            }
        }
        
        log.info("풀 '{}' 워밍업 완료 - 실제 크기: {}", poolName, poolSize.get());
    }
    
    /**
     * 풀 통계 정보 반환
     * @return 풀 통계
     */
    public PoolStats getStats() {
        return PoolStats.builder()
                .poolName(poolName)
                .currentSize(poolSize.get())
                .maxSize(maxPoolSize)
                .totalHits(poolHits.count())
                .totalMisses(poolMisses.count())
                .totalCreated(objectsCreated.count())
                .totalReturned(objectsReturned.count())
                .totalDiscarded(objectsDiscarded.count())
                .hitRate(calculateHitRate())
                .utilizationRate(calculateUtilizationRate())
                .build();
    }
    
    /**
     * 히트율 계산
     * @return 히트율 (0.0 ~ 1.0)
     */
    private double calculateHitRate() {
        double totalRequests = poolHits.count() + poolMisses.count();
        return totalRequests > 0 ? poolHits.count() / totalRequests : 0.0;
    }
    
    /**
     * 풀 사용률 계산
     * @return 사용률 (0.0 ~ 1.0)
     */
    private double calculateUtilizationRate() {
        return maxPoolSize > 0 ? (double) poolSize.get() / maxPoolSize : 0.0;
    }
    
    /**
     * 풀 정리 - 모든 객체 제거
     */
    public void clear() {
        log.info("풀 '{}' 정리 시작 - 현재 크기: {}", poolName, poolSize.get());
        
        pool.clear();
        poolSize.set(0);
        
        log.info("풀 '{}' 정리 완료", poolName);
    }
    
    /**
     * 풀 크기 조정
     * - 런타임에 풀 크기를 동적으로 조정
     * @param targetSize 목표 크기
     */
    public void resize(int targetSize) {
        if (targetSize < 0) {
            log.warn("잘못된 목표 크기: {} - 무시", targetSize);
            return;
        }
        
        int currentSize = poolSize.get();
        
        if (targetSize > currentSize) {
            // 풀 크기 증가
            int toAdd = Math.min(targetSize - currentSize, maxPoolSize - currentSize);
            log.info("풀 '{}' 크기 증가 - 현재: {}, 목표: {}, 추가: {}", 
                    poolName, currentSize, targetSize, toAdd);
            
            for (int i = 0; i < toAdd; i++) {
                try {
                    T object = createNewObject();
                    pool.offer(object);
                    poolSize.incrementAndGet();
                } catch (Exception e) {
                    log.warn("풀 크기 증가 중 객체 생성 실패", e);
                    break;
                }
            }
        } else if (targetSize < currentSize) {
            // 풀 크기 감소
            int toRemove = currentSize - targetSize;
            log.info("풀 '{}' 크기 감소 - 현재: {}, 목표: {}, 제거: {}", 
                    poolName, currentSize, targetSize, toRemove);
            
            for (int i = 0; i < toRemove; i++) {
                T object = pool.poll();
                if (object == null) {
                    break;
                }
                poolSize.decrementAndGet();
            }
        }
        
        log.info("풀 '{}' 크기 조정 완료 - 최종 크기: {}", poolName, poolSize.get());
    }
    
    /**
     * 풀 상태 검증
     * @return 검증 결과
     */
    public boolean validate() {
        boolean isValid = true;
        
        // 풀 크기 일관성 검증
        int actualSize = 0;
        for (@SuppressWarnings("unused") T ignored : pool) {
            actualSize++;
        }
        
        if (actualSize != poolSize.get()) {
            log.error("풀 '{}' 크기 불일치 - 실제: {}, 기록: {}", 
                    poolName, actualSize, poolSize.get());
            isValid = false;
        }
        
        // 풀 크기 제한 검증
        if (poolSize.get() > maxPoolSize) {
            log.error("풀 '{}' 크기 초과 - 현재: {}, 최대: {}", 
                    poolName, poolSize.get(), maxPoolSize);
            isValid = false;
        }
        
        return isValid;
    }
    
    /**
     * 풀 정보를 문자열로 반환
     */
    @Override
    public String toString() {
        return String.format("HighPerformanceObjectPool{name='%s', size=%d/%d, hitRate=%.2f%%}", 
                poolName, poolSize.get(), maxPoolSize, calculateHitRate() * 100);
    }
    
    // === 내부 데이터 클래스 ===
    
    /**
     * 풀 통계 정보
     */
    @Builder
    @Data
    public static class PoolStats {
        private String poolName;
        private int currentSize;
        private int maxSize;
        private double totalHits;
        private double totalMisses;
        private double totalCreated;
        private double totalReturned;
        private double totalDiscarded;
        private double hitRate;
        private double utilizationRate;
    }
}
