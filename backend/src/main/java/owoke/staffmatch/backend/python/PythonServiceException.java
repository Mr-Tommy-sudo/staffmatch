package owoke.staffmatch.backend.python;

public class PythonServiceException extends RuntimeException {
    private final int status;

    public PythonServiceException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() { return status; }
}
