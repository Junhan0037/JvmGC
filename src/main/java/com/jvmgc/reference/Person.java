package com.jvmgc.reference;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * 참조 추적 예제를 위한 Person 클래스
 * - GC의 참조 추적 동작 원리를 이해하기 위한 도메인 객체
 * - 순환 참조, 약한 참조 등 다양한 참조 패턴 시연
 */
@Data
@Slf4j
public class Person {
    
    /**
     * 사람의 이름
     */
    private String name;
    
    /**
     * 친구 참조 - 강한 참조 (Strong Reference)
     * - 이 참조가 존재하는 한 친구 객체는 GC되지 않음
     */
    private Person friend;
    
    /**
     * 배우자 참조 - 강한 참조
     * - 상호 참조 관계를 만들 수 있음 (순환 참조)
     */
    private Person spouse;
    
    /**
     * 부모 참조 - 강한 참조
     */
    private Person parent;
    
    /**
     * 자식들 참조 - 컬렉션을 통한 다중 참조
     */
    private java.util.List<Person> children;
    
    /**
     * 추가 데이터 - 메모리 사용량 증가를 위한 필드
     */
    private String description;
    private java.time.LocalDateTime createdAt;
    private java.util.Map<String, Object> attributes;
    
    /**
     * 생성자
     * @param name 사람 이름
     */
    public Person(String name) {
        this.name = name;
        this.children = new java.util.ArrayList<>();
        this.attributes = new java.util.HashMap<>();
        this.createdAt = java.time.LocalDateTime.now();
        this.description = "Person: " + name + " created at " + createdAt;
        
        log.debug("Person 객체 생성: {}", name);
    }
    
    /**
     * 친구 관계 설정 - 단방향 참조
     * @param friend 친구로 설정할 Person 객체
     */
    public void setFriend(Person friend) {
        this.friend = friend;
        log.debug("{} -> {} 친구 관계 설정", this.name, friend != null ? friend.name : "null");
    }
    
    /**
     * 상호 친구 관계 설정 - 양방향 참조
     * @param friend 상호 친구로 설정할 Person 객체
     */
    public void setMutualFriend(Person friend) {
        this.friend = friend;
        if (friend != null) {
            friend.friend = this;
        }
        log.debug("{} <-> {} 상호 친구 관계 설정", 
                this.name, friend != null ? friend.name : "null");
    }
    
    /**
     * 결혼 관계 설정 - 순환 참조 생성
     * @param spouse 배우자로 설정할 Person 객체
     */
    public void marry(Person spouse) {
        this.spouse = spouse;
        if (spouse != null) {
            spouse.spouse = this;
        }
        log.debug("{} <-> {} 결혼 관계 설정 (순환 참조 생성)", 
                this.name, spouse != null ? spouse.name : "null");
    }
    
    /**
     * 이혼 - 순환 참조 해제
     */
    public void divorce() {
        if (this.spouse != null) {
            Person exSpouse = this.spouse;
            this.spouse.spouse = null;
            this.spouse = null;
            log.debug("{} <-> {} 이혼 (순환 참조 해제)", this.name, exSpouse.name);
        }
    }
    
    /**
     * 자식 추가
     * @param child 자식으로 추가할 Person 객체
     */
    public void addChild(Person child) {
        if (child != null) {
            this.children.add(child);
            child.parent = this;
            log.debug("{} -> {} 부모-자식 관계 설정", this.name, child.name);
        }
    }
    
    /**
     * 자식 제거
     * @param child 제거할 자식 Person 객체
     */
    public void removeChild(Person child) {
        if (child != null && this.children.remove(child)) {
            child.parent = null;
            log.debug("{} -> {} 부모-자식 관계 해제", this.name, child.name);
        }
    }
    
    /**
     * 모든 관계 해제 - 참조 정리
     * GC 테스트 시 명시적으로 참조를 끊기 위해 사용
     */
    public void clearAllReferences() {
        log.debug("{} 모든 참조 관계 해제 시작", this.name);
        
        // 친구 관계 해제
        if (this.friend != null) {
            log.debug("친구 관계 해제: {} -> {}", this.name, this.friend.name);
            this.friend = null;
        }
        
        // 배우자 관계 해제
        if (this.spouse != null) {
            Person exSpouse = this.spouse;
            this.spouse.spouse = null;
            this.spouse = null;
            log.debug("배우자 관계 해제: {} <-> {}", this.name, exSpouse.name);
        }
        
        // 부모 관계 해제
        if (this.parent != null) {
            this.parent.children.remove(this);
            log.debug("부모 관계 해제: {} -> {}", this.parent.name, this.name);
            this.parent = null;
        }
        
        // 자식 관계 해제
        for (Person child : new java.util.ArrayList<>(this.children)) {
            child.parent = null;
            log.debug("자식 관계 해제: {} -> {}", this.name, child.name);
        }
        this.children.clear();
        
        // 기타 데이터 정리
        this.attributes.clear();
        
        log.debug("{} 모든 참조 관계 해제 완료", this.name);
    }
    
    /**
     * 참조 관계 정보를 문자열로 반환
     */
    public String getReferenceInfo() {
        StringBuilder info = new StringBuilder();
        info.append("Person: ").append(name).append("\n");
        info.append("  친구: ").append(friend != null ? friend.name : "없음").append("\n");
        info.append("  배우자: ").append(spouse != null ? spouse.name : "없음").append("\n");
        info.append("  부모: ").append(parent != null ? parent.name : "없음").append("\n");
        info.append("  자식 수: ").append(children.size()).append("\n");
        
        if (!children.isEmpty()) {
            info.append("  자식들: ");
            for (Person child : children) {
                info.append(child.name).append(" ");
            }
            info.append("\n");
        }
        
        return info.toString();
    }
    
    /**
     * 객체의 대략적인 메모리 사용량 추정
     * @return 추정 메모리 사용량 (바이트)
     */
    public long estimateMemoryUsage() {
        long size = 0;
        
        // 기본 객체 오버헤드
        size += 16; // 객체 헤더
        
        // 문자열 필드들
        size += name != null ? name.length() * 2 + 40 : 0;
        size += description != null ? description.length() * 2 + 40 : 0;
        
        // 참조 필드들 (각 8바이트)
        size += 8 * 4; // friend, spouse, parent, createdAt
        
        // 컬렉션 오버헤드
        size += 40; // ArrayList 오버헤드
        size += children.size() * 8; // 자식 참조들
        
        size += 64; // HashMap 오버헤드
        size += attributes.size() * 32; // Map 엔트리들
        
        return size;
    }
    
    /**
     * finalize 메서드 - GC 시점 확인용 (Java 9+에서는 deprecated)
     * 실제 운영 코드에서는 사용하지 않는 것을 권장
     */
    @Override
    protected void finalize() throws Throwable {
        log.debug("Person 객체 GC됨: {}", name);
        super.finalize();
    }
    
    /**
     * toString 메서드 오버라이드
     */
    @Override
    public String toString() {
        return String.format("Person{name='%s', hasSpouse=%s, childrenCount=%d}", 
                name, 
                spouse != null, 
                children.size());
    }
    
    /**
     * equals와 hashCode - 이름 기반 동등성 비교
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Person person = (Person) obj;
        return java.util.Objects.equals(name, person.name);
    }
    
    @Override
    public int hashCode() {
        return java.util.Objects.hash(name);
    }
}
