package com.jvmgc.optimization;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.awt.image.BufferedImage;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 메모리 최적화 예제 코드 모음
 * 
 * 포함된 최적화 기법:
 * 1. 컬렉션 크기 미리 지정하기
 * 2. 문자열 연결 최적화
 * 3. 메모리 누수 방지
 * 4. WeakReference 활용
 * 5. 객체 재사용 패턴
 * 6. 스트림 API 최적화
 * 7. 캐시 최적화
 * 
 * 각 예제는 "나쁜 예"와 "좋은 예"를 비교하여 
 * 최적화 효과를 명확히 보여줍니다.
 */
@Component
@Slf4j
public class MemoryOptimizationExamples {
    
    public MemoryOptimizationExamples() {
        log.info("MemoryOptimizationExamples 초기화 완료");
    }
    
    /**
     * 1. 컬렉션 크기 미리 지정하기
     * - ArrayList, HashMap 등의 초기 용량 설정
     * - 동적 확장으로 인한 메모리 복사 비용 절약
     */
    public void demonstrateCollectionSizing() {
        log.info("=== 컬렉션 크기 최적화 예제 ===");
        
        int expectedSize = 100000;
        
        // 나쁜 예: 기본 크기로 시작하여 계속 확장
        long startTime = System.nanoTime();
        List<String> badList = badCollectionSizing(expectedSize);
        long badTime = System.nanoTime() - startTime;
        
        // 좋은 예: 예상 크기로 초기화
        startTime = System.nanoTime();
        List<String> goodList = goodCollectionSizing(expectedSize);
        long goodTime = System.nanoTime() - startTime;
        
        log.info("나쁜 예 (기본 크기): {}ms, 최종 크기: {}", 
                badTime / 1_000_000, badList.size());
        log.info("좋은 예 (초기 크기 지정): {}ms, 최종 크기: {}", 
                goodTime / 1_000_000, goodList.size());
        log.info("성능 향상: {:.2f}배", (double) badTime / goodTime);
        
        // 메모리 정리
        badList.clear();
        goodList.clear();
    }
    
    /**
     * 나쁜 예: 기본 크기로 시작하여 계속 확장
     */
    private List<String> badCollectionSizing(int expectedSize) {
        List<String> list = new ArrayList<>(); // 기본 크기 10
        for (int i = 0; i < expectedSize; i++) {
            list.add("item" + i); // 크기 초과시 배열 복사 발생
        }
        return list;
    }
    
    /**
     * 좋은 예: 예상 크기로 초기화
     */
    private List<String> goodCollectionSizing(int expectedSize) {
        List<String> list = new ArrayList<>(expectedSize); // 미리 크기 지정
        for (int i = 0; i < expectedSize; i++) {
            list.add("item" + i); // 배열 복사 없음
        }
        return list;
    }
    
    /**
     * 2. 문자열 연결 최적화
     * - StringBuilder 사용으로 임시 String 객체 생성 방지
     */
    public void demonstrateStringOptimization() {
        log.info("=== 문자열 연결 최적화 예제 ===");
        
        String[] words = IntStream.range(0, 10000)
                .mapToObj(i -> "word" + i)
                .toArray(String[]::new);
        
        // 나쁜 예: String 연결로 인한 많은 임시 객체 생성
        long startTime = System.nanoTime();
        String badResult = badStringConcat(words);
        long badTime = System.nanoTime() - startTime;
        
        // 좋은 예: StringBuilder 사용
        startTime = System.nanoTime();
        String goodResult = goodStringConcat(words);
        long goodTime = System.nanoTime() - startTime;
        
        log.info("나쁜 예 (String 연결): {}ms, 길이: {}", 
                badTime / 1_000_000, badResult.length());
        log.info("좋은 예 (StringBuilder): {}ms, 길이: {}", 
                goodTime / 1_000_000, goodResult.length());
        log.info("성능 향상: {:.2f}배", (double) badTime / goodTime);
        
        // 결과 검증
        assert badResult.equals(goodResult) : "결과가 다릅니다!";
    }
    
