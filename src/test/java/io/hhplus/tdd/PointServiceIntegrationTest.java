package io.hhplus.tdd;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.PointService;
import io.hhplus.tdd.point.UserPoint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;


/**
 * 포인트 충전/사용 및 이에 따른 포인트 사용, 충전 내역 생성과 조회를 검증하는 통합 테스트
 * - 동시 발생하는 사용 및 충전 요청 수는 각각 25개로 설정한다.
 * <br></br>
 * - 예외를 발생시키지 않도록 사용자 생성 시 높은 초기 보유 잔액을 갖도록 한다.
 * <br></br>
 * - 또한 모든 충전/사용 요청 후에도 잔액이 정책을 위반하지 않도록 각 충전 및 사용 금액 크기는 각각 100, 50으로 설정한다. 다르게 설정함으로써 결과가 두드러지도록 한다.
 * <br></br>
 * - 해당 테스트의 목적은 <u>동시성 환경에서도 서비스 로직이 의도한 바대로 정상 작동하는 것을 검증하는 것</u>이기 때문에,
 *    테스트 도중 예외가 발생하는 상황을 상정하기보다 정상적인 반환값이 나오도록 테스트를 설계하여 결과에 대한 검증을 하는 것에 집중한다.
 */
public class PointServiceIntegrationTest {
    PointService pointService;
    UserPoint userPoint;

    private static final long initialBalance = 900_000L; // 사용자 최초 보유 잔액. 이를 넉넉하게 잡음으로써 예외 발생 여부 고려하지 않아도 동시성 문제 해결 여부 검증 가능하다.
    private static final int numberOfUseRequest = 25; // 사용 요청(스레드) 수
    private static final int numberOfChargeRequest = 25; // 충전 요청(스레드) 수
    private static final long amountPerCharge = 100L; // 충전 단위 금액
    private static final long amountPerUse = 50L;


    /**
     * 매 테스트 시행 이전에 PointService 필드에 주입될 인스턴스 및
     * 해당 인스턴스의 필드에 주입될 PointHistoryTable, UserPointTable 인스턴스 모두 새로 생성되어 주입되도록 한다.
     */
    @BeforeEach
    public void init(){
        pointService = new PointService(new PointHistoryTable(), new UserPointTable());
        userPoint = new UserPoint(1L, 0L, System.currentTimeMillis());
        pointService.charge(userPoint.id(), initialBalance); // 최초 충전 시행.
    }

    /**
     * 충전 요청을 흉내내기 위해, 충전 기능 호출을 ExeutorService에 등록 및 실행 가능한 작업(Task) 형태로 가공한다.
     * <br></br>
     * 생성된 25개의 충전 요청이 이후 ExecutorService.invokeAll()에 의해 동시에 실행된다.
     *
     * @param userId
     * @param amountPerCharge
     * @param numberOfChargeRequest
     * @return
     */
    private List<Callable<Void>> generateChargeTask(long userId,
                                                    long amountPerCharge,
                                                    int numberOfChargeRequest){
        List<Callable<Void>> taskList = new ArrayList<>();
        for(int i = 0; i < numberOfChargeRequest; i++){
            taskList.add(() -> {
                pointService.charge(userId, amountPerCharge);
                return null;
            });
        }
        return taskList;
    }

    /**
     * 사용 요청을 흉내내기 위해, 사용 기능 호출을 ExeutorService에 등록 및 실행 가능한 작업(Task) 형태로 가공한다.
     * <br></br>
     * 생성된 25개의 사용 요청이 이후 ExecutorService.invokeAll()에 의해 동시에 실행된다.
     *
     * @param userId
     * @param amountPerUse
     * @param numberOfUseRequest
     * @return
     */
    private List<Callable<Void>> generateUseTask(long userId,
                                                    long amountPerUse,
                                                    int numberOfUseRequest){
        List<Callable<Void>> taskList = new ArrayList<>();
        for(int i = 0; i < numberOfUseRequest; i++){
            taskList.add(() -> {
                pointService.use(userId, amountPerUse);
                return null;
            });
        }
        return taskList;
    }

    /* 실제 서비스 시 발생하는 사용 및 충전 요청 발생의 무작위성을 흉내내기 위해, 생성된 각 25개의 충전, 사용 요청을 섞은 리스트를 반환한다. */
    private List<Callable<Void>> generateShuffledChargeAndUse(long userId,
                                                              long amountPerCharge,
                                                              long amountPerUse,
                                                              int numberOfChargeRequest,
                                                              int numberOfUseRequest){
        List<Callable<Void>> jobs = new ArrayList<>();
        jobs.addAll(generateChargeTask(userId, amountPerCharge, numberOfChargeRequest));
        jobs.addAll(generateUseTask(userId, amountPerUse, numberOfUseRequest));

        Collections.shuffle(jobs);

        return jobs;
    }


    /**
     * 시나리오 : 동일한 사용자에 대해 동시에 다수의 충전 및 사용 요청이 발생한다.
     * <br></br>
     * 이때 순차적으로 처리되도록 하여 동시성 문제가 발생하지 않도록 한다.
     */
    @Test
    @DisplayName("동일한 사용자에 대한 동시 다발적 충전 및 사용 요청이 발생")
    void shouldBehaveProperly_WhenConcurrentUseAndChargeOccurs_ForSameUser() throws InterruptedException, ExecutionException {
        // given : 아이디 1L, 잔액 900_000을 가진 사용자가 존재한다. 해당 사용자에게 각각 25개의 사용 및 충전 요청이 생성된다.
        ExecutorService executorService = Executors.newFixedThreadPool(numberOfChargeRequest + numberOfUseRequest);
        List<Callable<Void>> shuffledTask = generateShuffledChargeAndUse(userPoint.id(), amountPerCharge, amountPerUse, numberOfChargeRequest, numberOfUseRequest);

        long expectedBalance = 901_250L; // 동시 요청이 모두 순차적으로 올바르게 처리되었을 때의 잔액.

        // when : ExecutorService의 스레드 풀 내에 생성되어 등록되어있던 스레드들이 주어진 작업 목록을 동시 실행한다.
        List<Future<Void>> futures = executorService.invokeAll(shuffledTask); // 생성해둔 요청 전체 동시 실행 시작.
        executorService.shutdown(); // 실행된 shuffledTask 내의 Task 부터 순차 종료

        for(Future<Void> future : futures){
            future.get(); // invokeAll()에 의해 실행되었던 작업이 종료될때까지 기다렸다가 해당 작업의 결과물을 회수한다.
        }

        // then 1 : 포인트 잔액 일치 여부 검증
        Assertions.assertEquals(expectedBalance, pointService.getUserPoint(userPoint.id()).point());

        // then 2 : 포인트 충전 및 사용 내역 총 51개(최초 생성 시 1개 포함) 여부 확인.
        Assertions.assertEquals(51, pointService.getAllHistory(userPoint.id()).size());





    }

    /**
     * 시나리오 : 다수의 사용자에 대한 동시 다발적 충전 및 사용 요청이 발생한다.
     * <br></br>
     * 이때 순차적으로 처리되도록 하여 동시성 문제가 발생하지 않도록 한다.
     */
    @Test
    @DisplayName("복수의 사용자에 대한 동시 다발적 충전 및 사용 요청이 발생")
    void shouldBehaveProperly_WhenConcurrentUseAndChargeOccurs_ForManyUser(){

    }


}
