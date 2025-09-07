package com.jvmgc.benchmark;

import com.jvmgc.model.Product;
import com.jvmgc.repository.MockProductRepository;
import com.jvmgc.service.MockProductService;
import lombok.extern.slf4j.Slf4j;
import org.openjdk.jmh.annotations.*;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * GC 성능 영향을 측정하기 위한 벤치마크 테스트
 * 
 * 목적: 메모리 할당 패턴이 GC에 미치는 영향 분석
 * 방법론: JMH(Java Microbenchmark Harness) 활용
 * 
 * 실행 방법:
 * 1. Gradle: ./gradlew jmh
 * 2. IDE: 이 클래스를 직접 실행
 * 3. 명령행: java -jar jmh-benchmarks.jar
 */
@BenchmarkMode(Mode.AverageTime)  // 평균 실행 시간 측정
@OutputTimeUnit(TimeUnit.MILLISECONDS)  // 밀리초 단위로 결과 출력
@State(Scope.Benchmark)  // 벤치마크 전체에서 상태 공유
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)  // 워밍업 3회
@Measurement(iterations = 5, time = 2, timeUnit = TimeUnit.SECONDS)  // 측정 5회
@Slf4j
public class GCImpactBenchmark {

    /**
     * 테스트용 상품 서비스 - 메모리 집약적 연산 담당
     */
    private MockProductService productService;
    
    /**
     * 테스트용 상품 저장소 - 최적화된 데이터 접근 담당
     */
    private MockProductRepository productRepository;
    
    /**
     * 벤치마크 실행 전 초기화
     * - 테스트 데이터 준비 (100만 개 상품)
     * - 메모리 집약적 환경 구성
     */
    @Setup(Level.Trial)  // 전체 벤치마크 시작 전 1회 실행
    public void setup() {
        log.info("=== GC Impact Benchmark 초기화 시작 ===");
        
        // 테스트 데이터 준비 (100만 개 상품)
        log.info("MockProductService 초기화 중...");
        productService = new MockProductService(1_000_000);
        
        log.info("MockProductRepository 초기화 중...");
        productRepository = new MockProductRepository();
        
        // 저장소에 일부 데이터 미리 로드 (검색 테스트용)
        log.info("저장소에 테스트 데이터 로드 중...");
        List<Product> sampleProducts = productService.getAllProducts()
                .stream()
                .limit(10_000)  // 1만 개만 저장소에 로드
                .collect(Collectors.toList());
        productRepository.saveAll(sampleProducts);
        
        log.info("=== GC Impact Benchmark 초기화 완료 ===");
        log.info("- 서비스 상품 개수: {}", productService.getProductCount());
        log.info("- 저장소 통계: {}", productRepository.getStats());
    }
    
    /**
     * 벤치마크 실행 후 정리
     * - 메모리 해제 및 리소스 정리
     */
    @TearDown(Level.Trial)  // 전체 벤치마크 종료 후 1회 실행
    public void tearDown() {
        log.info("=== GC Impact Benchmark 정리 시작 ===");
        
        if (productService != null) {
            productService.clearProducts();
        }
        
        if (productRepository != null) {
            productRepository.clear();
        }
        
        // 명시적 GC 호출 (테스트 목적)
        System.gc();
        
        log.info("=== GC Impact Benchmark 정리 완료 ===");
    }
    
    /**
     * 메모리 집약적 검색 벤치마크
     * - 모든 데이터를 메모리에 로드하여 필터링
     * - 높은 GC 압박 상황 시뮬레이션
     * 
     * 예상 결과: 높은 메모리 사용량, 빈번한 GC 발생
     */
    @Benchmark
    @Fork(value = 1, jvmArgs = {
        "-Xms4g", "-Xmx4g",           // 힙 크기 4GB 고정
        "-XX:+UseParallelGC",         // Parallel GC 사용
        "-XX:+PrintGC",               // GC 로그 출력
        "-XX:+PrintGCDetails"         // 상세 GC 정보 출력
    })
    public List<Product> testMemoryIntensiveSearch() {
        log.debug("메모리 집약적 검색 시작");
        
        // 전체 상품 데이터를 메모리에 로드 (메모리 집약적)
        List<Product> allProducts = productService.getAllProducts();
        
        // 스트림 API를 통한 필터링 (추가 메모리 할당)
        List<Product> filteredProducts = allProducts.stream()
                .filter(p -> p.getName().contains("test") || p.getName().contains("테스트"))
                .limit(20)  // 상위 20개만 반환
                .collect(Collectors.toList());
        
        log.debug("메모리 집약적 검색 완료 - 결과 개수: {}", filteredProducts.size());
        return filteredProducts;
    }
    