    /**
     * 나쁜 예: String 연결로 인한 많은 임시 객체 생성
     */
    private String badStringConcat(String[] words) {
        String result = "";
        for (String word : words) {
            result += word + " "; // 매번 새로운 String 객체 생성
        }
        return result;
    }
    
    /**
     * 좋은 예: StringBuilder 사용
     */
    private String goodStringConcat(String[] words) {
        StringBuilder sb = new StringBuilder(words.length * 10); // 예상 크기
        for (String word : words) {
            sb.append(word).append(" ");
        }
        return sb.toString();
    }
    
    /**
     * 3. 메모리 누수 방지 패턴
     * - 리스너 해제, 리소스 정리 등
     */
    public void demonstrateMemoryLeakPrevention() {
        log.info("=== 메모리 누수 방지 예제 ===");
        
        // 나쁜 예: 리스너 해제하지 않음
        log.info("나쁜 예: 리스너 해제하지 않는 패턴");
        BadEventHandler badHandler = new BadEventHandler();
        for (int i = 0; i < 1000; i++) {
            badHandler.addListener(new MockEventListener("listener_" + i));
        }
        log.info("BadEventHandler 리스너 수: {}", badHandler.getListenerCount());
        
        // 좋은 예: 적절한 리소스 관리
        log.info("좋은 예: 적절한 리소스 관리 패턴");
        GoodEventHandler goodHandler = new GoodEventHandler();
        List<MockEventListener> listeners = new ArrayList<>();
        
        for (int i = 0; i < 1000; i++) {
            MockEventListener listener = new MockEventListener("listener_" + i);
            goodHandler.addListener(listener);
            listeners.add(listener);
        }
        
        log.info("GoodEventHandler 리스너 수 (추가 후): {}", goodHandler.getListenerCount());
        
        // 적절한 리스너 해제
        for (MockEventListener listener : listeners) {
            goodHandler.removeListener(listener);
        }
        goodHandler.cleanup();
        
        log.info("GoodEventHandler 리스너 수 (정리 후): {}", goodHandler.getListenerCount());
    }
    
    /**
     * 4. WeakReference 활용 캐시
     * - 메모리 압박 시 자동 해제되는 캐시 구현
     */
    public void demonstrateWeakReferenceCache() {
        log.info("=== WeakReference 캐시 최적화 예제 ===");
        
        // WeakReference를 사용한 이미지 캐시 예제
        ImageCache cache = new ImageCache();
        
        // 이미지 캐시에 데이터 저장
        for (int i = 0; i < 1000; i++) {
            String path = "image_" + i + ".jpg";
            // 실제로는 BufferedImage를 로드하지만, 여기서는 Mock 객체 사용
            MockImage image = new MockImage(path, 1024 * 1024); // 1MB 이미지
            cache.putImage(path, image);
        }
        
        log.info("캐시 크기 (저장 직후): {}", cache.getCacheSize());
        
        // 메모리 압박 상황 시뮬레이션
        log.info("메모리 압박 상황 시뮬레이션 (GC 실행)");
        System.gc();
        
        // 잠시 대기 후 캐시 크기 확인
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        log.info("캐시 크기 (GC 후): {}", cache.getCacheSize());
        
        // 일부 이미지 다시 접근 (강한 참조 생성)
        List<MockImage> strongRefs = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            String path = "image_" + i + ".jpg";
            MockImage image = cache.getImage(path);
            if (image != null) {
                strongRefs.add(image); // 강한 참조 유지
            }
        }
        
        log.info("강한 참조 유지 중인 이미지 수: {}", strongRefs.size());
        
        // 다시 GC 실행
        System.gc();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        log.info("캐시 크기 (강한 참조 유지 후 GC): {}", cache.getCacheSize());
        
