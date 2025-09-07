package com.jvmgc.pool;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 메시지 처리 서비스 - 객체 풀링 실제 사용 예제
 * 
 * 사용 사례:
 * - 대용량 메시지 처리 시스템
 * - ByteBuffer 재사용을 통한 GC 압박 감소
 * - Direct Memory 사용으로 힙 메모리 절약
 * 
 * 성능 최적화 기법:
 * 1. ByteBuffer 풀링 - 객체 생성 비용 절약
 * 2. Direct Memory 사용 - GC 영향 최소화
 * 3. 메트릭 기반 모니터링 - 성능 추적
 */
@Service
@Slf4j
public class MessageProcessor {
    
    /**
     * ByteBuffer 풀 - Direct Memory 기반
     */
    private final HighPerformanceObjectPool<ByteBuffer> bufferPool;
    
    /**
     * StringBuilder 풀 - 문자열 처리용
     */
    private final HighPerformanceObjectPool<StringBuilder> stringBuilderPool;
    
    /**
     * 처리된 메시지 수 카운터
     */
    private final AtomicLong processedMessages = new AtomicLong(0);
    
    /**
     * 처리된 바이트 수 카운터
     */
    private final AtomicLong processedBytes = new AtomicLong(0);
    
    /**
     * 메트릭 레지스트리 - 객체 풀 성능 모니터링용
     */
    @SuppressWarnings("unused") // 실제로는 HighPerformanceObjectPool 생성자에서 사용됨
    private final MeterRegistry meterRegistry;
    
    public MessageProcessor() {
        this(new SimpleMeterRegistry());
    }
    
    public MessageProcessor(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        // ByteBuffer 풀 초기화
        this.bufferPool = new HighPerformanceObjectPool<>(
                () -> {
                    // Direct ByteBuffer 생성 - 힙 외부 메모리 사용
                    ByteBuffer buffer = ByteBuffer.allocateDirect(8192); // 8KB 버퍼
                    log.debug("새 Direct ByteBuffer 생성 - 크기: 8KB");
                    return buffer;
                },
                ByteBuffer::clear, // 재사용 전 버퍼 초기화
                1000,              // 최대 1000개 풀링
                "ByteBufferPool",
                meterRegistry
        );
        
        // StringBuilder 풀 초기화
        this.stringBuilderPool = new HighPerformanceObjectPool<>(
                () -> {
                    StringBuilder sb = new StringBuilder(1024); // 1KB 초기 용량
                    log.debug("새 StringBuilder 생성 - 초기 용량: 1KB");
                    return sb;
                },
                sb -> sb.setLength(0), // 재사용 전 내용 초기화
                500,                   // 최대 500개 풀링
                "StringBuilderPool",
                meterRegistry
        );
        
        log.info("MessageProcessor 초기화 완료");
        log.info("ByteBuffer 풀: {}", bufferPool);
        log.info("StringBuilder 풀: {}", stringBuilderPool);
    }
    
    /**
     * 메시지 처리 - 바이트 배열 입력
     * @param messageData 처리할 메시지 데이터
     * @return 처리 결과
     */
    public ProcessingResult processMessage(byte[] messageData) {
        if (messageData == null || messageData.length == 0) {
            log.warn("빈 메시지 데이터 - 처리 건너뜀");
            return ProcessingResult.builder()
                    .success(false)
                    .errorMessage("빈 메시지 데이터")
                    .build();
        }
        
        long startTime = System.nanoTime();
        ByteBuffer buffer = null;
        
        try {
            // ByteBuffer 풀에서 버퍼 획득
            buffer = bufferPool.acquire();
            log.debug("ByteBuffer 획득 - 용량: {}, 위치: {}", buffer.capacity(), buffer.position());
            
            // 버퍼 크기 확인
            if (messageData.length > buffer.capacity()) {
                log.error("메시지 크기가 버퍼 용량을 초과 - 메시지: {}bytes, 버퍼: {}bytes", 
                        messageData.length, buffer.capacity());
                return ProcessingResult.builder()
                        .success(false)
                        .errorMessage("메시지 크기 초과")
                        .build();
            }
            
            // 메시지 데이터를 버퍼에 복사
            buffer.put(messageData);
            buffer.flip(); // 읽기 모드로 전환
            
            // 메시지 처리 로직
            ProcessingResult result = processBuffer(buffer);
            
            // 통계 업데이트
            processedMessages.incrementAndGet();
            processedBytes.addAndGet(messageData.length);
            
            // 처리 시간 계산
            long processingTimeNanos = System.nanoTime() - startTime;
            result.setProcessingTimeNanos(processingTimeNanos);
            
            log.debug("메시지 처리 완료 - 크기: {}bytes, 시간: {}μs", 
                    messageData.length, processingTimeNanos / 1000);
            
            return result;
            
        } catch (Exception e) {
            log.error("메시지 처리 중 오류 발생", e);
            return ProcessingResult.builder()
                    .success(false)
                    .errorMessage("처리 오류: " + e.getMessage())
                    .build();
        } finally {
            // 반드시 버퍼를 풀로 반환
            if (buffer != null) {
                bufferPool.release(buffer);
                log.debug("ByteBuffer 풀로 반환");
            }
        }
    }
    
