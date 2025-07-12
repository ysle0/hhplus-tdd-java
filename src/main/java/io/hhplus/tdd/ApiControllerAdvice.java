package io.hhplus.tdd;

import io.hhplus.tdd.point.exception.NegativePointException;
import io.hhplus.tdd.point.exception.NotEnoughPointException;
import io.hhplus.tdd.point.exception.UserNotFoundException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
class ApiControllerAdvice extends ResponseEntityExceptionHandler {
    @ExceptionHandler(value = Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        return ResponseEntity.status(500).body(new ErrorResponse("500", "에러가 발생했습니다."));
    }

    @ExceptionHandler(value = NotEnoughPointException.class)
    public ResponseEntity<ErrorResponse> handleNotEnoughPointException(Exception e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("400", e.getMessage()));
    }

    @ExceptionHandler(value = NegativePointException.class)
    public ResponseEntity<ErrorResponse> handleNegativePointException(Exception e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("400", e.getMessage()));
    }

    @ExceptionHandler(value = UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFoundException(Exception e) {
        return ResponseEntity.badRequest().body(new ErrorResponse("400", e.getMessage()));
    }
}
