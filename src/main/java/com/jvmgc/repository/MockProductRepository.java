package com.jvmgc.repository;

import com.jvmgc.model.Product;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 테스트용 상품 저장소 구현체
 * - 데이터베이스 레벨 최적화를 시뮬레이션
 * - 메모리 효율적인 데이터 접근 패턴 구현
 */
@Slf4j
@Repository
public class MockProductRepository implements ProductRepository {
    
    /**
     * 메모리 기반 상품 저장소
     * - ConcurrentHashMap을 사용하여 동시성 지원
     */
    private final ConcurrentHashMap<Long, Product> productStore;
    
    /**
     * 카테고리별 인덱스 - 빠른 검색을 위한 보조 인덱스
     */
    private final ConcurrentHashMap<String, List<Long>> categoryIndex;
    
    /**
     * 상품명 인덱스 - 부분 문자열 검색을 위한 보조 인덱스
     */
    private final ConcurrentHashMap<String, List<Long>> nameIndex;
    
    public MockProductRepository() {
        this.productStore = new ConcurrentHashMap<>();
        this.categoryIndex = new ConcurrentHashMap<>();
        this.nameIndex = new ConcurrentHashMap<>();
        
        log.info("MockProductRepository 초기화 완료");
    }
    
    /**
     * 상품명으로 검색 (페이징 지원)
     * - 데이터베이스 레벨 필터링 시뮬레이션
     * - 메모리 사용량 최적화를 위한 페이징 처리
     * 
     * @param keyword 검색 키워드
     * @param pageRequest 페이징 정보
     * @return 검색된 상품 목록 (페이징 적용)
     */
    @Override
    public List<Product> findByNameContaining(String keyword, PageRequest pageRequest) {
        log.debug("findByNameContaining 호출 - 키워드: {}, 페이지: {}, 크기: {}", 
                keyword, pageRequest.getPageNumber(), pageRequest.getPageSize());
        
        // 효율적인 검색을 위한 인덱스 활용
        List<Product> matchedProducts = productStore.values().stream()
                .filter(product -> product.getName().contains(keyword))
                .skip((long) pageRequest.getPageNumber() * pageRequest.getPageSize())
                .limit(pageRequest.getPageSize())
                .collect(Collectors.toList());
                
        log.debug("검색 결과 개수: {}", matchedProducts.size());
        return matchedProducts;
    }
    
    /**
     * 모든 상품 조회
     * - 실제 환경에서는 페이징 처리가 필요하지만 테스트용으로 전체 반환
     * @return 전체 상품 목록
     */
    @Override
    public List<Product> findAll() {
        log.debug("findAll 호출 - 전체 상품 개수: {}", productStore.size());
        return new ArrayList<>(productStore.values());
    }
    
    /**
     * 카테고리별 상품 조회
     * - 카테고리 인덱스를 활용한 효율적 검색
     * @param category 카테고리명
     * @return 해당 카테고리 상품 목록
     */
    @Override
    public List<Product> findByCategory(String category) {
        log.debug("findByCategory 호출 - 카테고리: {}", category);
        
        List<Long> productIds = categoryIndex.get(category);
        if (productIds == null) {
            return new ArrayList<>();
        }
        
        return productIds.stream()
                .map(productStore::get)
                .filter(product -> product != null)
                .collect(Collectors.toList());
    }
    
    /**
     * 상품 저장
     * - 메인 저장소와 보조 인덱스 동시 업데이트
     * @param product 저장할 상품
     * @return 저장된 상품
     */
    @Override
    public Product save(Product product) {
        log.debug("save 호출 - 상품 ID: {}, 이름: {}", product.getId(), product.getName());
        
        // 메인 저장소에 저장
        productStore.put(product.getId(), product);
        
        // 카테고리 인덱스 업데이트
        categoryIndex.computeIfAbsent(product.getCategory(), k -> new ArrayList<>())
                .add(product.getId());
        
        // 상품명 인덱스 업데이트 (간단한 토큰 기반)
        String[] nameTokens = product.getName().split("_");
        for (String token : nameTokens) {
            nameIndex.computeIfAbsent(token, k -> new ArrayList<>())
                    .add(product.getId());
        }
        
        return product;
    }
    
    /**
     * 대량 상품 저장 - 배치 처리 최적화
     * @param products 저장할 상품 목록
     * @return 저장된 상품 개수
     */
    public int saveAll(List<Product> products) {
        log.info("saveAll 호출 - 저장할 상품 개수: {}", products.size());
        
        int savedCount = 0;
        for (Product product : products) {
            save(product);
            savedCount++;
            
            // 진행률 로깅 (10만 개마다)
            if (savedCount % 100_000 == 0) {
                log.info("저장 진행률: {}/{}", savedCount, products.size());
            }
        }
        
        log.info("saveAll 완료 - 총 저장된 상품 개수: {}", savedCount);
        return savedCount;
    }
    
    /**
     * 저장소 통계 정보 반환
     * @return 저장소 통계
     */
    public RepositoryStats getStats() {
        return RepositoryStats.builder()
                .totalProducts(productStore.size())
                .totalCategories(categoryIndex.size())
                .totalNameTokens(nameIndex.size())
                .memoryUsageEstimate(estimateMemoryUsage())
                .build();
    }
    
    /**
     * 메모리 사용량 추정
     * @return 추정 메모리 사용량 (바이트)
     */
    private long estimateMemoryUsage() {
        // 간단한 메모리 사용량 추정 로직
        long productMemory = productStore.size() * 1024L; // 상품당 약 1KB 추정
        long indexMemory = (categoryIndex.size() + nameIndex.size()) * 64L; // 인덱스 오버헤드
        return productMemory + indexMemory;
    }
    
    /**
     * 저장소 정리 - 테스트 후 메모리 해제
     */
    public void clear() {
        log.info("저장소 정리 시작");
        productStore.clear();
        categoryIndex.clear();
        nameIndex.clear();
        log.info("저장소 정리 완료");
    }
    
    /**
     * 저장소 통계 정보 클래스
     */
    @lombok.Builder
    @lombok.Data
    public static class RepositoryStats {
        private int totalProducts;
        private int totalCategories;
        private int totalNameTokens;
        private long memoryUsageEstimate;
    }
}
