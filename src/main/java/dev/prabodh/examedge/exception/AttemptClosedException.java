package dev.prabodh.examedge.exception;

public class AttemptClosedException extends BusinessRuleException {

    private final Long attemptId;

    public AttemptClosedException(Long attemptId, String message) {
        super(message);
        this.attemptId = attemptId;
    }

    public Long getAttemptId() {
        return attemptId;
    }
}
