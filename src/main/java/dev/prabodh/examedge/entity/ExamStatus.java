package dev.prabodh.examedge.entity;

public enum ExamStatus {
    DRAFT("Draft", "neutral"),
    SCHEDULED("Scheduled", "info"),
    LIVE("Live", "success"),
    ENDED("Ended", "danger");

    private final String label;
    private final String badgeClass;

    ExamStatus(String label, String badgeClass) {
        this.label = label;
        this.badgeClass = badgeClass;
    }

    public String getLabel() {
        return label;
    }

    public String getBadgeClass() {
        return badgeClass;
    }
}
