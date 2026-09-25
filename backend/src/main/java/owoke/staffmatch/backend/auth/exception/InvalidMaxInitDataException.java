package owoke.staffmatch.backend.auth.exception;

public class InvalidMaxInitDataException extends RuntimeException {

    public InvalidMaxInitDataException() {
        super("Invalid or expired MAX init data");
    }
}
