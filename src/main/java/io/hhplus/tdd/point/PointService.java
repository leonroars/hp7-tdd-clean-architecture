package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import java.util.List;
import javax.naming.LimitExceededException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PointService {

    private final PointHistoryTable pointHistoryTable;
    private final UserPointTable userPointTable;

    /**
     * 포인트 충전 기능
     * @param userId
     * @param chargeAmount
     * @return
     */
    public UserPoint charge(long userId, long chargeAmount) {

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

    /**
     * 포인트 사용 기능
     * @param userId
     * @param useAmount
     * @return
     */
    public UserPoint use(long userId, long useAmount){

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
