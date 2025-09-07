## 🎉 JVM 가비지 컬렉션 학습 프로젝트

블로그 포스팅의 내용을 바탕으로 실제 동작하는 종합적인 JVM GC 학습 프로젝트를 구현했습니다.

### 📋 구현된 주요 기능들

#### ✅ 완료된 작업들:

1. **🔧 프로젝트 설정**
    - `build.gradle`에 필요한 모든 dependencies 추가 (JMH, Micrometer, Chronicle Map 등)
    - JMH 벤치마크 실행을 위한 gradle 태스크 설정

2. **📊 GC 성능 벤치마크 시스템**
    - `GCImpactBenchmark`: JMH 기반 성능 측정
    - 메모리 집약적 vs 최적화된 검색 비교
    - 다양한 GC 알고리즘별 성능 테스트

3. **🔍 GC 분석 도구**
    - `GCAnalyzer`: 실시간 GC 메트릭 수집 및 분석
    - 메모리 사용 패턴 분석
    - 성능 평가 및 권장사항 제시

4. **💾 엔터프라이즈급 캐시 시스템**
    - `EnterpriseCache`: L1(On-heap) + L2(Off-heap) 2단계 캐시
    - WeakReference 기반 메모리 압박 대응
    - Chronicle Map을 활용한 대용량 Off-heap 저장

5. **🔗 참조 추적 예제**
    - `ReferenceExample`: GC의 참조 추적 동작 원리 시연
    - 기본 참조, 순환 참조, 복잡한 참조 구조 테스트
    - WeakReference 활용 패턴

6. **⚡ 고성능 객체 풀링**
    - `HighPerformanceObjectPool`: 스레드 안전한 객체 재사용
    - `MessageProcessor`: ByteBuffer 풀링 실제 사용 예제
    - Micrometer 기반 메트릭 수집

7. **📈 실시간 GC 모니터링**
    - `GCMonitor`: 24/7 운영 환경 모니터링 도구
    - 이상 상황 감지 및 알림
    - 상세한 성능 분석 리포트

8. **🔍 메모리 누수 감지 테스트**
    - `MemoryLeakDetectionTest`: 일반적인 누수 패턴 분석
    - 세션 관리, 캐시, 리스너, ThreadLocal 누수 시나리오
    - 누수 있는 구현 vs 최적화된 구현 비교

9. **🚀 메모리 최적화 예제**
    - `MemoryOptimizationExamples`: 실무 최적화 기법 모음
    - 컬렉션 크기 지정, 문자열 최적화, 객체 재사용 등
    - 나쁜 예 vs 좋은 예 비교 분석

10. **🎯 통합 애플리케이션**
    - `JvmGcApplication`: 모든 기능을 통합한 메인 애플리케이션
    - CommandLineRunner로 자동 실행 시나리오
    - 단계별 학습 프로그램

### 🚀 실행 방법

#### 1. 기본 실행
```bash
cd /Users/junhankim/Desktop/Project/JvmGC
./gradlew bootRun
```

#### 2. 특정 GC로 실행
```bash
# G1GC로 실행 (권장)
./gradlew bootRun -Dspring-boot.run.jvmArguments="-XX:+UseG1GC -XX:MaxGCPauseMillis=100 -Xms4g -Xmx4g"

# Parallel GC로 실행
./gradlew bootRun -Dspring-boot.run.jvmArguments="-XX:+UseParallelGC -Xms4g -Xmx4g"
```

#### 3. 테스트 실행
```bash
# 전체 테스트
./gradlew test

# JMH 벤치마크
./gradlew jmh
```

### 📚 학습 효과

이 프로젝트를 통해 다음을 학습할 수 있습니다:

1. **이론과 실습의 결합**: 블로그의 이론적 내용을 실제 코드로 구현
2. **정량적 성능 측정**: JMH를 통한 과학적 벤치마크
3. **실무 적용 패턴**: 엔터프라이즈 환경에서 사용하는 실제 기법들
4. **문제 해결 능력**: 메모리 누수 감지 및 해결 방법
5. **모니터링 역량**: 운영 환경에서의 GC 성능 추적