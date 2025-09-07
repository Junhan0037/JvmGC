package com.jvmgc.repository;

import com.jvmgc.model.Product;
import org.springframework.data.domain.PageRequest;
import java.util.List;

/**
 * 상품 저장소 인터페이스
 * - 데이터베이스 레벨 최적화를 시뮬레이션하는 인터페이스
 */
public interface ProductRepository {
    
    /**
     * 상품명으로 검색 (페이징 지원)
     * - 데이터베이스 레벨에서 필터링하여 메모리 사용량 최적화
     * @param keyword 검색 키워드
     * @param pageRequest 페이징 정보
     * @return 검색된 상품 목록 (페이징 적용)
     */
    List<Product> findByNameContaining(String keyword, PageRequest pageRequest);
    
    /**
     * 모든 상품 조회
     * @return 전체 상품 목록
     */
    List<Product> findAll();
    
    /**
     * 카테고리별 상품 조회
     * @param category 카테고리명
     * @return 해당 카테고리 상품 목록
     */
    List<Product> findByCategory(String category);
    
    /**
     * 상품 저장
     * @param product 저장할 상품
     * @return 저장된 상품
     */
    Product save(Product product);
}
