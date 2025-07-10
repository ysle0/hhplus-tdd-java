package io.hhplus.tdd.point.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class NegativePointException extends RuntimeException {
    public NegativePointException(long p) {
        super(MakeExMsg(p));
    }

    public static String MakeExMsg(long p) {
        return "Negative point is not allowed. point: %d".formatted(p);
    }
}
