package owoke.staffmatch.backend.user.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import owoke.staffmatch.backend.user.controller.MeController;

@RestControllerAdvice(basePackageClasses = MeController.class)
public class UserExceptionHandler {

    @ExceptionHandler(RoleConflictException.class)
    public ProblemDetail handleRoleConflict(RoleConflictException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }
}
