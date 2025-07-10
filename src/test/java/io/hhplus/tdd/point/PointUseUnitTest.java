package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.exception.NegativePointException;
import io.hhplus.tdd.point.exception.NotEnoughPointException;
import io.hhplus.tdd.point.exception.UserNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class PointUseUnitTest {
    // test helpers
    private static final long TEST_USER_ID = 12324L;

    static Stream<Arguments> genKeepUsedExpectAmountTupleSource() {
        return Stream.of(
                Arguments.of(1, 0, 1),
                Arguments.of(100, 99, 1),
                Arguments.of(10_000, 232, 10_000 - 232),
                Arguments.of(10_000, 1122, 10_000 - 1122),
                Arguments.of(100_000_000, 99_900_099, 100_000_000 - 99_900_099));
    }

    @DisplayName("should never use negative amount of points and kept points are unchanged")
    @ParameterizedTest(name = "#{index} ({0}, {1})")
    @ValueSource(longs = {-1, -10, -100, -1_000, -10_000, -100_000, -100_000_000})
    public void should_throw_exception_on_using_negative_amount_of_point(long used) {
        // ACQUIRE
        var userPointTable = new UserPointTable();
        var pointHistoryTable = new PointHistoryTable();
        var pointService = new PointService(userPointTable, pointHistoryTable);

        // ACT
        // use a negative number of points.
        var actualEx = assertThrows(
                NegativePointException.class,
                () -> pointService.usePoint(TEST_USER_ID, used));

        // ASSERT
        assert actualEx != null;
        assert actualEx instanceof NegativePointException;
        assert NegativePointException.MakeExMsg(used)
                .equals(actualEx.getMessage());
    }

    @DisplayName("should throw UserNotFoundException if no user found")
    @Test
    public void should_throw_exception_if_no_user_found() {
        // ACQUIRE
        var userPointTable = new UserPointTable();
        var pointHistoryTable = Mockito.mock(PointHistoryTable.class);
        var pointService = new PointService(userPointTable, pointHistoryTable);

        final long wrongUserID = 23410941902L;

        // ACT
        final var actualEx = assertThrows(
                UserNotFoundException.class,
                () -> pointService.showPoint(wrongUserID));

        // ASSERT
        assert actualEx != null;
        assertEquals(
                UserNotFoundException.MakeExMsg(wrongUserID),
                actualEx.getMessage());

        verify(pointHistoryTable, Mockito.times(0))
                .insert(anyLong(), anyLong(), any(), anyLong());
    }

    @DisplayName("should throw NotEnoughPointException when using more points than available")
    @Test
    public void should_throw_not_enough_point_exception_when_using_more_points_than_available() {
        // ACQUIRE
        var userPointTable = new UserPointTable();
        var pointHistoryTable = new PointHistoryTable();
        var pointService = new PointService(userPointTable, pointHistoryTable);

        long availablePoints = 100L;
        long requestedPoints = 150L;

        // ACT
        pointService.chargePoint(TEST_USER_ID, availablePoints);
        var result = assertThrows(
                NotEnoughPointException.class,
                () -> pointService.usePoint(TEST_USER_ID, requestedPoints));

        // ASSERT
        assert result != null;
        assert result instanceof NotEnoughPointException;
        assert result.getMessage().equals(
                NotEnoughPointException.MakeExMsg(TEST_USER_ID, requestedPoints, availablePoints));

        // Check that user still has original points
        var userPoint = userPointTable.selectById(TEST_USER_ID);
        assert userPoint.point() == availablePoints;

        // Check that only charge transaction exists in history
        var histories = pointHistoryTable.selectAllByUserId(TEST_USER_ID);
        assert histories.size() == 1;
        assert histories.get(0).type() == TransactionType.CHARGE;
    }

    @DisplayName("should succeed when using exact balance")
    @Test
    public void should_succeed_when_using_exact_balance() {
        // ACQUIRE
        var userPointTable = new UserPointTable();
        var pointHistoryTable = new PointHistoryTable();
        var pointService = new PointService(userPointTable, pointHistoryTable);

        long exactBalance = 100L;

        // ACT
        pointService.chargePoint(TEST_USER_ID, exactBalance);
        var result = pointService.usePoint(TEST_USER_ID, exactBalance);

        // ASSERT
        assert result.point() == 0L;

        // Check that user has zero points
        var userPoint = userPointTable.selectById(TEST_USER_ID);
        assert userPoint.point() == 0L;

        // Check that both transactions exist in history
        var histories = pointHistoryTable.selectAllByUserId(TEST_USER_ID);
        assert histories.size() == 2;
        assert histories.get(0).type() == TransactionType.CHARGE;
        assert histories.get(1).type() == TransactionType.USE;
    }

    @DisplayName("should use zero amount of points")
    @Test
    public void should_use_zero_amount_of_points() {
        // ACQUIRE
        var userPointTable = new UserPointTable();
        var pointHistoryTable = new PointHistoryTable();
        var pointService = new PointService(userPointTable, pointHistoryTable);

        long initialPoints = 100L;

        // ACT
        pointService.chargePoint(TEST_USER_ID, initialPoints);
        var result = pointService.usePoint(TEST_USER_ID, 0L);

        // ASSERT
        assert result.point() == initialPoints;

        // Check that user still has original points
        var userPoint = userPointTable.selectById(TEST_USER_ID);
        assert userPoint.point() == initialPoints;

        // Check that both transactions exist in history
        var histories = pointHistoryTable.selectAllByUserId(TEST_USER_ID);
        assert histories.size() == 2;
        assert histories.get(1).amount() == 0L;
        assert histories.get(1).type() == TransactionType.USE;
    }
}
