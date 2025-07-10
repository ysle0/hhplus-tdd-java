package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class PointDataConsistencyUnitTest {
    private static final long TEST_USER_ID = 12324L;

    @DisplayName("should maintain consistency between UserPoint and PointHistory tables after charge")
    @Test
    public void should_maintain_consistency_between_tables_after_charge() {
        // ACQUIRE
        var userPointTable = new UserPointTable();
        var pointHistoryTable = new PointHistoryTable();
        var pointService = new PointService(userPointTable, pointHistoryTable);

        long chargeAmount = 100L;

        // ACT
        pointService.chargePoint(TEST_USER_ID, chargeAmount);

        // Get data from both tables
        var userPoint = userPointTable.selectById(TEST_USER_ID);
        var pointHistories = pointHistoryTable.selectAllByUserId(TEST_USER_ID);

        // ASSERT
        assert userPoint.point() == chargeAmount;
        assert pointHistories.size() == 1;
        assert pointHistories.get(0).amount() == chargeAmount;
        assert pointHistories.get(0).type() == TransactionType.CHARGE;
        assert pointHistories.get(0).userId() == TEST_USER_ID;
    }

    @DisplayName("should maintain consistency between UserPoint and PointHistory tables after use")
    @Test
    public void should_maintain_consistency_between_tables_after_use() {
        // ACQUIRE
        var userPointTable = new UserPointTable();
        var pointHistoryTable = new PointHistoryTable();
        var pointService = new PointService(userPointTable, pointHistoryTable);

        long initialAmount = 100L;
        long useAmount = 30L;
        long expectedBalance = initialAmount - useAmount;

        // ACT
        pointService.chargePoint(TEST_USER_ID, initialAmount);
        pointService.usePoint(TEST_USER_ID, useAmount);

        // Get data from both tables
        var userPoint = userPointTable.selectById(TEST_USER_ID);
        var pointHistories = pointHistoryTable.selectAllByUserId(TEST_USER_ID);

        // ASSERT
        assert userPoint.point() == expectedBalance;
        assert pointHistories.size() == 2;

        // Check charge transaction
        var chargeHistory = pointHistories.get(0);
        assert chargeHistory.amount() == initialAmount;
        assert chargeHistory.type() == TransactionType.CHARGE;
        assert chargeHistory.userId() == TEST_USER_ID;

        // Check use transaction
        var useHistory = pointHistories.get(1);
        assert useHistory.amount() == useAmount;
        assert useHistory.type() == TransactionType.USE;
        assert useHistory.userId() == TEST_USER_ID;
    }

    @DisplayName("should maintain consistency with multiple operations")
    @Test
    public void should_maintain_consistency_with_multiple_operations() {
        // ACQUIRE
        var userPointTable = new UserPointTable();
        var pointHistoryTable = new PointHistoryTable();
        var pointService = new PointService(userPointTable, pointHistoryTable);

        // ACT
        pointService.chargePoint(TEST_USER_ID, 100L);
        pointService.usePoint(TEST_USER_ID, 20L);
        pointService.chargePoint(TEST_USER_ID, 50L);
        pointService.usePoint(TEST_USER_ID, 30L);

        // Get data from both tables
        var userPoint = userPointTable.selectById(TEST_USER_ID);
        var pointHistories = pointHistoryTable.selectAllByUserId(TEST_USER_ID);

        // ASSERT
        // Expected balance: 100 - 20 + 50 - 30 = 100
        assert userPoint.point() == 100L;
        assert pointHistories.size() == 4;

        // Check transaction sequence
        assert pointHistories.get(0).type() == TransactionType.CHARGE;
        assert pointHistories.get(0).amount() == 100L;

        assert pointHistories.get(1).type() == TransactionType.USE;
        assert pointHistories.get(1).amount() == 20L;

        assert pointHistories.get(2).type() == TransactionType.CHARGE;
        assert pointHistories.get(2).amount() == 50L;

        assert pointHistories.get(3).type() == TransactionType.USE;
        assert pointHistories.get(3).amount() == 30L;

        // All transactions should be for the same user
        for (var history : pointHistories) {
            assert history.userId() == TEST_USER_ID;
        }
    }

    @DisplayName("should verify total balance matches history sum")
    @Test
    public void should_verify_total_balance_matches_history_sum() {
        // ACQUIRE
        var userPointTable = new UserPointTable();
        var pointHistoryTable = new PointHistoryTable();
        var pointService = new PointService(userPointTable, pointHistoryTable);

        // ACT
        pointService.chargePoint(TEST_USER_ID, 200L);
        pointService.usePoint(TEST_USER_ID, 50L);
        pointService.chargePoint(TEST_USER_ID, 100L);
        pointService.usePoint(TEST_USER_ID, 80L);

        // Get data from both tables
        var userPoint = userPointTable.selectById(TEST_USER_ID);
        var pointHistories = pointHistoryTable.selectAllByUserId(TEST_USER_ID);

        // ASSERT
        // Calculate expected balance from history
        long totalCharged = pointHistories.stream()
                .filter(h -> h.type() == TransactionType.CHARGE)
                .mapToLong(PointHistory::amount)
                .sum();

        long totalUsed = pointHistories.stream()
                .filter(h -> h.type() == TransactionType.USE)
                .mapToLong(PointHistory::amount)
                .sum();

        long expectedBalance = totalCharged - totalUsed;

        assert userPoint.point() == expectedBalance;
        assert totalCharged == 300L; // 200 + 100
        assert totalUsed == 130L; // 50 + 80
        assert expectedBalance == 170L; // 300 - 130
    }
}