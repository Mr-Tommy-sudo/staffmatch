package owoke.staffmatch.backend.user;

public class RoleConflictException extends RuntimeException {

    public RoleConflictException() {
        super("This account already has a different role");
    }
}
