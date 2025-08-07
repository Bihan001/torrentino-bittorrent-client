package me.bihan.service.seeding;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import me.bihan.service.TrackerService;
import me.bihan.torrent.TorrentInfo;
import me.bihan.tracker.TrackerRequest;
import me.bihan.tracker.TrackerResponse;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages tracker announcements during seeding phase.
 * Handles periodic announcements to trackers so other peers can discover this seeder.
 * Follows Single Responsibility Principle - only handles tracker announcements for seeding.
 */
@Log4j2
public class SeedingAnnouncementManager {

    private final TorrentInfo torrentInfo;
    private final List<String> trackerUrls;
    private final TrackerService trackerService;
    private final String peerId;
    private final int listenPort;
    private final int announcementIntervalMinutes;
    
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicLong totalUploaded = new AtomicLong(0);
    private final AtomicLong totalDownloaded = new AtomicLong(0);
    
    private ScheduledExecutorService announceExecutor;

    public SeedingAnnouncementManager(TorrentInfo torrentInfo, List<String> trackerUrls, 
                                    TrackerService trackerService, String peerId, int listenPort, 
                                    int announcementIntervalMinutes) {
        this.torrentInfo = torrentInfo;
        this.trackerUrls = trackerUrls;
        this.trackerService = trackerService;
        this.peerId = peerId;
        this.listenPort = listenPort;
        this.announcementIntervalMinutes = announcementIntervalMinutes;
    }

    /**
     * Start announcing to trackers that we're seeding.
     */
    public void startAnnouncements() {
        if (isRunning.get()) {
            log.warn(" alrSeeding announcement manageready running for: {}", torrentInfo.getName());
            return;
        }

        log.info("Starting seeding announcements for: {} (interval: {} minutes)", 
                torrentInfo.getName(), announcementIntervalMinutes);

        isRunning.set(true);
        announceExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SeedingAnnouncementManager-" + torrentInfo.getName());
            t.setDaemon(true);
            return t;
        });

        // Send initial "started" event (we may not be complete yet, just starting to seed some pieces)
        announceToTrackers("started");

        // Schedule periodic announcements
        announceExecutor.scheduleAtFixedRate(
            () -> announceToTrackers(""),
            announcementIntervalMinutes,
            announcementIntervalMinutes,
            TimeUnit.MINUTES
        );

        log.info("Seeding announcements started for: {}", torrentInfo.getName());
    }

    /**
     * Stop announcing to trackers.
     */
    public void stopAnnouncements() {
        if (!isRunning.get()) {
            log.warn("Seeding announcement manager not running for: {}", torrentInfo.getName());
            return;
        }

        log.info("Stopping seeding announcements for: {}", torrentInfo.getName());
        isRunning.set(false);

        // Send "stopped" event to trackers
        announceToTrackers("stopped");

        // Shutdown scheduler
        if (announceExecutor != null) {
            announceExecutor.shutdown();
            try {
                if (!announceExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    announceExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                announceExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        log.info("Seeding announcements stopped for: {}", torrentInfo.getName());
    }

    /**
     * Update upload statistics.
     */
    public void updateUploadStats(long bytesUploaded) {
        totalUploaded.addAndGet(bytesUploaded);
    }

    /**
     * Update download statistics (for cases where we're still downloading while seeding).
     */
    public void updateDownloadStats(long bytesDownloaded) {
        totalDownloaded.addAndGet(bytesDownloaded);
    }

    /**
     * Announce completion to trackers (call when download is fully completed).
     */
    public void announceCompleted() {
        if (isRunning.get()) {
            log.info("Announcing completion to trackers for: {}", torrentInfo.getName());
            announceToTrackers("completed");
        }
    }

    /**
     * Announce to all configured trackers.
     */
    private void announceToTrackers(String event) {
        if (!isRunning.get() && !"stopped".equals(event)) {
            return;
        }

        log.debug("Announcing to {} tracker(s) for {} (event: {})", 
                trackerUrls.size(), torrentInfo.getName(), 
                event.isEmpty() ? "periodic" : event);

        for (String trackerUrl : trackerUrls) {
            try {
                TrackerRequest request = createTrackerRequest(event);
                
                TrackerResponse response;
                if ("completed".equals(event)) {
                    response = trackerService.announceCompleted(trackerUrl, request);
                } else if ("stopped".equals(event)) {
                    response = trackerService.announceStopped(trackerUrl, request);
                } else if ("started".equals(event)) {
                    response = trackerService.announceStarted(trackerUrl, request);
                } else {
                response = trackerService.announce(trackerUrl, request);
                }

                if (response.getFailureReason() == null) {
                    log.debug("Successfully announced to tracker: {} (event: {})",
                            trackerUrl, event.isEmpty() ? "periodic" : event);
                    
                    // Log peer count if available (useful for monitoring)
                    if (response.getPeers() != null) {
                        log.debug("Tracker {} reports {} peers for torrent {}",
                                trackerUrl, response.getPeers().size(), torrentInfo.getName());
                    }
                } else {
                    log.warn("Tracker {} returned failure for seeding announcement: {}", 
                            trackerUrl, response.getFailureReason());
                }

            } catch (Exception e) {
                log.warn("Failed to announce to tracker {} for seeding: {}", 
                        trackerUrl, e.getMessage());
            }
        }
    }

    /**
     * Create tracker request for seeding announcements.
     */
    private TrackerRequest createTrackerRequest(String event) {
        // For seeding, we typically have left=0 (complete file)
        // But we'll calculate based on actual completion status
        long bytesLeft = 0; // Assume complete for seeding
        
        return TrackerRequest.builder()
                .torrentInfo(torrentInfo)
                .peerId(peerId)
                .port(listenPort)
                .uploaded(totalUploaded.get())
                .downloaded(totalDownloaded.get())
                .left(bytesLeft)
                .compact(true)
                .event(event)
                .numwant(50) // Request fewer peers since we're primarily seeding
                .build();
    }

    /**
     * Check if currently announcing to trackers.
     */
    public boolean isRunning() {
        return isRunning.get();
    }
}