package com.jvmgc.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 상품 정보를 담는 모델 클래스
 * - GC 성능 테스트에서 사용되는 기본 도메인 객체
 * - 다양한 크기의 객체 생성을 통해 메모리 할당 패턴 시뮬레이션
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Product {
    
    /**
     * 상품 고유 식별자
     */
    private Long id;
    
    /**
     * 상품명 - 문자열 객체로 인한 메모리 할당 테스트
     */
    private String name;
    
    /**
     * 상품 설명 - 큰 문자열 객체 테스트용
     */
    private String description;
    
    /**
     * 상품 가격
     */
    private Double price;
    
    /**
     * 카테고리 - 참조 관계 테스트
     */
    private String category;
    
    /**
     * 재고 수량
     */
    private Integer stock;
    
    /**
     * 상품 태그들 - 컬렉션 객체 메모리 사용량 테스트
     */
    private java.util.List<String> tags;
    
    /**
     * 상품 속성들 - Map 객체 메모리 사용량 테스트
     */
    private java.util.Map<String, Object> attributes;
    
    /**
     * 생성 시간 - 시간 객체 메모리 할당 테스트
     */
    private java.time.LocalDateTime createdAt;
    
    /**
     * 업데이트 시간
     */
    private java.time.LocalDateTime updatedAt;
    
    /**
     * 테스트용 생성자 - 기본 데이터로 상품 객체 생성
     */
    public Product(String name) {
        this.name = name;
        this.description = "테스트용 상품 설명: " + name;
        this.price = Math.random() * 1000;
        this.category = "테스트 카테고리";
        this.stock = (int) (Math.random() * 100);
        this.tags = java.util.Arrays.asList("tag1", "tag2", "tag3");
        this.attributes = new java.util.HashMap<>();
        this.attributes.put("color", "red");
        this.attributes.put("size", "medium");
        this.createdAt = java.time.LocalDateTime.now();
        this.updatedAt = java.time.LocalDateTime.now();
    }
}
