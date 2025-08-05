package me.bihan.service;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Monitors transfer speeds for both upload and download.
 * Calculates real-time speed measurements in bytes per second.
 * Follows Single Responsibility Principle - only handles speed calculations.
 */
@Log4j2
public class TransferSpeedMonitor {

    private final String torrentName;
    private final int measurementIntervalSeconds;
    
    private final AtomicLong totalUploaded = new AtomicLong(0);
    private final AtomicLong totalDownloaded = new AtomicLong(0);
    
    // Speed calculation fields
    private final AtomicLong lastUploadMeasurement = new AtomicLong(0);
    private final AtomicLong lastDownloadMeasurement = new AtomicLong(0);
    private volatile long lastMeasurementTime = System.currentTimeMillis();
    /**
     * -- GETTER --
     *  Get current upload speed in bytes per second.
     */
    @Getter
    private volatile long currentUploadSpeed = 0; // bytes/second
    /**
     * -- GETTER --
     *  Get current download speed in bytes per second.
     */
    @Getter
    private volatile long currentDownloadSpeed = 0; // bytes/second
    
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private ScheduledExecutorService speedCalculatorExecutor;

    public TransferSpeedMonitor(String torrentName, int measurementIntervalSeconds) {
        this.torrentName = torrentName;
        this.measurementIntervalSeconds = measurementIntervalSeconds;
    }

    /**
     * Start monitoring transfer speeds.
     */
    public void start() {
        if (isRunning.get()) {
            log.warn("Transfer speed monitor already running for: {}", torrentName);
            return;
        }

        log.debug("Starting transfer speed monitor for: {} (interval: {} seconds)", 
                torrentName, measurementIntervalSeconds);

        isRunning.set(true);
        speedCalculatorExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "TransferSpeedMonitor-" + torrentName);
            t.setDaemon(true);
            return t;
        });

        // Initialize measurement time
        lastMeasurementTime = System.currentTimeMillis();
        lastUploadMeasurement.set(totalUploaded.get());
        lastDownloadMeasurement.set(totalDownloaded.get());

        // Schedule speed calculations
        speedCalculatorExecutor.scheduleAtFixedRate(
            this::calculateSpeeds,
            measurementIntervalSeconds,
            measurementIntervalSeconds,
            TimeUnit.SECONDS
        );

        log.debug("Transfer speed monitor started for: {}", torrentName);
    }

    /**
     * Stop monitoring transfer speeds.
     */
    public void stop() {
        if (!isRunning.get()) {
            return;
        }

        log.debug("Stopping transfer speed monitor for: {}", torrentName);
        isRunning.set(false);

        if (speedCalculatorExecutor != null) {
            speedCalculatorExecutor.shutdown();
            try {
                if (!speedCalculatorExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    speedCalculatorExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                speedCalculatorExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        log.debug("Transfer speed monitor stopped for: {}", torrentName);
    }

    /**
     * Update upload statistics.
     */
    public void addUploadedBytes(long bytesUploaded) {
        totalUploaded.addAndGet(bytesUploaded);
    }

    /**
     * Update download statistics.
     */
    public void addDownloadedBytes(long bytesDownloaded) {
        totalDownloaded.addAndGet(bytesDownloaded);
    }

    /**
     * Calculate current upload and download speeds.
     */
    private void calculateSpeeds() {
        long currentTime = System.currentTimeMillis();
        long currentUpload = totalUploaded.get();
        long currentDownload = totalDownloaded.get();
        
        long timeDelta = currentTime - lastMeasurementTime;
        if (timeDelta > 0) {
            long uploadDelta = currentUpload - lastUploadMeasurement.get();
            long downloadDelta = currentDownload - lastDownloadMeasurement.get();
            
            // Calculate bytes per second
            currentUploadSpeed = (uploadDelta * 1000) / timeDelta;
            currentDownloadSpeed = (downloadDelta * 1000) / timeDelta;
            
            lastUploadMeasurement.set(currentUpload);
            lastDownloadMeasurement.set(currentDownload);
            lastMeasurementTime = currentTime;
            
            if (log.isTraceEnabled() && (currentUploadSpeed > 0 || currentDownloadSpeed > 0)) {
                log.trace("Speed update for {}: Upload: {} B/s, Download: {} B/s", 
                         torrentName, currentUploadSpeed, currentDownloadSpeed);
            }
        }
    }

    /**
     * Get total bytes uploaded.
     */
    public long getTotalUploaded() {
        return totalUploaded.get();
    }

    /**
     * Get total bytes downloaded.
     */
    public long getTotalDownloaded() {
        return totalDownloaded.get();
    }

    /**
     * Check if currently monitoring.
     */
    public boolean isRunning() {
        return isRunning.get();
    }

    /**
     * Reset all statistics (useful for testing or restart scenarios).
     */
    public void reset() {
        totalUploaded.set(0);
        totalDownloaded.set(0);
        lastUploadMeasurement.set(0);
        lastDownloadMeasurement.set(0);
        currentUploadSpeed = 0;
        currentDownloadSpeed = 0;
        lastMeasurementTime = System.currentTimeMillis();
    }
}