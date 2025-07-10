package io.hhplus.tdd.point.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class NotEnoughPointException extends RuntimeException {
    public NotEnoughPointException(long userID, long requested, long available) {
        super(MakeExMsg(userID, requested, available));
    }

    public static String MakeExMsg(long userID, long requested, long available) {
        return "Not enough points. userID: %d, requested: %d, available: %d".formatted(userID, requested, available);
    }

}
