package io.hhplus.tdd;

import io.hhplus.tdd.point.PointService;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;

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

    private static final long USER_ID = 1L;

    /**
     * <b>1. 포인트 충전 기능</b>
     */
    @Nested
    class PointChargeTests {

        /* 실패 : 충전 금액과 잔액 합이 최대 충전 금액을 초과할 경우, 충전 실패 후 IllegalArgumentException 이 발생한다. */
        @Test
        void shouldThrowException_WhenPointExceedsLimit_AfterCharge(){
            // given : 아이디가 1L인 사용자가 존재하고, 해당 사용자의 보유 포인트는 900_000 점이다.


            // when : 100,001 점의 충전 요청이 발생한다.
            // then : IllegalArgumentException 발생 시 테스트는 성공한다.


        }

        /* 실패 : 충전하고자 하는 금액이 0원 미만일 경우 실패한다. */
        @Test
        void shouldThrowException_IfChargeAmountBelowZero(){
            // given : 아이디가 1L인 사용자가 존재한다. 해당 사용자의 보유 포인트는 현재 10이다.

            // when : -1L 점의 충전 요청이 발생한다.

            // then : IllegalArgumentException 발생 시 테스트는 성공한다.

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

        /* 성공 : 올바른 충전 요청(0 이상의 충전 금액, 충전 시 허용 잔액 범위 이내)일 경우, 이를 수행하고 PointHistory를 생성하여 저장한다. */
        @Test
        void shouldCreateAndSavePointHistory_WhenPointChargeIsValid(){
            // given : 아이디가 1L인 사용자가 존재하고, 해당 사용자의 포유 포인트는 10이다.

            // when : 10점의 충전 요청이 발생한다. 이 충전 요청은 정책에 부합하는 유효한 요청이다.

            // then : 해당 충전에 대한 PointHistory가 정상적으로 생성 및 저장된다.
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
