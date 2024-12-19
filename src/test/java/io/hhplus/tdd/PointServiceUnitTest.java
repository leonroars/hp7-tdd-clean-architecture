package io.hhplus.tdd;

import static org.awaitility.Awaitility.given;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.PointService;
import io.hhplus.tdd.point.UserPoint;
import javax.naming.LimitExceededException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
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
                    .isInstanceOf(LimitExceededException.class)
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

            // when : 50,000 점의 충전 요청이 발생한다.

            // then : 예외 발생 없이 성공하는지, 충전 후 잔액이 950,000점이 맞는지 검증한다. 문제 없을 경우 성공.
        }
    }


    /**
     * <b>2. 포인트 사용 기능</b>
     */
    @Nested
    class PointUsageTests {

    }

    /**
     * <b>3. 포인트 잔액 조회 기능</b>
     */
    @Nested
    class PointCheckTests {

    }

    /**
     * <b>4. 포인트 충전 및 사용 내역 조회 기능</b>
     */
    @Nested
    class PointHistoryTests {

    }
}
