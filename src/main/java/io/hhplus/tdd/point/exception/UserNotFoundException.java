package io.hhplus.tdd.point.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(long userID) {
        super(MakeExMsg(userID));
    }

    public static String MakeExMsg(long userID) {
        return "User not found. userID: %s".formatted(userID);
    }
}
