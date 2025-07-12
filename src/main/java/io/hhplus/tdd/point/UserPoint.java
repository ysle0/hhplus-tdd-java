package io.hhplus.tdd.point;

public record UserPoint(
        long id,
        long point,
        long updateMillis
) {
    public static final long EMPTY_POINT = Long.MIN_VALUE;

    public static UserPoint empty(long id) {
        return new UserPoint(id, EMPTY_POINT, System.currentTimeMillis());
    }

    public boolean isEmpty() {
        return point == EMPTY_POINT;
    }
}
