package com.jvmgc.cache;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 캐시 엔트리 클래스
 * - 캐시에 저장되는 개별 항목의 메타데이터와 값을 포함
 * - TTL(Time To Live) 기반 만료 처리 지원
 */
@Data
public class CacheEntry {
    
    /**
     * 캐시된 실제 값
     */
    private final Object value;
    
    /**
     * 캐시 엔트리 생성 시간
     */
    private final LocalDateTime createdAt;
    
    /**
     * 마지막 접근 시간 - LRU 정책에 사용
     */
    private volatile LocalDateTime lastAccessedAt;
    
    /**
     * 만료 시간 - null이면 만료되지 않음
     */
    private final LocalDateTime expiresAt;
    
    /**
     * 접근 횟수 - 통계 및 캐시 정책에 사용
     */
    private volatile long accessCount;
    
    /**
     * 엔트리 크기 추정값 (바이트) - 메모리 관리에 사용
     */
    private final long estimatedSize;
    
    /**
     * 생성자 - TTL 지정
     * @param value 캐시할 값
     * @param ttlSeconds TTL (초 단위, 0이면 만료되지 않음)
     */
    public CacheEntry(Object value, long ttlSeconds) {
        this.value = value;
        this.createdAt = LocalDateTime.now();
        this.lastAccessedAt = this.createdAt;
        this.expiresAt = ttlSeconds > 0 ? 
                this.createdAt.plusSeconds(ttlSeconds) : null;
        this.accessCount = 0;
        this.estimatedSize = estimateObjectSize(value);
    }
    
    /**
     * 기본 생성자 - 만료되지 않는 엔트리
     * @param value 캐시할 값
     */
    public CacheEntry(Object value) {
        this(value, 0);
    }
    
    /**
     * 엔트리 접근 시 호출 - 통계 업데이트
     */
    public void recordAccess() {
        this.lastAccessedAt = LocalDateTime.now();
        this.accessCount++;
    }
    
    /**
     * 엔트리가 만료되었는지 확인
     * @return 만료 여부
     */
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
    
    /**
     * 엔트리의 나이 (생성 후 경과 시간, 초 단위)
     * @return 경과 시간 (초)
     */
    public long getAgeInSeconds() {
        return java.time.Duration.between(createdAt, LocalDateTime.now()).getSeconds();
    }
    
    /**
     * 마지막 접근 후 경과 시간 (초 단위)
     * @return 경과 시간 (초)
     */
    public long getIdleTimeInSeconds() {
        return java.time.Duration.between(lastAccessedAt, LocalDateTime.now()).getSeconds();
    }
    
    /**
     * 객체 크기 추정 (간단한 휴리스틱)
     * - 실제 메모리 사용량과는 차이가 있을 수 있음
     * - 캐시 메모리 관리를 위한 대략적인 추정치
     * 
     * @param obj 크기를 추정할 객체
     * @return 추정 크기 (바이트)
     */
    private long estimateObjectSize(Object obj) {
        if (obj == null) {
            return 0;
        }
        
        // 기본 타입들의 크기
        if (obj instanceof String) {
            return ((String) obj).length() * 2L + 40; // UTF-16 + 오버헤드
        } else if (obj instanceof Integer) {
            return 24; // Integer 객체 오버헤드 포함
        } else if (obj instanceof Long) {
            return 24;
        } else if (obj instanceof Double) {
            return 24;
        } else if (obj instanceof Boolean) {
            return 16;
        } else if (obj instanceof byte[]) {
            return ((byte[]) obj).length + 16; // 배열 오버헤드
        } else if (obj instanceof java.util.List) {
            java.util.List<?> list = (java.util.List<?>) obj;
            return list.size() * 8L + 40; // 참조 크기 + ArrayList 오버헤드
        } else if (obj instanceof java.util.Map) {
            java.util.Map<?, ?> map = (java.util.Map<?, ?>) obj;
            return map.size() * 32L + 64; // Entry 오버헤드 + HashMap 오버헤드
        } else {
            // 일반 객체는 기본 크기로 추정
            return 64; // 평균적인 객체 크기 추정
        }
    }
    
    /**
     * 캐시 엔트리 정보를 문자열로 반환
     */
    @Override
    public String toString() {
        return String.format("CacheEntry{value=%s, age=%ds, idle=%ds, access=%d, size=%d bytes, expired=%s}",
                value != null ? value.getClass().getSimpleName() : "null",
                getAgeInSeconds(),
                getIdleTimeInSeconds(),
                accessCount,
                estimatedSize,
                isExpired());
    }
}
