package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.exception.UserNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PointShowUnitTest {
    private static final long TEST_USER_ID = 12324L;
    private static final long TOOK_TO_CALC_MS = System.currentTimeMillis();

    @DisplayName("should show matching point with point set")
    @ParameterizedTest(name = "#{index} {0}")
    @ValueSource(ints = {1, 10, 100, 1_000, 1_000_000, 100_000_000,})
    public void should_show_matching_point_with_point_set(long amount) {
        // ACQUIRE
        var userPointTable = new UserPointTable();
        var pointHistoryTable = Mockito.mock(PointHistoryTable.class);
        var pointService = new PointService(userPointTable, pointHistoryTable);

        when(pointHistoryTable.insert(anyLong(), eq(amount), eq(TransactionType.CHARGE), anyLong()))
                .thenReturn(new PointHistory(1L, TEST_USER_ID, amount, TransactionType.CHARGE, TOOK_TO_CALC_MS));

        // ACT
        pointService.chargePoint(TEST_USER_ID, amount);
        var userPointRecord = pointService.showPoint(TEST_USER_ID);

        // ASSERT
        assert userPointRecord.point() == amount;
        verify(pointHistoryTable).insert(anyLong(), eq(amount), eq(TransactionType.CHARGE), anyLong());
    }

    @DisplayName("should throw exception if no user found")
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
                .insert(wrongUserID, wrongUserID, null, wrongUserID);
    }
}
