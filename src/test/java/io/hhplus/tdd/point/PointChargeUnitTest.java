package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.exception.NegativePointException;
import io.hhplus.tdd.point.exception.UserNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// 주어질 포인트 int 를 받고 문제가 생길 경우, 반드시 구현되어야 할 경우를 생각해보자.
// 1. 주어진 양만큼 충전하고 다시 조회하면 이전에 충전한 만큼의 포인트만 가지고 있어야함.
// 2. 음수의 포인트는 충전이 불가능 해야함
// 3. 충전한 양 만큼 UserPoint Table 과 Point History Table 에 record 가 존재해야함.
//    3 가지 경우 테스트

@ExtendWith(MockitoExtension.class)
public class PointChargeUnitTest {
    // test helpers
    private static final long TEST_USER_ID = 12324L;
    private static final long TOOK_TO_CALC_MS = System.currentTimeMillis();

    static Stream<Arguments> genKeepAmountNegativeAmountExpectedAmountTupleSource() {
        return Stream.of(
                Arguments.of(1, -1, 1),
                Arguments.of(100, -99, 100),
                Arguments.of(10_000, -232, 10_000),
                Arguments.of(10_000, -112232, 10_000));
    }

    // 주어진 파라미터의 포인트 만큼 충전하면 반드시 그 만큼 충전이 되어야 함.
    @DisplayName("should charge positive amount of points and have same amount.")
    @ParameterizedTest(name = "#{index} {0}")
    @ValueSource(ints = {1, 10, 100, 1_000, 1_000_000, 100_000_000})
    public final void should_charge_positive_amount_of_points_and_have_same_amount(long amount) {
        // ACQUIRE
        var userPointTable = Mockito.mock(UserPointTable.class);
        var pointHistoryTable = Mockito.mock(PointHistoryTable.class);
        var pointService = new PointService(userPointTable, pointHistoryTable);

        // - stubs for the dependencies
        // here the dependencies below are the database.
        // we don't want to care about any concrete implementation of those, since we
        // only would like to test
        // charging functionality.
        when(userPointTable.selectById(anyLong()))
                .thenReturn(new UserPoint(TEST_USER_ID, 0L, TOOK_TO_CALC_MS));
        when(userPointTable.insertOrUpdate(anyLong(), eq(amount)))
                .thenReturn(new UserPoint(TEST_USER_ID, amount, TOOK_TO_CALC_MS));

        when(pointHistoryTable.insert(anyLong(), eq(amount), eq(TransactionType.CHARGE), anyLong()))
                .thenReturn(new PointHistory(1L, TEST_USER_ID, amount, TransactionType.CHARGE,
                        TOOK_TO_CALC_MS));

        // ACT
        // test only our concerns as described above within the name of the function.
        final var userPoint = pointService.chargePoint(TEST_USER_ID, amount);

        // ASSERT
        // check the point has been correctly recharged with the same amount of a given
        // point.
        assert userPoint.point() == amount;
        // ensure stubbed methods are called in the correct order, and its parameter is
        // the same as we have arranged before.
        final var inOrder = Mockito.inOrder(userPointTable, pointHistoryTable);
        inOrder.verify(userPointTable)
                .insertOrUpdate(anyLong(), eq(amount));
        inOrder.verify(pointHistoryTable)
                .insert(anyLong(), eq(amount), eq(TransactionType.CHARGE), anyLong());
    }

    // Q: 음수 단위의 포인트 충전을 허용하지 않는 이유
    // A: 충전, 차감, 삭제 등의 모든 포인트 조작은 충전하는 순간이 아닌 이전의 어플리케이션에서 이루어진다.
    @DisplayName("should throw exception after charging negative amount of points")
    @ParameterizedTest(name = "#{index} {0}")
    @ValueSource(ints = {-1, -10, -100, -1_000, -10_000, -100_000, -1_000_000, -100_000_000})
    public void should_throw_exception_after_charging_negative_amount_of_points(long amount) {
        // ACQUIRE
        var userPointTable = Mockito.mock(UserPointTable.class);
        var pointHistoryTable = Mockito.mock(PointHistoryTable.class);
        var pointService = new PointService(userPointTable, pointHistoryTable);
        // we don't make any stubbing on the dependent table as they never be called
        // during the test.

        // ACT
        final var actualEx = assertThrows(
                NegativePointException.class,
                () -> pointService.chargePoint(TEST_USER_ID, amount));

        // ASSERT
        assert actualEx != null;
        assertEquals(
                NegativePointException.makeExMsg(amount),
                actualEx.getMessage());
        verifyNoInteractions(userPointTable, pointHistoryTable);
    }

