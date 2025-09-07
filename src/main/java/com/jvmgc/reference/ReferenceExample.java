package com.jvmgc.reference;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * GC의 참조 추적 동작 원리를 보여주는 예제 클래스
 * 
 * 학습 목표:
 * 1. Root Set에서 시작하는 참조 추적 이해
 * 2. 강한 참조(Strong Reference)와 약한 참조(Weak Reference) 차이점
 * 3. 순환 참조 상황에서의 GC 동작
 * 4. 참조 해제를 통한 메모리 관리
 * 
 * 실행 방법:
 * - 각 메서드를 개별적으로 실행하여 GC 동작 관찰
 * - GC 로그를 활성화하여 실제 GC 발생 시점 확인
 */
@Component
@Slf4j
public class ReferenceExample {
    
    private final MemoryMXBean memoryBean;
    private final List<GarbageCollectorMXBean> gcBeans;
    
    public ReferenceExample() {
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        
        log.info("ReferenceExample 초기화 완료");
    }
    
    /**
     * 기본 참조 추적 예제
     * - Root Set에서 시작하는 참조 추적 시연
     * - 참조 해제를 통한 GC 대상 객체 생성
     */
    public void demonstrateBasicReferenceTracing() {
        log.info("=== 기본 참조 추적 예제 시작 ===");
        
        // 초기 메모리 상태 확인
        logMemoryUsage("시작 전");
        
        // Root Set: main 메서드의 지역변수들
        Person person1 = new Person("김철수"); // 참조됨
        Person person2 = new Person("이영희"); // 참조됨
        
        log.info("두 Person 객체 생성 완료");
        logMemoryUsage("객체 생성 후");
        
        // 상호 참조 관계 설정
        person1.setFriend(person2); // person2는 person1을 통해서도 참조됨
        log.info("친구 관계 설정: {} -> {}", person1.getName(), person2.getName());
        
        // 참조 관계 정보 출력
        log.info("참조 관계 정보:\n{}", person1.getReferenceInfo());
        log.info("참조 관계 정보:\n{}", person2.getReferenceInfo());
        
        // 첫 번째 참조 해제
        person2 = null; // 직접 참조는 끊어졌지만 person1.friend로 여전히 참조됨
        log.info("person2 직접 참조 해제 (하지만 person1.friend로 여전히 참조됨)");
        
        // GC 실행 시도
        System.gc();
        waitForGC();
        logMemoryUsage("첫 번째 GC 후");
        
        // 두 번째 참조 해제
        person1 = null; // 이제 두 객체 모두 참조되지 않아 GC 대상이 됨
        log.info("person1 직접 참조 해제 (이제 두 객체 모두 GC 대상)");
        
        // GC 실행 시도
        System.gc();
        waitForGC();
        logMemoryUsage("두 번째 GC 후");
        
        log.info("=== 기본 참조 추적 예제 완료 ===");
    }
    
    /**
     * 순환 참조 예제
     * - 순환 참조 상황에서의 GC 동작 확인
     * - 현대 GC는 순환 참조를 올바르게 처리함을 보여줌
     */
    public void demonstrateCircularReference() {
        log.info("=== 순환 참조 예제 시작 ===");
        
        logMemoryUsage("시작 전");
        
        // 순환 참조 생성
        Person husband = new Person("남편");
        Person wife = new Person("아내");
        
        // 결혼 관계 설정 (순환 참조 생성)
        husband.marry(wife);
        log.info("결혼 관계 설정 완료 - 순환 참조 생성");
        
        // 참조 관계 확인
        log.info("남편 정보:\n{}", husband.getReferenceInfo());
        log.info("아내 정보:\n{}", wife.getReferenceInfo());
        
        logMemoryUsage("순환 참조 생성 후");
        
        // Root Set에서의 참조 해제
        husband = null;
        wife = null;
        log.info("Root Set에서 참조 해제 (순환 참조는 여전히 존재)");
        
        // GC 실행 - 현대 GC는 순환 참조를 올바르게 처리
        System.gc();
        waitForGC();
        logMemoryUsage("GC 후 (순환 참조 객체들이 정리되어야 함)");
        
        log.info("=== 순환 참조 예제 완료 ===");
        log.info("현대 GC는 순환 참조를 올바르게 감지하고 정리합니다.");
    }
    
