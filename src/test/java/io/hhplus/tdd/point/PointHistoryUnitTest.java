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

public class PointHistoryUnitTest {
    private static final long TEST_USER_ID = 12324L;
    private static final long TOOK_TO_CALC_MS = System.currentTimeMillis();

    @DisplayName("should show matching point with point set")
    @ParameterizedTest(name = "#{index} {0}")
    @ValueSource(ints = {1, 10, 100, 1_000, 1_000_000, 100_000_000,})
    public void should_show_matching_point_with_point_set(final long amount) {
        // ACQUIRE
        var userPointTable = Mockito.mock(UserPointTable.class);
        var pointHistoryTable = new PointHistoryTable();
        var pointService = new PointService(userPointTable, pointHistoryTable);

        when(userPointTable.selectById(anyLong()))
                .thenReturn(new UserPoint(TEST_USER_ID, 0L, TOOK_TO_CALC_MS));
        when(userPointTable.insertOrUpdate(anyLong(), eq(amount)))
                .thenReturn(new UserPoint(TEST_USER_ID, amount, TOOK_TO_CALC_MS));

        // ACT
        pointService.chargePoint(TEST_USER_ID, amount);
        var pointHistories = pointService.showPointHistory(TEST_USER_ID);

        // ASSERT
        for (final var h : pointHistories) {
            assert h.amount() == amount;
        }

        verify(userPointTable).insertOrUpdate(anyLong(), eq(amount));
    }

    @DisplayName("should return user not found if wrong user ID sets or empty histories")
    @Test
    public void should_return_user_not_found_if_wrong_user_id_sets_or_empty_histories() {
        // ACQUIRE
        var userPointTable = new UserPointTable();
        var pointHistoryTable = new PointHistoryTable();
        var pointService = new PointService(userPointTable, pointHistoryTable);

        final long wrongUserID = 23410941902L;

        // ACT
        pointService.chargePoint(TEST_USER_ID, 100);
        pointService.chargePoint(TEST_USER_ID, 100_000);
        pointService.chargePoint(TEST_USER_ID, 23_143);

        var actualEx = assertThrows(
                UserNotFoundException.class,
                () -> pointService.showPointHistory(wrongUserID));

        // Assert
        assert actualEx instanceof UserNotFoundException;
        assertEquals(UserNotFoundException.makeExMsg(wrongUserID), actualEx.getMessage());
    }
}
