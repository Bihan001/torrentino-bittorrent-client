package me.bihan.util;

import java.time.Duration;

/**
 * Utility class for formatting various data types for display.
 * Centralizes formatting logic to avoid duplication across the codebase.
 */
public final class FormatUtils {
    
    private FormatUtils() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Format bytes in a human-readable format.
     * @param bytes Number of bytes to format
     * @return Formatted string (e.g., "1.5 MB", "256 KB", "42 B")
     */
    public static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
    
    /**
     * Format duration in a human-readable format.
     * @param duration Duration to format
     * @return Formatted string (e.g., "2h 30m 15s", "45m 20s", "12s")
     */
    public static String formatDuration(Duration duration) {
        if (duration == null) {
            return "0s";
        }
        
        long hours = duration.toHours();
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;
        
        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }
    
    /**
     * Format percentage with one decimal place.
     * @param percentage Percentage value (0.0 to 100.0)
     * @return Formatted string (e.g., "87.5%")
     */
    public static String formatPercentage(double percentage) {
        return String.format("%.1f%%", percentage);
    }
    
    /**
     * Format rate (bytes per second) in human-readable format.
     * @param bytesPerSecond Rate in bytes per second
     * @return Formatted string (e.g., "1.2 MB/s", "45 KB/s")
     */
    public static String formatRate(long bytesPerSecond) {
        return formatBytes(bytesPerSecond) + "/s";
    }
}