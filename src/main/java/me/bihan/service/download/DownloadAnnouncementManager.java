package me.bihan.service.download;

import lombok.extern.log4j.Log4j2;
import me.bihan.service.PieceManager;
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
 * Manages tracker announcements during download phase.
 * Handles periodic announcements to trackers so other peers can discover this client
 * even while it's still downloading. Updates the 'left' field accurately.
 * Follows Single Responsibility Principle - only handles tracker announcements for downloading.
 */
@Log4j2
public class DownloadAnnouncementManager {

    private final TorrentInfo torrentInfo;
    private final List<String> trackerUrls;
    private final TrackerService trackerService;
    private final String peerId;
    private final int listenPort;
    private final int announcementIntervalMinutes;
    private final PieceManager pieceManager;
    
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicLong totalUploaded = new AtomicLong(0);
    private final AtomicLong totalDownloaded = new AtomicLong(0);
    
    private ScheduledExecutorService announceExecutor;

    public DownloadAnnouncementManager(TorrentInfo torrentInfo, List<String> trackerUrls, 
                                     TrackerService trackerService, String peerId, int listenPort, 
                                     int announcementIntervalMinutes, PieceManager pieceManager) {
        this.torrentInfo = torrentInfo;
        this.trackerUrls = trackerUrls;
        this.trackerService = trackerService;
        this.peerId = peerId;
        this.listenPort = listenPort;
        this.announcementIntervalMinutes = announcementIntervalMinutes;
        this.pieceManager = pieceManager;
    }

    /**
     * Start announcing to trackers that we're downloading.
     */
    public void startAnnouncements() {
        if (isRunning.get()) {
            log.warn("Download announcement manager already running for: {}", torrentInfo.getName());
            return;
        }

        log.info("Starting download announcements for: {} (interval: {} minutes)", 
                torrentInfo.getName(), announcementIntervalMinutes);

        isRunning.set(true);
        announceExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "DownloadAnnouncementManager-" + torrentInfo.getName());
            t.setDaemon(true);
            return t;
        });

        // Send initial "started" event
        announceToTrackers("started");

        // Schedule periodic announcements
        announceExecutor.scheduleAtFixedRate(
            () -> announceToTrackers(""),
            announcementIntervalMinutes,
            announcementIntervalMinutes,
            TimeUnit.MINUTES
        );

        log.info("Download announcements started for: {}", torrentInfo.getName());
    }

    /**
     * Stop announcing to trackers (typically when transitioning to seeding).
     */
    public void stopAnnouncements() {
        if (!isRunning.get()) {
            return;
        }

        log.info("Stopping download announcements for: {}", torrentInfo.getName());
        isRunning.set(false);

        // Shutdown scheduler
        if (announceExecutor != null) {
            announceExecutor.shutdown();
            try {
                if (!announceExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    announceExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                announceExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        log.info("Download announcements stopped for: {}", torrentInfo.getName());
    }

    /**
     * Update upload statistics.
     */
    public void updateUploadStats(long bytesUploaded) {
        totalUploaded.addAndGet(bytesUploaded);
    }

    /**
     * Update download statistics.
     */
    public void updateDownloadStats(long bytesDownloaded) {
        totalDownloaded.addAndGet(bytesDownloaded);
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
                    log.debug("Successfully announced to tracker: {} (event: {}, left: {} bytes)", 
                            trackerUrl, event.isEmpty() ? "periodic" : event, 
                            calculateBytesLeft());
                    
                    // Log peer count if available (useful for monitoring)
                    if (response.getPeers() != null) {
                        log.debug("Tracker {} reports {} peers for torrent {}", 
                                trackerUrl, response.getPeers().size(), torrentInfo.getName());
                    }
                } else {
                    log.warn("Tracker {} returned failure for download announcement: {}", 
                            trackerUrl, response.getFailureReason());
                }

            } catch (Exception e) {
                log.warn("Failed to announce to tracker {} for downloading: {}", 
                        trackerUrl, e.getMessage());
            }
        }
    }

    /**
     * Create tracker request for download announcements.
     */
    private TrackerRequest createTrackerRequest(String event) {
        long bytesLeft = calculateBytesLeft();
        
        return TrackerRequest.builder()
                .torrentInfo(torrentInfo)
                .peerId(peerId)
                .port(listenPort)
                .uploaded(totalUploaded.get())
                .downloaded(totalDownloaded.get())
                .left(bytesLeft)
                .compact(true)
                .event(event)
                .numwant(200) // Request more peers since we're downloading
                .build();
    }

    /**
     * Calculate bytes left to download based on completed pieces.
     * Improved calculation that handles the last piece correctly.
     */
    private long calculateBytesLeft() {
        if (pieceManager.isDownloadComplete()) {
            return 0L;
        }
        
        // Calculate based on completed pieces
        int completedPieces = pieceManager.getCompletedPiecesCount();
        int totalPieces = torrentInfo.getNumPieces();
        long pieceLength = torrentInfo.getPieceLength();
        
        if (completedPieces == 0) {
            return torrentInfo.getTotalLength();
        }
        
        // Handle the last piece which may be smaller than standard piece length
        long completedBytes;
        if (completedPieces == totalPieces) {
            // All pieces completed
            completedBytes = torrentInfo.getTotalLength();
        } else {
            // Some pieces completed - calculate accurately
            completedBytes = (long) completedPieces * pieceLength;
            
            // If we have the last piece, adjust for its potentially smaller size
            if (completedPieces == totalPieces - 1) {
                long lastPieceSize = torrentInfo.getTotalLength() % pieceLength;
                if (lastPieceSize != 0) {
                    completedBytes = completedBytes - pieceLength + lastPieceSize;
                }
            }
        }
        
        long bytesLeft = torrentInfo.getTotalLength() - completedBytes;
        return Math.max(0L, bytesLeft);
    }

    /**
     * Check if currently announcing to trackers.
     */
    public boolean isRunning() {
        return isRunning.get();
    }
}