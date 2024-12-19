package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import javax.naming.LimitExceededException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PointService {

    private final PointHistoryTable pointHistoryTable;
    private final UserPointTable userPointTable;

    public UserPoint charge(long userId, long chargeAmount) {

        // 정책 : 충전하고자 하는 포인트가 0보다 작은 경우 예외를 발생시킨다.
        if(chargeAmount < 0){
            throw new IllegalArgumentException("0보다 작은 금액의 포인트 충전은 불가합니다.");
        }

        UserPoint userPoint = userPointTable.selectById(userId);
        UserPoint updatedUserPoint = new UserPoint(userId, userPoint.point() + chargeAmount, System.currentTimeMillis());

        if(updatedUserPoint.point() > 1_000_000){throw new IllegalArgumentException("허용된 포인트 한도를 초과합니다.");}

        return userPointTable.insertOrUpdate(updatedUserPoint.id(), updatedUserPoint.point());
    }

    public UserPoint use(long userId, long useAmount){

        if(useAmount < 0){throw new IllegalArgumentException("0 미만 금액의 사용은 불가합니다.");}

        UserPoint userPoint = userPointTable.selectById(userId);
        UserPoint updatedUserPoint = new UserPoint(userId, userPoint.point() - useAmount, System.currentTimeMillis());

        if(updatedUserPoint.point() < 0){throw new IllegalArgumentException("잔액 이상의 금액은 사용이 불가합니다.");}

        return userPointTable.insertOrUpdate(updatedUserPoint.id(), updatedUserPoint.point());
    }

    public UserPoint getUserPoint(long userId){
        return userPointTable.selectById(userId);
    }


}
