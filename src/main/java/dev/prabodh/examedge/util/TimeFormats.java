package dev.prabodh.examedge.util;

public final class TimeFormats {

    private TimeFormats() {
    }

    public static String duration(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format("%dh %02dm %02ds", hours, minutes, seconds);
        }
        return String.format("%dm %02ds", minutes, seconds);
    }
}
