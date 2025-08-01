package me.bihan.service.strategy;

import lombok.Data;
import lombok.AllArgsConstructor;

/**
 * Represents information about a piece that needs to be downloaded.
 * Used in the work queue system.
 */
@Data
@AllArgsConstructor
public class PieceInfo {
    private final int index;
    private final long length;
    private int retryCount = 0;
    private long lastAttemptTime = 0;

    public PieceInfo(int index, long length) {
        this.index = index;
        this.length = length;
        this.retryCount = 0;
        this.lastAttemptTime = 0;
    }

    /**
     * Increment retry count and update last attempt time.
     */
    public void incrementRetry() {
        this.retryCount++;
        this.lastAttemptTime = System.currentTimeMillis();
    }

    /**
     * Check if this piece should be retried based on retry count and time.
     */
    public boolean canRetry(int maxRetries, long retryDelayMs) {
        if (retryCount >= maxRetries) {
            return false;
        }
        
        if (lastAttemptTime == 0) {
            return true;
        }
        
        return (System.currentTimeMillis() - lastAttemptTime) >= retryDelayMs;
    }

    @Override
    public String toString() {
        return String.format("Piece{index=%d, length=%d, retries=%d}", index, length, retryCount);
    }
} 