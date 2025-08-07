package me.bihan.service;

import lombok.Builder;
import lombok.Value;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Immutable statistics for seeding operations.
 * Provides metrics about upload performance and peer activity.
 */
@Value
@Builder(toBuilder = true)
public class SeedingStats {
    
    /** Total bytes uploaded in this seeding session */
    long totalUploaded;
    
    /** Total bytes downloaded in this seeding session */
    long totalDownloaded;
    
    /** Number of currently connected peers */
    int activePeers;
    
    /** Number of peers that have shown interest */
    int interestedPeers;
    
    /** Current upload speed in bytes per second */
    long uploadSpeed;
    
    /** Current download speed in bytes per second */
    long downloadSpeed;
    
    /** Average upload rate since seeding started */
    long averageUploadRate;
    
    /** Total number of pieces uploaded */
    int piecesUploaded;
    
    /** Time when seeding started */
    LocalDateTime startTime;
    
    /** Duration of seeding session */
    Duration sessionDuration;
    
    /** Number of successful piece uploads */
    int successfulUploads;
    
    /** Number of failed upload attempts */
    int failedUploads;
    
    /** Share ratio (uploaded / downloaded) if known */
    Double shareRatio;
    
    /**
     * Creates initial stats for a new seeding session.
     */
    public static SeedingStats createInitial() {
        return SeedingStats.builder()
                .totalUploaded(0L)
                .totalDownloaded(0L)
                .activePeers(0)
                .interestedPeers(0)
                .uploadSpeed(0L)
                .downloadSpeed(0L)
                .averageUploadRate(0L)
                .piecesUploaded(0)
                .startTime(LocalDateTime.now())
                .sessionDuration(Duration.ZERO)
                .successfulUploads(0)
                .failedUploads(0)
                .shareRatio(null)
                .build();
    }
    
    /**
     * Creates updated stats with new values.
     */
    public SeedingStats withUpdatedValues(long additionalUploaded, int currentActivePeers, 
                                         int currentInterestedPeers, long currentUploadSpeed) {
        LocalDateTime now = LocalDateTime.now();
        Duration newSessionDuration = Duration.between(startTime, now);
        
        long newTotalUploaded = totalUploaded + additionalUploaded;
        long newAverageUploadRate = newSessionDuration.toSeconds() > 0 ? 
                newTotalUploaded / newSessionDuration.toSeconds() : 0L;

        return this.toBuilder()
                .totalUploaded(newTotalUploaded)
                .activePeers(currentActivePeers)
                .interestedPeers(currentInterestedPeers)
                .uploadSpeed(currentUploadSpeed)
                .averageUploadRate(newAverageUploadRate)
                .sessionDuration(newSessionDuration)
                .build();
    }


}