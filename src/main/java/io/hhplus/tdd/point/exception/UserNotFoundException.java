package io.hhplus.tdd.point.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(long userID) {
        super(makeExMsg(userID));
    }

    public static String makeExMsg(long userID) {
        return "User not found. userID: %s".formatted(userID);
    }
}