    /**
     * 최적화된 검색 벤치마크
     * - 데이터베이스 레벨 필터링 시뮬레이션
     * - 메모리 사용량 최적화
     * 
     * 예상 결과: 낮은 메모리 사용량, 안정적인 GC 패턴
     */
    @Benchmark
    @Fork(value = 1, jvmArgs = {
        "-Xms4g", "-Xmx4g",           // 힙 크기 4GB 고정
        "-XX:+UseParallelGC",         // Parallel GC 사용
        "-XX:+PrintGC",               // GC 로그 출력
        "-XX:+PrintGCDetails"         // 상세 GC 정보 출력
    })
    public List<Product> testOptimizedSearch() {
        log.debug("최적화된 검색 시작");
        
        // 데이터베이스 레벨 필터링 (페이징 적용)
        List<Product> optimizedResults = productRepository.findByNameContaining(
                "test", 
                0, 20  // 첫 번째 페이지, 20개 제한
        );
        
        log.debug("최적화된 검색 완료 - 결과 개수: {}", optimizedResults.size());
        return optimizedResults;
    }
    
    /**
     * G1GC를 사용한 메모리 집약적 검색 벤치마크
     * - G1GC의 낮은 지연시간 특성 테스트
     */
    @Benchmark
    @Fork(value = 1, jvmArgs = {
        "-Xms4g", "-Xmx4g",           // 힙 크기 4GB 고정
        "-XX:+UseG1GC",               // G1 GC 사용
        "-XX:MaxGCPauseMillis=100",   // 최대 GC 일시정지 시간 100ms
        "-XX:+PrintGC",               // GC 로그 출력
        "-XX:+PrintGCDetails"         // 상세 GC 정보 출력
    })
    public List<Product> testG1GCMemoryIntensiveSearch() {
        log.debug("G1GC 메모리 집약적 검색 시작");
        
        // 동일한 워크로드를 G1GC로 실행
        List<Product> allProducts = productService.getAllProducts();
        
        List<Product> filteredProducts = allProducts.stream()
                .filter(p -> p.getName().contains("test") || p.getName().contains("테스트"))
                .limit(20)
                .collect(Collectors.toList());
        
        log.debug("G1GC 메모리 집약적 검색 완료 - 결과 개수: {}", filteredProducts.size());
        return filteredProducts;
    }
    
    /**
     * 카테고리별 집계 연산 벤치마크
     * - 그룹핑 연산을 통한 메모리 사용 패턴 테스트
     */
    @Benchmark
    @Fork(value = 1, jvmArgs = {
        "-Xms4g", "-Xmx4g",
        "-XX:+UseParallelGC",
        "-XX:+PrintGC"
    })
    public java.util.Map<String, Long> testCategoryAggregation() {
        log.debug("카테고리별 집계 연산 시작");
        
        // 전체 상품을 카테고리별로 집계
        java.util.Map<String, Long> categoryStats = productService.getAllProducts()
                .stream()
                .collect(Collectors.groupingBy(
                    Product::getCategory,
                    Collectors.counting()
                ));
        
        log.debug("카테고리별 집계 연산 완료 - 카테고리 개수: {}", categoryStats.size());
        return categoryStats;
    }
    
    /**
     * 대용량 객체 생성 벤치마크
     * - Young Generation GC 압박 테스트
     */
    @Benchmark
    @Fork(value = 1, jvmArgs = {
        "-Xms4g", "-Xmx4g",
        "-XX:+UseParallelGC",
        "-XX:NewRatio=1",              // Young:Old = 1:1 비율
        "-XX:+PrintGC"
    })
    public List<Product> testMassiveObjectCreation() {
        log.debug("대용량 객체 생성 시작");
        
        // 임시 객체 대량 생성 (Young Generation 압박)
        List<Product> tempProducts = java.util.stream.IntStream.range(0, 50_000)
                .mapToObj(i -> new Product("임시상품_" + i))
                .collect(Collectors.toList());
        
        // 일부만 반환 (나머지는 GC 대상)
        List<Product> result = tempProducts.stream()
                .limit(100)
                .collect(Collectors.toList());
        
        log.debug("대용량 객체 생성 완료 - 반환 개수: {}", result.size());
        return result;
    }
    
    /**
     * 벤치마크 실행을 위한 메인 메서드
     * - IDE에서 직접 실행 가능
     */
    public static void main(String[] args) throws Exception {
        log.info("=== GC Impact Benchmark 시작 ===");
        
        // JMH 실행 옵션 설정
        org.openjdk.jmh.runner.options.Options opt = new org.openjdk.jmh.runner.options.OptionsBuilder()
                .include(GCImpactBenchmark.class.getSimpleName())
                .forks(1)                    // 프로세스 포크 1회
                .warmupIterations(2)         // 워밍업 2회
                .measurementIterations(3)    // 측정 3회
                .build();
        
        // 벤치마크 실행
        new org.openjdk.jmh.runner.Runner(opt).run();
        
        log.info("=== GC Impact Benchmark 완료 ===");
    }
}
