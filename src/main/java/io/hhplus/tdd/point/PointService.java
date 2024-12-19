package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import javax.naming.LimitExceededException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PointService {

    private final PointHistoryTable pointHistoryTable;
    private final UserPointTable userPointTable;

    /*
       사용자 별 락 상태를 보관하는 ConcurrentHashMap.

       * 왜 ConcurrentHashMap 인가?
        - 동시성 문제 발생 시나리오를 생각해보면 동일 사용자에 대한 포인트 충전 및 사용이 동시에 발생해서는 안됨을 알 수 있다.
          이를 예방하기 위해서는 '어떤 사용자에 대해', '어떤 락'이 걸려있는지에 관한 정보 어떤 '목록'에 저장함으로써 이를를 추적할 필요가 있다.
          또한 락이 해제될 경우 해당 '목록'으로부터 그 락의 정보를 제거해야한다.

          이처럼 특정 사용자의 포인트 잔액에 대한 어떤 쓰기 연산이 다른 스레드에 의해 진행 중이라는 것을 다른 스레드들이 알 필요가 있고,
          해제가 됨으로써 사용 가능함 또한 다른 스레드가 확인할 수 있어야 한다.

          또한 동일 사용자가 아닌 복수의 사용자에 대한 쓰기 연산이 종료되어 동시에 여러 개의 락 상태가 해제될 가능성도 존재한다.

          그렇기 때문에 ConcurrentHashMap을 활용하여 이런 정보를 관리함으로써 복수의 스레드에 의해 즉각적으로 갱신 및 조회가 가능하도록 한다.
          이는 dead-lock 상태가 쉬이 발생하지 않도록 하는데에 주요하다.
     */
    private final ConcurrentHashMap<Long, Lock> perUserLockStatus = new ConcurrentHashMap<>();


    /**
     * 포인트 충전 기능
     * @param userId
     * @param chargeAmount
     * @return
     */
    public UserPoint charge(long userId, long chargeAmount) {

        // 현재 충전이 수행될 사용자에 대한 쓰기 작업 락 여부를 확인한다. 없을 경우, 현재 충전 연산을 수행하려하는 현 스레드가 락을 설정하고 보유한다.
        Lock reEntrantLock = perUserLockStatus.computeIfAbsent(userId, mapping -> new ReentrantLock(true));
        reEntrantLock.lock(); // 락 설정!
        long lockAttainedAt = System.currentTimeMillis(); // 락 획득 시점 기준 순차 수행하도록 설계한다!
        log.info("충전 작업 - 락을 획득했습니다 : " + lockAttainedAt); // 락 획득 시점 로깅.

        try {
            // 정책 : 충전하고자 하는 포인트가 0보다 작은 경우 예외를 발생시킨다.
            if(chargeAmount < 0){
                throw new IllegalArgumentException("0보다 작은 금액의 포인트 충전은 불가합니다.");
            }

            UserPoint userPoint = userPointTable.selectById(userId);
            UserPoint updatedUserPoint = new UserPoint(userId, userPoint.point() + chargeAmount, System.currentTimeMillis());

            // 정책 : 충전하고자 하는 포인트가 최대 충전 한도인 1,000,000점 이상일 경우 예외를 발생시킨다.
            if(updatedUserPoint.point() > 1_000_000){throw new IllegalArgumentException("허용된 포인트 한도를 초과합니다.");}

            // 정책 위반 사항이 없어 정상 충전 가능한 경우, 충전 이력을 생성하고 저장한다.
            pointHistoryTable.insert(userId, chargeAmount, TransactionType.CHARGE, System.currentTimeMillis());

            return userPointTable.insertOrUpdate(updatedUserPoint.id(), updatedUserPoint.point());
        }
        finally {
            long lockReleasedAt = System.currentTimeMillis();
            log.info("충전 작업 - 락이 해제되었습니다. : {}", lockReleasedAt);
            log.info("충전 작업 - 총 락 유지 시간 : {}", lockReleasedAt - lockAttainedAt);
            reEntrantLock.unlock(); // 명시적 잠금 해제.
        }

    }

    /**
     * 포인트 사용 기능
     * @param userId
     * @param useAmount
     * @return
     */
    public UserPoint use(long userId, long useAmount){

        Lock reEntrantLock = perUserLockStatus.computeIfAbsent(userId, mapping -> new ReentrantLock(true));
        reEntrantLock.lock(); // 락 설정!
        long lockAttainedAt = System.currentTimeMillis();
        log.info("사용 작업 - 락을 획득했습니다 : " + lockAttainedAt);

        try {

            // 정책 : 사용 금액이 0 미만인 경우 예외를 발생시킨다.
            if(useAmount < 0){throw new IllegalArgumentException("0 미만 금액의 사용은 불가합니다.");}

            UserPoint userPoint = userPointTable.selectById(userId);
            UserPoint updatedUserPoint = new UserPoint(userId, userPoint.point() - useAmount, System.currentTimeMillis());

            // 정책 : 차감 후 금액이 0보다 작아질 경우, 유효하지 않으므로 예외를 발생시킨다.
            if(updatedUserPoint.point() < 0){throw new IllegalArgumentException("잔액 이상의 금액은 사용이 불가합니다.");}

            // 정책 위반이 없는 경우, 정상적으로 사용 처리 후 사용 내역을 생성하여 저장한다.
            pointHistoryTable.insert(userId, useAmount, TransactionType.USE, System.currentTimeMillis());
            return userPointTable.insertOrUpdate(updatedUserPoint.id(), updatedUserPoint.point());
        }
        finally {
            long lockReleasedAt = System.currentTimeMillis();
            log.info("사용 작업 - 락이 해제되었습니다. : {}", lockReleasedAt);
            log.info("사용 작업 - 총 락 유지 시간 : {}", lockReleasedAt - lockAttainedAt);
            reEntrantLock.unlock(); // 명시적 락 해제.
        }
    }

    /**
     * 포인트 잔액 조회 기능.
     * @param userId
     * @return
     */
    public UserPoint getUserPoint(long userId){
        return userPointTable.selectById(userId);
    }

    /**
     * 포인트 사용 및 충전 내역 전체 조회
     * @param userId
     * @return
     */
    public List<PointHistory> getAllHistory(long userId){
        return pointHistoryTable.selectAllByUserId(userId);
    }


}