    /**
     * 문자열 메시지 처리
     * @param message 처리할 문자열 메시지
     * @return 처리 결과
     */
    public ProcessingResult processStringMessage(String message) {
        if (message == null || message.isEmpty()) {
            log.warn("빈 문자열 메시지 - 처리 건너뜀");
            return ProcessingResult.builder()
                    .success(false)
                    .errorMessage("빈 문자열 메시지")
                    .build();
        }
        
        long startTime = System.nanoTime();
        StringBuilder sb = null;
        
        try {
            // StringBuilder 풀에서 획득
            sb = stringBuilderPool.acquire();
            log.debug("StringBuilder 획득 - 용량: {}, 길이: {}", sb.capacity(), sb.length());
            
            // 문자열 처리 로직
            ProcessingResult result = processString(message, sb);
            
            // 통계 업데이트
            processedMessages.incrementAndGet();
            processedBytes.addAndGet(message.length() * 2); // UTF-16 기준
            
            // 처리 시간 계산
            long processingTimeNanos = System.nanoTime() - startTime;
            result.setProcessingTimeNanos(processingTimeNanos);
            
            log.debug("문자열 메시지 처리 완료 - 길이: {}, 시간: {}μs", 
                    message.length(), processingTimeNanos / 1000);
            
            return result;
            
        } catch (Exception e) {
            log.error("문자열 메시지 처리 중 오류 발생", e);
            return ProcessingResult.builder()
                    .success(false)
                    .errorMessage("처리 오류: " + e.getMessage())
                    .build();
        } finally {
            // 반드시 StringBuilder를 풀로 반환
            if (sb != null) {
                stringBuilderPool.release(sb);
                log.debug("StringBuilder 풀로 반환");
            }
        }
    }
    
    /**
     * ByteBuffer 처리 로직
     * @param buffer 처리할 버퍼
     * @return 처리 결과
     */
    private ProcessingResult processBuffer(ByteBuffer buffer) {
        // 실제 메시지 처리 로직 시뮬레이션
        
        // 1. 헤더 파싱
        if (buffer.remaining() < 4) {
            return ProcessingResult.builder()
                    .success(false)
                    .errorMessage("헤더 길이 부족")
                    .build();
        }
        
        int messageType = buffer.getInt();
        log.debug("메시지 타입: {}", messageType);
        
        // 2. 페이로드 처리
        byte[] payload = new byte[buffer.remaining()];
        buffer.get(payload);
        
        // 3. 체크섬 계산 (간단한 예제)
        long checksum = calculateChecksum(payload);
        
        // 4. 처리 결과 생성
        return ProcessingResult.builder()
                .success(true)
                .messageType(messageType)
                .payloadSize(payload.length)
                .checksum(checksum)
                .build();
    }
    
    /**
     * 문자열 처리 로직
     * @param message 원본 메시지
     * @param sb 작업용 StringBuilder
     * @return 처리 결과
     */
    private ProcessingResult processString(String message, StringBuilder sb) {
        // 문자열 변환 작업 시뮬레이션
        
        // 1. 대소문자 변환
        sb.append(message.toUpperCase());
        
        // 2. 특수 문자 제거
        for (int i = sb.length() - 1; i >= 0; i--) {
            char c = sb.charAt(i);
            if (!Character.isLetterOrDigit(c) && !Character.isWhitespace(c)) {
                sb.deleteCharAt(i);
            }
        }
        
        // 3. 단어 수 계산
        String processed = sb.toString();
        int wordCount = processed.split("\\s+").length;
        
        // 4. 체크섬 계산
        long checksum = calculateChecksum(processed.getBytes());
        
        return ProcessingResult.builder()
                .success(true)
                .messageType(1) // 문자열 타입
                .payloadSize(processed.length())
                .checksum(checksum)
                .processedContent(processed)
                .wordCount(wordCount)
                .build();
    }
    