    /**
     * 복잡한 참조 관계 예제
     * - 가족 관계를 통한 복잡한 참조 구조 생성
     * - 부분적 참조 해제와 GC 동작 관찰
     */
    public void demonstrateComplexReferenceStructure() {
        log.info("=== 복잡한 참조 관계 예제 시작 ===");
        
        logMemoryUsage("시작 전");
        
        // 가족 구조 생성
        Person grandpa = new Person("할아버지");
        Person grandma = new Person("할머니");
        Person father = new Person("아버지");
        Person mother = new Person("어머니");
        Person son = new Person("아들");
        Person daughter = new Person("딸");
        
        // 결혼 관계 설정
        grandpa.marry(grandma);
        father.marry(mother);
        
        // 부모-자식 관계 설정
        grandpa.addChild(father);
        grandma.addChild(father); // 아버지는 할아버지와 할머니 모두의 자식
        father.addChild(son);
        father.addChild(daughter);
        mother.addChild(son);
        mother.addChild(daughter);
        
        // 친구 관계 설정
        son.setFriend(daughter);
        
        log.info("복잡한 가족 구조 생성 완료");
        logMemoryUsage("가족 구조 생성 후");
        
        // 각 가족 구성원의 참조 관계 출력
        Person[] family = {grandpa, grandma, father, mother, son, daughter};
        for (Person person : family) {
            log.info("{}의 참조 관계:\n{}", person.getName(), person.getReferenceInfo());
        }
        
        // 조부모 세대 참조 해제
        grandpa = null;
        grandma = null;
        log.info("조부모 세대 Root Set 참조 해제");
        
        System.gc();
        waitForGC();
        logMemoryUsage("조부모 참조 해제 후 GC");
        
        // 부모 세대 참조 해제
        father = null;
        mother = null;
        log.info("부모 세대 Root Set 참조 해제");
        
        System.gc();
        waitForGC();
        logMemoryUsage("부모 참조 해제 후 GC");
        
        // 자식 세대 참조 해제
        son = null;
        daughter = null;
        log.info("자식 세대 Root Set 참조 해제 (모든 가족 구성원 참조 해제)");
        
        System.gc();
        waitForGC();
        logMemoryUsage("모든 참조 해제 후 GC");
        
        log.info("=== 복잡한 참조 관계 예제 완료 ===");
    }
    
    /**
     * 약한 참조(WeakReference) 예제
     * - 강한 참조와 약한 참조의 차이점 시연
     * - WeakHashMap을 통한 캐시 구현 예제
     */
    public void demonstrateWeakReference() {
        log.info("=== 약한 참조 예제 시작 ===");
        
        logMemoryUsage("시작 전");
        
        // 강한 참조 vs 약한 참조 비교
        Person strongRefPerson = new Person("강한참조");
        WeakReference<Person> weakRefPerson = new WeakReference<>(new Person("약한참조"));
        
        log.info("강한 참조와 약한 참조 객체 생성");
        log.info("약한 참조 객체 존재 여부: {}", weakRefPerson.get() != null);
        
        // 첫 번째 GC - 약한 참조 객체만 정리될 가능성
        System.gc();
        waitForGC();
        
        log.info("첫 번째 GC 후");
        log.info("강한 참조 객체: {}", strongRefPerson != null ? "존재" : "null");
        log.info("약한 참조 객체 존재 여부: {}", weakRefPerson.get() != null);
        
        // WeakHashMap을 사용한 캐시 예제
        Map<String, Person> cache = new WeakHashMap<>();
        
        // 캐시에 객체들 저장
        for (int i = 0; i < 1000; i++) {
            Person person = new Person("캐시인물_" + i);
            cache.put("key_" + i, person);
            
            // 일부 객체는 강한 참조 유지
            if (i % 100 == 0) {
                // 강한 참조를 유지하지 않음 - WeakHashMap에서 자동 정리됨
            }
        }
        
        log.info("WeakHashMap 캐시 크기 (저장 직후): {}", cache.size());
        logMemoryUsage("WeakHashMap 캐시 생성 후");
        
        // GC 실행 - WeakHashMap의 엔트리들이 자동으로 정리됨
        System.gc();
        waitForGC();
        
        log.info("WeakHashMap 캐시 크기 (GC 후): {}", cache.size());
        logMemoryUsage("WeakHashMap GC 후");
        
        // 강한 참조 해제
        strongRefPerson = null;
        
        System.gc();
        waitForGC();
        logMemoryUsage("모든 참조 해제 후 GC");
        
        log.info("=== 약한 참조 예제 완료 ===");
        log.info("WeakReference와 WeakHashMap은 메모리 압박 시 자동으로 정리됩니다.");
    }
    
