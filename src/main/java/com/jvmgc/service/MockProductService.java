package com.jvmgc.service;

import com.jvmgc.model.Product;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 테스트용 상품 서비스 구현체
 * - 대용량 데이터를 메모리에 생성하여 GC 압박 상황 시뮬레이션
 * - 메모리 집약적 연산을 통한 GC 성능 측정
 */
@Slf4j
@Service
public class MockProductService implements ProductService {
    
    /**
     * 메모리에 저장된 상품 목록
     * - 테스트 시 대용량 데이터로 인한 메모리 사용량 증가
     */
    private final List<Product> products;
    
    /**
     * 생성자 - 지정된 개수만큼 테스트 상품 데이터 생성
     * @param productCount 생성할 상품 개수
     */
    public MockProductService(int productCount) {
        log.info("MockProductService 초기화 시작 - 상품 개수: {}", productCount);
        
        // 대용량 상품 데이터 생성 (메모리 집약적 연산)
        this.products = IntStream.range(0, productCount)
                .parallel() // 병렬 처리로 성능 향상
                .mapToObj(i -> createTestProduct(i))
                .collect(Collectors.toList());
                
        log.info("MockProductService 초기화 완료 - 실제 생성된 상품 개수: {}", products.size());
    }
    
    /**
     * 기본 생성자 - 100만 개 상품 생성
     */
    public MockProductService() {
        this(1_000_000);
    }
    
    /**
     * 테스트용 상품 객체 생성
     * - 다양한 크기의 객체를 생성하여 실제 애플리케이션 패턴 모방
     * @param index 상품 인덱스
     * @return 생성된 상품 객체
     */
    private Product createTestProduct(int index) {
        Product product = new Product();
        product.setId((long) index);
        product.setName("테스트상품_" + index);
        
        // 다양한 크기의 설명 생성 (메모리 사용량 다양화)
        StringBuilder description = new StringBuilder();
        int descriptionLength = 50 + (index % 200); // 50~250자 사이
        for (int i = 0; i < descriptionLength; i++) {
            description.append("설명");
        }
        product.setDescription(description.toString());
        
        product.setPrice(Math.random() * 10000);
        product.setCategory("카테고리_" + (index % 10)); // 10개 카테고리로 분산
        product.setStock((int) (Math.random() * 1000));
        
        // 태그 리스트 생성 (컬렉션 메모리 사용량 테스트)
        List<String> tags = new ArrayList<>();
        int tagCount = 3 + (index % 5); // 3~7개 태그
        for (int i = 0; i < tagCount; i++) {
            tags.add("태그_" + (index + i));
        }
        product.setTags(tags);
        
        // 속성 맵 생성 (Map 메모리 사용량 테스트)
        java.util.Map<String, Object> attributes = new java.util.HashMap<>();
        attributes.put("color", "색상_" + (index % 5));
        attributes.put("size", "크기_" + (index % 3));
        attributes.put("weight", Math.random() * 10);
        attributes.put("material", "재질_" + (index % 4));
        product.setAttributes(attributes);
        
        product.setCreatedAt(java.time.LocalDateTime.now().minusDays(index % 365));
        product.setUpdatedAt(java.time.LocalDateTime.now().minusHours(index % 24));
        
        return product;
    }
    
    /**
     * 모든 상품 조회 - 메모리 집약적 연산
     * - 전체 상품 리스트를 반환하여 대용량 메모리 사용
     * @return 전체 상품 목록
     */
    @Override
    public List<Product> getAllProducts() {
        log.debug("getAllProducts 호출 - 반환할 상품 개수: {}", products.size());
        
        // 새로운 리스트 생성으로 추가 메모리 할당
        return new ArrayList<>(products);
    }
    
    /**
     * 상품명으로 검색 - 스트림 API를 통한 필터링
     * - 전체 데이터를 메모리에서 필터링 (비효율적 방식)
     * @param keyword 검색 키워드
     * @return 검색된 상품 목록
     */
    @Override
    public List<Product> searchByName(String keyword) {
        log.debug("searchByName 호출 - 키워드: {}", keyword);
        
        // 스트림 API를 통한 메모리 집약적 필터링
        return products.stream()
                .filter(product -> product.getName().contains(keyword))
                .collect(Collectors.toList());
    }
    
    /**
     * 카테고리별 상품 조회
     * @param category 카테고리명
     * @return 해당 카테고리 상품 목록
     */
    @Override
    public List<Product> getProductsByCategory(String category) {
        log.debug("getProductsByCategory 호출 - 카테고리: {}", category);
        
        return products.stream()
                .filter(product -> product.getCategory().equals(category))
                .collect(Collectors.toList());
    }
    
    /**
     * 현재 메모리에 로드된 상품 개수 반환
     * @return 상품 개수
     */
    public int getProductCount() {
        return products.size();
    }
    
    /**
     * 메모리 정리 - 테스트 후 메모리 해제용
     */
    public void clearProducts() {
        log.info("상품 데이터 메모리 정리 시작");
        products.clear();
        log.info("상품 데이터 메모리 정리 완료");
    }
}