    /**
     * 간단한 체크섬 계산
     * @param data 체크섬을 계산할 데이터
     * @return 체크섬 값
     */
    private long calculateChecksum(byte[] data) {
        long checksum = 0;
        for (byte b : data) {
            checksum += b & 0xFF;
        }
        return checksum;
    }
    
    /**
     * 대량 메시지 처리 테스트
     * @param messageCount 처리할 메시지 수
     * @param messageSize 메시지 크기 (바이트)
     * @return 처리 통계
     */
    public BatchProcessingStats processBatchMessages(int messageCount, int messageSize) {
        log.info("대량 메시지 처리 시작 - 개수: {}, 크기: {}bytes", messageCount, messageSize);
        
        long startTime = System.currentTimeMillis();
        int successCount = 0;
        int failureCount = 0;
        long totalBytes = 0;
        
        // 테스트 메시지 생성
        byte[] testMessage = new byte[messageSize];
        for (int i = 0; i < messageSize; i++) {
            testMessage[i] = (byte) (i % 256);
        }
        
        // 배치 처리
        for (int i = 0; i < messageCount; i++) {
            ProcessingResult result = processMessage(testMessage);
            
            if (result.isSuccess()) {
                successCount++;
                totalBytes += messageSize;
            } else {
                failureCount++;
            }
            
            // 진행률 로깅
            if (i % 10000 == 0 && i > 0) {
                log.info("처리 진행률: {}/{} ({}%)", 
                        i, messageCount, String.format("%.1f", (double) i / messageCount * 100));
            }
        }
        
        long endTime = System.currentTimeMillis();
        long totalTimeMs = endTime - startTime;
        
        BatchProcessingStats stats = BatchProcessingStats.builder()
                .totalMessages(messageCount)
                .successCount(successCount)
                .failureCount(failureCount)
                .totalBytes(totalBytes)
                .totalTimeMs(totalTimeMs)
                .messagesPerSecond(totalTimeMs > 0 ? (double) successCount * 1000 / totalTimeMs : 0)
                .bytesPerSecond(totalTimeMs > 0 ? (double) totalBytes * 1000 / totalTimeMs : 0)
                .bufferPoolStats(bufferPool.getStats())
                .stringBuilderPoolStats(stringBuilderPool.getStats())
                .build();
        
        log.info("대량 메시지 처리 완료");
        log.info("처리 통계: {}", stats);
        
        return stats;
    }
    
    /**
     * 현재 처리 통계 반환
     */
    public ProcessingStats getCurrentStats() {
        return ProcessingStats.builder()
                .totalProcessedMessages(processedMessages.get())
                .totalProcessedBytes(processedBytes.get())
                .bufferPoolStats(bufferPool.getStats())
                .stringBuilderPoolStats(stringBuilderPool.getStats())
                .build();
    }
    
    /**
     * 리소스 정리
     */
    public void shutdown() {
        log.info("MessageProcessor 종료 시작");
        
        bufferPool.clear();
        stringBuilderPool.clear();
        
        log.info("MessageProcessor 종료 완료");
        log.info("최종 처리 통계: {}", getCurrentStats());
    }
    
    // === 내부 데이터 클래스들 ===
    
    /**
     * 메시지 처리 결과
     */
    @Builder
    @Data
    public static class ProcessingResult {
        private boolean success;
        private String errorMessage;
        private int messageType;
        private int payloadSize;
        private long checksum;
        private String processedContent;
        private int wordCount;
        private long processingTimeNanos;
    }
    
    /**
     * 배치 처리 통계
     */
    @Builder
    @Data
    public static class BatchProcessingStats {
        private int totalMessages;
        private int successCount;
        private int failureCount;
        private long totalBytes;
        private long totalTimeMs;
        private double messagesPerSecond;
        private double bytesPerSecond;
        private HighPerformanceObjectPool.PoolStats bufferPoolStats;
        private HighPerformanceObjectPool.PoolStats stringBuilderPoolStats;
    }
    
    /**
     * 현재 처리 통계
     */
    @Builder
    @Data
    public static class ProcessingStats {
        private long totalProcessedMessages;
        private long totalProcessedBytes;
        private HighPerformanceObjectPool.PoolStats bufferPoolStats;
        private HighPerformanceObjectPool.PoolStats stringBuilderPoolStats;
    }
}