    /**
     * 메모리 누수 시뮬레이션 예제
     * - 의도하지 않은 강한 참조로 인한 메모리 누수
     * - 참조 해제를 통한 메모리 누수 해결
     */
    public void demonstrateMemoryLeak() {
        log.info("=== 메모리 누수 시뮬레이션 예제 시작 ===");
        
        logMemoryUsage("시작 전");
        
        // 메모리 누수를 일으키는 컬렉션
        List<Person> leakyList = new ArrayList<>();
        
        // 대량의 객체 생성 및 저장
        for (int i = 0; i < 10000; i++) {
            Person person = new Person("누수대상_" + i);
            
            // 복잡한 참조 관계 생성
            if (i > 0) {
                person.setFriend(leakyList.get(i - 1));
            }
            
            leakyList.add(person);
            
            // 진행률 로깅
            if (i % 2000 == 0) {
                log.info("객체 생성 진행률: {}/10000", i);
                logMemoryUsage("진행 중");
            }
        }
        
        log.info("10,000개 객체 생성 완료 - 메모리 누수 상황 시뮬레이션");
        logMemoryUsage("대량 객체 생성 후");
        
        // GC 실행해도 정리되지 않음 (강한 참조 유지)
        System.gc();
        waitForGC();
        logMemoryUsage("GC 후 (여전히 참조 유지)");
        
        // 메모리 누수 해결 - 명시적 참조 해제
        log.info("메모리 누수 해결 시작 - 참조 관계 정리");
        
        // 각 객체의 참조 관계 정리
        for (Person person : leakyList) {
            person.clearAllReferences();
        }
        
        // 컬렉션 정리
        leakyList.clear();
        leakyList = null;
        
        log.info("모든 참조 관계 정리 완료");
        
        // GC 실행 - 이제 객체들이 정리됨
        System.gc();
        waitForGC();
        logMemoryUsage("참조 해제 후 GC");
        
        log.info("=== 메모리 누수 시뮬레이션 예제 완료 ===");
        log.info("명시적 참조 해제를 통해 메모리 누수를 해결했습니다.");
    }
    
    /**
     * 종합 테스트 - 모든 예제를 순차적으로 실행
     */
    public void runAllExamples() {
        log.info("=== 참조 추적 종합 테스트 시작 ===");
        
        try {
            demonstrateBasicReferenceTracing();
            Thread.sleep(2000);
            
            demonstrateCircularReference();
            Thread.sleep(2000);
            
            demonstrateComplexReferenceStructure();
            Thread.sleep(2000);
            
            demonstrateWeakReference();
            Thread.sleep(2000);
            
            demonstrateMemoryLeak();
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("테스트 중 인터럽트 발생", e);
        }
        
        log.info("=== 참조 추적 종합 테스트 완료 ===");
    }
    
    /**
     * 현재 메모리 사용량 로깅
     */
    private void logMemoryUsage(String phase) {
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        long usedMB = heapUsage.getUsed() / (1024 * 1024);
        long maxMB = heapUsage.getMax() / (1024 * 1024);
        double usagePercent = (double) heapUsage.getUsed() / heapUsage.getMax() * 100;
        
        log.info("[{}] 힙 메모리: {}MB / {}MB ({}%)", 
                phase, usedMB, maxMB, String.format("%.2f", usagePercent));
    }
    
    /**
     * GC 완료까지 대기
     */
    private void waitForGC() {
        try {
            Thread.sleep(100); // GC 완료를 위한 짧은 대기
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * 현재 GC 통계 출력
     */
    public void printGCStats() {
        log.info("=== 현재 GC 통계 ===");
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            log.info("{}: 실행 횟수 {}, 총 시간 {}ms", 
                    gcBean.getName(), 
                    gcBean.getCollectionCount(), 
                    gcBean.getCollectionTime());
        }
    }
}