    @DisplayName("should keep the same amount of points even after trying to charge negative amount of points")
    @ParameterizedTest(name = "#{index} ({0}, {1})")
    @MethodSource("genKeepAmountNegativeAmountExpectedAmountTupleSource")
    public void should_keep_the_same_amount_of_points_even_after_trying_to_charge_negative_amount_of_points(
            long keep, long negative, long expect) {
        // ACQUIRE
        var userPointTable = new UserPointTable();
        var pointHistoryTable = Mockito.mock(PointHistoryTable.class);
        var pointService = new PointService(userPointTable, pointHistoryTable);

        when(pointHistoryTable.insert(anyLong(), anyLong(), any(), anyLong()))
                .thenReturn(new PointHistory(1L, TEST_USER_ID, keep, TransactionType.CHARGE,
                        TOOK_TO_CALC_MS));

        pointService.chargePoint(TEST_USER_ID, keep);
        // we don't make any stubbing on the dependent table as they be never called
        // during the test.

        // ACT
        final var actualEx = assertThrows(
                NegativePointException.class,
                () -> pointService.chargePoint(TEST_USER_ID, negative));

        final var userPoint = userPointTable.selectById(TEST_USER_ID);
        assert userPoint.point() == expect;

        // ASSERT
        assert actualEx != null;
        assertEquals(
                NegativePointException.makeExMsg(negative),
                actualEx.getMessage());
    }

    @DisplayName("should charge zero amount of points")
    @Test
    public void should_charge_zero_amount_of_points() {
        // ACQUIRE
        var userPointTable = Mockito.mock(UserPointTable.class);
        var pointHistoryTable = Mockito.mock(PointHistoryTable.class);
        var pointService = new PointService(userPointTable, pointHistoryTable);

        when(userPointTable.selectById(anyLong()))
                .thenReturn(new UserPoint(TEST_USER_ID, 0L, TOOK_TO_CALC_MS));
        when(userPointTable.insertOrUpdate(anyLong(), eq(0L)))
                .thenReturn(new UserPoint(TEST_USER_ID, 0L, TOOK_TO_CALC_MS));

        when(pointHistoryTable.insert(anyLong(), eq(0L), eq(TransactionType.CHARGE), anyLong()))
                .thenReturn(new PointHistory(1L, TEST_USER_ID, 0L, TransactionType.CHARGE,
                        TOOK_TO_CALC_MS));

        // ACT
        final var userPoint = pointService.chargePoint(TEST_USER_ID, 0L);

        // ASSERT
        assert userPoint.point() == 0L;
        verify(userPointTable).insertOrUpdate(anyLong(), eq(0L));
        verify(pointHistoryTable).insert(anyLong(), eq(0L), eq(TransactionType.CHARGE), anyLong());
    }

    @DisplayName("should charge maximum long value amount of points")
    @Test
    public void should_charge_maximum_long_value_amount_of_points() {
        // ACQUIRE
        var userPointTable = Mockito.mock(UserPointTable.class);
        var pointHistoryTable = Mockito.mock(PointHistoryTable.class);
        var pointService = new PointService(userPointTable, pointHistoryTable);

        when(userPointTable.selectById(anyLong()))
                .thenReturn(new UserPoint(TEST_USER_ID, 0L, TOOK_TO_CALC_MS));
        when(userPointTable.insertOrUpdate(anyLong(), eq(Long.MAX_VALUE)))
                .thenReturn(new UserPoint(TEST_USER_ID, Long.MAX_VALUE, TOOK_TO_CALC_MS));

        when(pointHistoryTable.insert(anyLong(), eq(Long.MAX_VALUE), eq(TransactionType.CHARGE), anyLong()))
                .thenReturn(new PointHistory(1L, TEST_USER_ID, Long.MAX_VALUE, TransactionType.CHARGE,
                        TOOK_TO_CALC_MS));

        // ACT
        final var userPoint = pointService.chargePoint(TEST_USER_ID, Long.MAX_VALUE);

        // ASSERT
        assert userPoint.point() == Long.MAX_VALUE;
        verify(userPointTable).insertOrUpdate(anyLong(), eq(Long.MAX_VALUE));
        verify(pointHistoryTable).insert(anyLong(), eq(Long.MAX_VALUE), eq(TransactionType.CHARGE), anyLong());
    }

}
