package owoke.staffmatch.backend.user.exception;

public class RoleConflictException extends RuntimeException {

    public RoleConflictException() {
        super("This account already has a different role");
    }
}
