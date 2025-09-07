package com.jvmgc.service;

import com.jvmgc.model.Product;

import java.util.List;

/**
 * 상품 서비스 인터페이스
 * - GC 성능 테스트를 위한 비즈니스 로직 추상화
 */
public interface ProductService {
    
    /**
     * 모든 상품 조회 - 메모리 집약적 연산
     * @return 전체 상품 목록
     */
    List<Product> getAllProducts();
    
    /**
     * 상품명으로 검색 - 필터링 연산
     * @param keyword 검색 키워드
     * @return 검색된 상품 목록
     */
    List<Product> searchByName(String keyword);
    
    /**
     * 카테고리별 상품 조회
     * @param category 카테고리명
     * @return 해당 카테고리 상품 목록
     */
    List<Product> getProductsByCategory(String category);
}
