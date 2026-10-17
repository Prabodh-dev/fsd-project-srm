package dev.prabodh.examedge.entity;

public enum ProctoringEventType {
    TAB_SWITCH(true),
    WINDOW_BLUR(true),
    COPY_BLOCKED(false),
    PASTE_BLOCKED(false),
    CONTEXT_MENU_BLOCKED(false),
    VIOLATION_LIMIT_REACHED(false);

    private final boolean violation;

    ProctoringEventType(boolean violation) {
        this.violation = violation;
    }

    public boolean isViolation() {
        return violation;
    }
}