        // 강한 참조 해제
        strongRefs.clear();
    }
    
    /**
     * 5. 객체 재사용 패턴
     * - 임시 객체 생성을 줄이는 패턴들
     */
    public void demonstrateObjectReuse() {
        log.info("=== 객체 재사용 패턴 예제 ===");
        
        int iterations = 100000;
        
        // 나쁜 예: 매번 새로운 객체 생성
        long startTime = System.nanoTime();
        List<ProcessingResult> badResults = badObjectCreation(iterations);
        long badTime = System.nanoTime() - startTime;
        
        // 좋은 예: 객체 재사용
        startTime = System.nanoTime();
        List<ProcessingResult> goodResults = goodObjectReuse(iterations);
        long goodTime = System.nanoTime() - startTime;
        
        log.info("나쁜 예 (매번 새 객체): {}ms, 결과 수: {}", 
                badTime / 1_000_000, badResults.size());
        log.info("좋은 예 (객체 재사용): {}ms, 결과 수: {}", 
                goodTime / 1_000_000, goodResults.size());
        log.info("성능 향상: {:.2f}배", (double) badTime / goodTime);
        
        // 메모리 정리
        badResults.clear();
        goodResults.clear();
    }
    
    /**
     * 나쁜 예: 매번 새로운 객체 생성
     */
    private List<ProcessingResult> badObjectCreation(int iterations) {
        List<ProcessingResult> results = new ArrayList<>(iterations);
        
        for (int i = 0; i < iterations; i++) {
            // 매번 새로운 StringBuilder 생성
            StringBuilder sb = new StringBuilder();
            sb.append("Processing item ").append(i);
            
            // 매번 새로운 결과 객체 생성
            ProcessingResult result = new ProcessingResult();
            result.setId(i);
            result.setMessage(sb.toString());
            result.setTimestamp(System.currentTimeMillis());
            
            results.add(result);
        }
        
        return results;
    }
    
    /**
     * 좋은 예: 객체 재사용
     */
    private List<ProcessingResult> goodObjectReuse(int iterations) {
        List<ProcessingResult> results = new ArrayList<>(iterations);
        
        // StringBuilder 재사용
        StringBuilder sb = new StringBuilder(50);
        ProcessingResult reusableResult = new ProcessingResult();
        
        for (int i = 0; i < iterations; i++) {
            // StringBuilder 재사용
            sb.setLength(0); // 내용 초기화
            sb.append("Processing item ").append(i);
            
            // 결과 객체 재사용 (복사본 생성)
            reusableResult.setId(i);
            reusableResult.setMessage(sb.toString());
            reusableResult.setTimestamp(System.currentTimeMillis());
            
            // 복사본 생성하여 저장
            ProcessingResult copy = new ProcessingResult();
            copy.setId(reusableResult.getId());
            copy.setMessage(reusableResult.getMessage());
            copy.setTimestamp(reusableResult.getTimestamp());
            
            results.add(copy);
        }
        
        return results;
    }
    
    /**
     * 6. 스트림 API 최적화
     * - 중간 연산 최소화, 병렬 처리 활용
     */
    public void demonstrateStreamOptimization() {
        log.info("=== 스트림 API 최적화 예제 ===");
        
        List<Integer> numbers = IntStream.range(0, 1_000_000)
                .boxed()
                .collect(Collectors.toList());
        
        // 나쁜 예: 비효율적인 스트림 사용
        long startTime = System.nanoTime();
        List<String> badResult = badStreamUsage(numbers);
        long badTime = System.nanoTime() - startTime;
        
        // 좋은 예: 최적화된 스트림 사용
        startTime = System.nanoTime();
        List<String> goodResult = goodStreamUsage(numbers);
        long goodTime = System.nanoTime() - startTime;
        
        log.info("나쁜 예 (비효율적 스트림): {}ms, 결과 수: {}", 
                badTime / 1_000_000, badResult.size());
        log.info("좋은 예 (최적화된 스트림): {}ms, 결과 수: {}", 
                goodTime / 1_000_000, goodResult.size());
        log.info("성능 향상: {:.2f}배", (double) badTime / goodTime);
        
        // 결과 검증
        assert badResult.size() == goodResult.size() : "결과 크기가 다릅니다!";
    }
    
    /**
     * 나쁜 예: 비효율적인 스트림 사용
     */
    private List<String> badStreamUsage(List<Integer> numbers) {
        return numbers.stream()
                .filter(n -> n % 2 == 0)          // 짝수 필터
                .map(n -> n * 2)                  // 2배
                .filter(n -> n > 1000)            // 1000 초과
                .map(n -> "Number: " + n)         // 문자열 변환
                .filter(s -> s.length() > 10)    // 길이 필터
                .collect(Collectors.toList());
    }
    
    /**
     * 좋은 예: 최적화된 스트림 사용
     */
    private List<String> goodStreamUsage(List<Integer> numbers) {
        return numbers.parallelStream()           // 병렬 처리
                .filter(n -> n % 2 == 0 && n > 500) // 조건 결합
                .mapToInt(n -> n * 2)             // 박싱 방지
                .filter(n -> n > 1000)
                .mapToObj(n -> "Number: " + n)    // 최종 변환
                .collect(Collectors.toList());
    }
    
    /**
     * 모든 최적화 예제 실행
     */
    public void runAllOptimizationExamples() {
        log.info("=== 메모리 최적화 예제 전체 실행 ===");
        
        try {
            demonstrateCollectionSizing();
            Thread.sleep(1000);
            
            demonstrateStringOptimization();
            Thread.sleep(1000);
            
            demonstrateMemoryLeakPrevention();
            Thread.sleep(1000);
            
            demonstrateWeakReferenceCache();
            Thread.sleep(1000);
            
            demonstrateObjectReuse();
            Thread.sleep(1000);
            
            demonstrateStreamOptimization();
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("예제 실행 중 인터럽트 발생", e);
        }
        
        log.info("=== 메모리 최적화 예제 전체 실행 완료 ===");
    }
    
    // === 내부 클래스들 ===
    
    /**
     * 나쁜 이벤트 핸들러 - 리스너 해제하지 않음
     */
    static class BadEventHandler {
        private final List<MockEventListener> listeners = new ArrayList<>();
        
        public void addListener(MockEventListener listener) {
            listeners.add(listener);
        }
        
        public int getListenerCount() {
            return listeners.size();
        }
    }
    
    /**
     * 좋은 이벤트 핸들러 - 적절한 리소스 관리
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
        
        public int getListenerCount() {
            return listeners.size();
        }
    }
    
    /**
     * Mock 이벤트 리스너
     */
    static class MockEventListener {
        private final String name;
        
        public MockEventListener(String name) {
            this.name = name;
        }
        
        public String getName() {
            return name;
        }
    }
    
    /**
     * WeakReference를 사용한 이미지 캐시
     */
    static class ImageCache {
        private final Map<String, WeakReference<MockImage>> cache = new ConcurrentHashMap<>();
        
        public void putImage(String path, MockImage image) {
            cache.put(path, new WeakReference<>(image));
        }
        
        public MockImage getImage(String path) {
            WeakReference<MockImage> ref = cache.get(path);
            if (ref != null) {
                MockImage image = ref.get();
                if (image != null) {
                    return image;
                } else {
                    // WeakReference가 정리됨
                    cache.remove(path);
                }
            }
            
            // 캐시 미스 - 실제로는 디스크에서 로드
            return loadImageFromDisk(path);
        }
        
        private MockImage loadImageFromDisk(String path) {
            // 실제 이미지 로딩 로직 시뮬레이션
            MockImage image = new MockImage(path, 1024 * 1024);
            putImage(path, image);
            return image;
        }
        
        public int getCacheSize() {
            // 유효한 참조만 카운트
            return (int) cache.values().stream()
                    .mapToLong(ref -> ref.get() != null ? 1 : 0)
                    .sum();
        }
    }
    
    /**
     * Mock 이미지 클래스
     */
    static class MockImage {
        private final String path;
        private final byte[] data;
        
        public MockImage(String path, int size) {
            this.path = path;
            this.data = new byte[size];
        }
        
        public String getPath() {
            return path;
        }
        
        public int getSize() {
            return data.length;
        }
    }
    
    /**
     * 처리 결과 클래스
     */
    @lombok.Data
    static class ProcessingResult {
        private int id;
        private String message;
        private long timestamp;
    }
}
