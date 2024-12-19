package io.hhplus.tdd;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.PointHistory;
import io.hhplus.tdd.point.PointService;
import io.hhplus.tdd.point.TransactionType;
import io.hhplus.tdd.point.UserPoint;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;


/**
 * <b>{@link PointService} 단위 테스트 </b>
 * <br></br>
 * 해당 단위 테스트는 <u>동시성 문제가 발생하지 않는 시나리오</u>에서의 비즈니스 로직에 대한 단위테스트 입니다.
 * <br></br>
 * 동시성 문제 발생 시나리오는 통합 테스트에서 시행 됩니다.
 * <br></br>
 * <br></br>
 * <b>포인트 정책</b>
 * <br></br>
 * - 최대 포인트 잔액 : 1,000,000 점
 * <br></br>
 * - 최소 포인트 잔액 : 0 점
 * <br></br>
 * - 최소 포인트 충전 금액 : 0점
 *
 */
public class PointServiceUnitTest {

    @InjectMocks
    PointService pointService;

    @Mock
    PointHistoryTable pointHistoryTable;

    @Mock
    UserPointTable userPointTable;

    private static final long USER_ID = 1L;

    /**
     * 현 테스트 클래스에서 @Mock, @InjeckMocks 로 Annotated 된 필드를 매 테스트 수행 이전 새로 생성하여 주입한다.
     * <br></br>
     * 이는 각 테스트들이 다른 테스트 수행 및 결과로부터 독립적으로 수행될 수 있도록 한다.
     */
    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }


    /**
     * <b>1. 포인트 충전 기능</b>
     */
    @Nested
    class PointChargeTests {

        /* 실패 : 충전 금액과 잔액 합이 최대 충전 금액을 초과할 경우, 충전 실패 후 LimitedExceededException 이 발생한다. */
        @Test
        void shouldThrowException_WhenPointExceedsLimit_AfterCharge(){
            // given : 아이디가 1L인 사용자가 존재하고, 해당 사용자의 보유 포인트는 900_000 점이다.
            UserPoint userPoint = new UserPoint(USER_ID, 900_000, System.currentTimeMillis());
            long chargeAmount = 100_001;

            // Mock 객체 행동 정의
            Mockito.when(userPointTable.selectById(USER_ID))
                    .thenReturn(userPoint);

            // when : 100,001 점의 충전 요청이 발생한다.
            // then : IllegalArgumentException 발생 시 테스트는 성공한다.
            Assertions.assertThatThrownBy(() -> {
                pointService.charge(USER_ID, chargeAmount);
            })
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("허용된 포인트 한도를 초과합니다.");
        }

        /* 실패 : 충전하고자 하는 금액이 0원 미만일 경우 실패한다. */
        @Test
        void shouldThrowException_IfChargeAmountBelowZero(){
            // given : 아이디가 1L인 사용자가 존재한다. 해당 사용자의 보유 포인트는 현재 10이다.
            UserPoint userPoint = new UserPoint(USER_ID, 10, System.currentTimeMillis());
            long chargeAmount = -1L;

            Mockito.when(userPointTable.selectById(USER_ID))
                    .thenReturn(userPoint);

            // when : -1L 점의 충전 요청이 발생한다.
            // then : IllegalArgumentException 발생 시 테스트는 성공한다.
            Assertions.assertThatThrownBy(() -> {
                pointService.charge(USER_ID, chargeAmount);
            })
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("0보다 작은 금액의 포인트 충전은 불가합니다.");
        }

         /*
          * 성공 : 충전 금액과 잔액 합이 최대 충전 금액 이하일 경우, 예외 발생 없이 충전이 수행된다.
          * - 충전 이후 잔액은 (충전 전 잔액 + 충전 금액)과 같을 경우 성공한다.
          */
        @Test
        void shouldIncreaseUserPoint_WhenPointIsCharged_AndIsBelowLimit(){
            // given : 아이디가 1L인 사용자가 존재하고, 해당 사용자의 보유 포인트는 900,000 점이다.
            UserPoint userPoint = new UserPoint(USER_ID, 900_000L, System.currentTimeMillis());
            long chargeAmount = 100_000L;
            long expectedNewBalance = 1_000_000L;
            UserPoint updatedUserPoint = new UserPoint(USER_ID, expectedNewBalance, System.currentTimeMillis());

            Mockito.when(userPointTable.selectById(USER_ID))
                    .thenReturn(userPoint);
            Mockito.when(userPointTable.insertOrUpdate(USER_ID, expectedNewBalance))
                    .thenReturn(updatedUserPoint);

            // when : 100,000 점의 충전 요청이 발생한다.
            UserPoint result = pointService.charge(USER_ID, chargeAmount);

            // then : 예외 발생 없이 성공하는지, 충전 후 잔액이 1,000,000점이 맞는지 검증한다. 문제 없을 경우 성공.
            assertEquals(expectedNewBalance, result.point());

            // + Verify : Service 로직 내에서 의존 중인 다른 객체와 호출 메서드에 대한 호출이 정상적으로 이루어지는 지 검증
            Mockito.verify(userPointTable).selectById(USER_ID);
            Mockito.verify(userPointTable).insertOrUpdate(USER_ID, expectedNewBalance);
        }
    }


    /**
     * <b>2. 포인트 사용 기능</b>
     */
    @Nested
    class PointUsageTests {
        /* 실패 : 현재 잔액보다 큰 금액 사용 시도할 경우, IllegalArgumentException이 발생하며 실패합니다. */
        @Test
        void shouldThrowException_WhenUsageExceedsCurrentBalance(){
            // given : 아이디가 1L인 사용자가 존재하고, 해당 사용자의 포인트 잔액은 1 점이다.
            UserPoint userPoint = new UserPoint(USER_ID, 1L, System.currentTimeMillis());
            long useAmount = 2L;

            Mockito.when(userPointTable.selectById(USER_ID))
                    .thenReturn(userPoint);

            // when : 2점의 포인트 사용 요청이 발생한다.
            // then : IllegalArgumentException 이 발생하면 테스트는 성공이다.
            Assertions.assertThatThrownBy(() -> {
                pointService.use(USER_ID, useAmount);
            })
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("잔액 이상의 금액은 사용이 불가합니다.");
        }

        @Test
        /* 실패 : 사용 요청 포인트 금액이 0보다 작은 경우, IllegalArgumentException이 발생하며 실패합니다. */
        void shouldThrowException_WhenUsageBelowZero(){

            // given : 아이디가 1L인 사용자가 존재하고, 해당 사용자의 포인트 잔액은 1 점이다.
            UserPoint userPoint = new UserPoint(USER_ID, 1L, System.currentTimeMillis());
            long useAmount = -1L;

            Mockito.when(userPointTable.selectById(USER_ID))
                    .thenReturn(userPoint);

            // when : -1점의 포인트 사용 요청이 발생한다.
            // then : IllegalArgumentException 이 발생하면 테스트는 성공이다.
            Assertions.assertThatThrownBy(() -> {
                        pointService.use(USER_ID, useAmount);
                    })
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("0 미만 금액의 사용은 불가합니다.");

        }

        /* 성공 : 유효한 포인트 사용(0 이상 최대 포인트 한도 미만의 사용 금액 + 잔액 충분)의 경우 정상적으로 처리됩니다.
         * - 포인트 사용 후 잔액은 기존의 잔액에서 사용 금액만큼 차감된 금액이어야 합니다.
         */
        @Test
        void shouldDecreasePointBalance_WhenUsageIsNotNegative_AndIsBelowLimit(){
            // given : 아이디가 1L인 사용자가 존재하고, 해당 사용자의 포인트 잔액은 10 점이다. 사용 시도 금액은 5점이다.
            UserPoint userPoint = new UserPoint(USER_ID, 10L, System.currentTimeMillis());
            long useAmount = 5L;
            long expectedNewBalance = 5L;
            UserPoint updatedUserPoint = new UserPoint(USER_ID, expectedNewBalance, System.currentTimeMillis());

            Mockito.when(userPointTable.selectById(USER_ID))
                    .thenReturn(userPoint);
            Mockito.when(userPointTable.insertOrUpdate(USER_ID, expectedNewBalance))
                    .thenReturn(updatedUserPoint);

            // when : 5점의 포인트 사용 요청이 발생한다.
            UserPoint result = pointService.use(USER_ID, useAmount);

            // then : 예외 발생 없이 성공하는지, 충전 후 잔액이 5점이 맞는지 검증한다. 문제 없을 경우 성공.
            assertEquals(expectedNewBalance, result.point());

            // + verify : 주입된 UserPointTable 클래스의 메서드 호출이 정상적으로 이루어졌는지 검증한다.
            Mockito.verify(userPointTable).selectById(USER_ID);
            Mockito.verify(userPointTable).insertOrUpdate(USER_ID, expectedNewBalance);
        }

    }

    /**
     * <b>3. 포인트 잔액 조회 기능</b>
     * <br></br>
     * - 현재 {@link UserPointTable} 설계 상, 주어진 ID를 가진 회원이 존재하지 않을 경우 0점을 가진 해당 회원을 새로 생성하여 저장합니다.
     * <br></br>
     * - 따라서 주어진 ID를 가진 회원이 없더라도 예외를 발생시키지 않습니다. 주어진 구조 내에서 정상 작동하기 때문입니다.
     */
    @Nested
    class PointCheckTests {

        /* 성공 테스트 : 이미 존재하는 회원의 포인트 잔액을 반환한다. */
        @Test
        void shouldReturnUserPointBalance_WhenUserIdGiven(){
            // given : ID가 1L인 회원이 존재한다. 해당 회원의 잔액은 10점이다.
            UserPoint userPoint = new UserPoint(USER_ID, 10L, System.currentTimeMillis());
            long userBalance = 10L;

            Mockito.when(userPointTable.selectById(USER_ID))
                    .thenReturn(userPoint);

            // when : 해당 회원의 포인트 잔액 조회가 발생한다.
            // then : 잔액이 10L인지 검증한다. 맞을 경우 통과한다.
            assertEquals(userBalance, pointService.getUserPoint(USER_ID).point());
        }

        /* 성공 테스트 : 존재하지 않는 회원의 포인트 잔액을 반환한다. 이때 해당 회원은 새로 생성되며, 잔액은 0이어야 한다. */
        @Test
        void shouldCreateUserAndReturnZeroBalance_WhenUserDoesntExist(){
            // given : mocking
            Mockito.when(userPointTable.selectById(USER_ID))
                    .thenReturn(new UserPoint(USER_ID, 0, System.currentTimeMillis()));

            // when : 존재하지 않는 회원의 잔액 조회 요청 발생
            // then : 이때 잔액이 0인 새로운 UserPoint가 생성되어 UserPointTable에 저장된다.
            assertEquals(0, userPointTable.selectById(USER_ID).point());
        }
    }

    /**
     * <b>4. 포인트 충전 및 사용 내역 조회 기능</b>
     * <br></br>
     *  - 포인트 충전 및 사용 내역 조회는 실패 테스트가 존재하지 않습니다.
     *  <br></br>
     *  - 포인트 충전 및 사용 내역은 <u>포인트 충전 및 사용 기능 실행에 따라 발생하는 사후적 로직</u>이기 때문입니다.
     *  <br></br>
     *  - 같은 맥락으로, ID 일치 회원이 존재하지 않는 경우 해당 회원은 잔액 0을 갖도록 새로 생성되지만,
     *    충전 혹은 사용이 발생한 것이 아니기 때문에 PointHistory가 생성되어 저장되지 않습니다.
     *
     */
    @Nested
    class PointHistoryTests {

        /* 성공 : 포인트 충전 이후 포인트 충전 내역 생성 및 조회 테스트 */
        @Test
        void shouldCreateAndRetrunPointHistory_WhenPointChargeOccurs(){
            // given : 1L 아이디를 가진 회원의 충전 전 잔액은 0원입니다. 30점 충전 발생합니다.
            UserPoint userPoint = new UserPoint(USER_ID, 0L, System.currentTimeMillis());
            long chargeAmount  = 30L;
            UserPoint updatedUserPoint = new UserPoint(USER_ID, 30L, System.currentTimeMillis());
            PointHistory createdPointHistory = new PointHistory(1L, USER_ID, updatedUserPoint.point(), TransactionType.CHARGE, System.currentTimeMillis());
            List<PointHistory> histories = List.of(createdPointHistory);

            Mockito.when(userPointTable.selectById(USER_ID))
                    .thenReturn(userPoint);
            Mockito.when(userPointTable.insertOrUpdate(USER_ID, chargeAmount))
                    .thenReturn(updatedUserPoint);
            Mockito.when(pointHistoryTable.selectAllByUserId(USER_ID))
                    .thenReturn(List.of(createdPointHistory));

            pointService.charge(USER_ID, chargeAmount);

            // when : 충전 후 해당 회원의 포인트 충전 및 사용 내역 전체 조회.
            List<PointHistory> result = pointService.getAllHistory(USER_ID);

            // then : 생성되어 반환된 포인트 충전 내역의 userId, amount(해당 유저 포인트 잔액), TransactionType.CHARGE가 모두 일치합니다.
            Assertions.assertThat(result)
                    .isEqualTo(histories);
        }
    }
}
