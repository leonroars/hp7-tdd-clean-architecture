# hp7-tdd-clean-architecture

> <big>**차례**</big>
> 1. *레포지토리 소개*
> 2. *과제 소개*
> 3. *과제 요구사항 분석*
> 4. TDD 개발
>   - 4.1 *Test Scenarios : 설계* 
>   - 4.2 *Test 설계 : Unit, Integration*
>   - 4.3 *Testable Code 설계*
>   - 4.4 *Refactor*
> 5. *동시성 제어 방식에 대한 분석 보고서*
<br>
# 1. 레포지토리 소개
항해 플러스 7기 백엔드 활동 1주차 과제 레포지토리 입니다.

<br>

# 2. 과제 소개
## 2.1 기본 과제
> - <u>포인트 충전, 사용에 대한 정책</u> 추가(잔고 부족, 최대 잔고 등) : 충전, 사용 기능을 사용자가 사용할 때 발생 가능한 다양한 상황을 고려하여 의도한 방향대로 작동하도록 정책 정하기.
> - 동시에 여러 요청이 들어오더라도 순서대로 (혹은 한번에 하나의 요청씩만) 제어될 수 있도록 리팩토링
> - 동시성 제어에 대한 ***통합 테스트*** 작성 : 복수의 스레드가 동시에 API 호출 혹은 Service 모듈을 사용하도록 시나리오 작성

## 2.2 심화 과제
> - 동시성 제어 방식에 대한 분석 및 보고서 작성
<br>

# 3. 과제 요구사항 분석
주어진 과제의 경우 하나의 도메인, 즉 포인트 관리 도메인을 표현한 것이라 할 수 있다.

프로젝트의 구조를 고려해볼 때 포인트 관리 도메인은 크게 세 가지 요소로 구성된다.
1. `User` : 사용자
> - `User` 엔티티는 `Long` 타입의 ID 를 갖도록 설계되었다.
> - 주어진 API의 path variable인 ID는 User의 ID 이다.
2. `UserPoint` : 사용자의 행위 대상
> - 사용자의 포인트를 의미하며, `Long` 타입의 허용 범위를 갖는 정수로 표현된다.
>   - 따라서 <u>특정 사용자의 누적 포인트가 `Long` 타입의 허용 범위를 넘는 경우</u> 적절한 처리가 필요하다.
> - `UserPoint`의 `id` 필드는 해당 포인트를 보유한 사용자의 `id`이다.
> - `UserPoint` 객체의 `amount` 필드는 해당 사용자의 잔여 포인트와 대응한다.
> - 수정 발생 시 `UserPoint`의 `updateMillis` 필드에 수정 시점이 기록된다.
> - `UserPoint` 객체는 \<Key : Value> = <User's ID : UserPoint> 형태로
>    HashMap 타입의 `UserPointTable` 에 저장된다.
>   - 이때, `UserPointTable`의 `throttle()` 메서드는 무작위로 생성된 millisecond 시간만큼 처리를 지연시킴으로써
>     애플리케이션의 외부에 존재하는 DB와의 I/O 시 존재하는 지연시간을 재연한다. 
>     

3. **Transaction** : 사용자의 행위
> - 포인트 변경 행위인 Transaction 은 *충전* 과 *사용* 으로 정의된다.
>   - 각각 `TransactionType`의 `CHARGE` 와 `USE` 로 표현된다.
> - Transaction 의 발생은 `PointHistory` 라는 객체 인스턴스 형태로 표현되어 `PointHistoryTable`에 기록된다.
>   - `PointHistory`는 "누가, 언제, 어떻게, 얼마나" 에 해당하는 정보를
>      각각 `userId`, `updateMillis`, `TransactionType`, `amount` 필드에 담아 트랜잭션의 세부 내용을 표현한다.
>   - `PointHistoryTable`은 `ArrayList` 자료구조로 표현된다.
>      동시성 프로그래밍을 지원할 수 없는 구현을 갖는 자료구조이므로 이에 대한 적절한 조치가 필요하다.
