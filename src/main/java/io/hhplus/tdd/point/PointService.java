package io.hhplus.tdd.point;

import io.hhplus.tdd.database.PointHistoryTable;
import io.hhplus.tdd.database.UserPointTable;
import io.hhplus.tdd.point.exception.NegativePointException;
import io.hhplus.tdd.point.exception.NotEnoughPointException;
import io.hhplus.tdd.point.exception.UserNotFoundException;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class PointService {
    private final UserPointTable userPointTable;
    private final PointHistoryTable pointHistoryTable;

    public PointService(UserPointTable upt, PointHistoryTable pht) {
        this.userPointTable = upt;
        this.pointHistoryTable = pht;
    }

    public UserPoint chargePoint(long userID, long amount) throws NegativePointException {
        if (amount < 0L) {
            throw new NegativePointException(amount);
        }

        final long updateStart = System.nanoTime();
        final var found = userPointTable.selectById(userID);

        UserPoint newUserPoint;
        if (found.isEmpty()) {
            // If user does not exist, create a new UserPoint with the given amount
            newUserPoint = userPointTable.insertOrUpdate(userID, amount);
        } else {
            final var addedPoint = found.point() + amount;
            newUserPoint = userPointTable.insertOrUpdate(userID, addedPoint);
        }

        final long updateTook = (System.nanoTime() - updateStart) / 1_000;
        pointHistoryTable.insert(userID, amount, TransactionType.CHARGE, updateTook);

        return newUserPoint;
    }

    public UserPoint usePoint(long userID, long amount)
            throws NegativePointException, UserNotFoundException, NotEnoughPointException {
        if (amount < 0) {
            throw new NegativePointException(amount);
        }

        final long updateStartMs = System.nanoTime();
        final var found = userPointTable.selectById(userID);
        if (found.isEmpty()) {
            throw new UserNotFoundException(userID);
        }

        final var diff = found.point() - amount;
        if (diff < 0) {
            throw new NotEnoughPointException(userID, amount, found.point());
        }

        final var newUserPoint = userPointTable.insertOrUpdate(userID, diff);
        final long updateTookMs = (System.nanoTime() - updateStartMs) / 1_000_000;
        pointHistoryTable.insert(userID, amount, TransactionType.USE, updateTookMs);

        return newUserPoint;
    }

    public UserPoint showPoint(long userID) throws UserNotFoundException {
        var found = userPointTable.selectById(userID);
        if (found.isEmpty()) {
            throw new UserNotFoundException(userID);
        }

        return found;
    }

    public List<PointHistory> showPointHistory(long userID) throws UserNotFoundException {
        var founds = pointHistoryTable.selectAllByUserId(userID);
        if (founds.isEmpty()) {
            throw new UserNotFoundException(userID);
        }

        return founds;
    }
}
