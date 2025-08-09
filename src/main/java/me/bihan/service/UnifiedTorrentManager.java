package me.bihan.service;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import me.bihan.service.download.DownloadAnnouncementManager;
import me.bihan.service.seeding.SeedingAnnouncementManager;
import me.bihan.service.seeding.UploadManager;
import me.bihan.service.strategy.FileManager;
import me.bihan.torrent.TorrentInfo;
import me.bihan.tracker.Peer;
import me.bihan.util.FormatUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Unified manager that handles both downloading and seeding using a single piece state.
 * Replaces the complex separate download/seeding architecture with a simple unified approach.
 */
@Log4j2
public class UnifiedTorrentManager {

    private final TorrentInfo torrentInfo;
    private final byte[] infoHash;
    private final List<Peer> peers;
    private final int maxConcurrentConnections;
    private final HashCalculatorService hashService;
    private final DownloadProgressObserver downloadObserver;
    private final SeedingProgressObserver seedingObserver;
    
    // Unified components
    private final PieceManager pieceManager;
    private final FileManager fileManager;
    private final ExecutorService downloadWorkerExecutor;

    /**
     * -- GETTER --
     *  Get the upload manager for external control.
     */
    // Seeding component (started when first piece becomes available)
    @Getter
    private UploadManager uploadManager;

    private SeedingAnnouncementManager seedingAnnouncementManager;
    private DownloadAnnouncementManager downloadAnnouncementManager;
    
    // Transfer speed monitoring component
    private TransferSpeedMonitor transferSpeedMonitor;
    
    private final AtomicLong totalDownloaded = new AtomicLong(0);
    private volatile boolean seedingStarted = false;

    public UnifiedTorrentManager(TorrentInfo torrentInfo, List<Peer> peers,
                                String downloadDirectory, int maxConcurrentConnections, int maxConcurrentUploads,
                                int seedingListenPort, HashCalculatorService hashService,
                                DownloadProgressObserver downloadObserver, SeedingProgressObserver seedingObserver,
                                List<String> trackerUrls, TrackerService trackerService, String peerId,
                                int announcementIntervalMinutes) throws IOException {
        this.torrentInfo = torrentInfo;
        this.infoHash = torrentInfo.getHash();
        this.peers = peers;
        this.maxConcurrentConnections = maxConcurrentConnections;
        this.hashService = hashService;
        this.downloadObserver = downloadObserver;
        this.seedingObserver = seedingObserver;

        // Initialize unified components
        this.pieceManager = new PieceManager(torrentInfo, downloadDirectory, hashService);
        this.fileManager = new FileManager(torrentInfo, downloadDirectory);
        this.downloadWorkerExecutor = Executors.newFixedThreadPool(maxConcurrentConnections);

        downloadAnnouncementManager = new DownloadAnnouncementManager(
                torrentInfo,
                trackerUrls,
                trackerService,
                peerId,
                seedingListenPort,
                announcementIntervalMinutes,
                pieceManager
        );

        seedingAnnouncementManager = new SeedingAnnouncementManager(
                torrentInfo,
                trackerUrls,
                trackerService,
                peerId,
                seedingListenPort,
                announcementIntervalMinutes
        );

        uploadManager = new UploadManager(
                torrentInfo,
                infoHash,
                downloadDirectory,
                seedingListenPort,
                maxConcurrentUploads,
                seedingObserver,
                new BitfieldManagerAdapter(pieceManager), // Adapter to make PieceManager work with UploadManager
                transferSpeedMonitor,
                downloadAnnouncementManager,
                seedingAnnouncementManager
        );

        // Create download directory if it doesn't exist
        Path downloadPath = Paths.get(downloadDirectory);
        if (!Files.exists(downloadPath)) {
            Files.createDirectories(downloadPath);
        }
    }

    /**
     * Start the unified download and seeding process.
     * Automatically handles file verification, resuming, and seeding.
     */
    public void start() throws Exception {
        log.info("Starting unified torrent manager for: {}", torrentInfo.getName());

        try {
            // Initialize file allocation
            fileManager.allocateFiles();

            // Initialize piece manager and check existing files
            boolean allPiecesAvailable = pieceManager.initializeAndVerifyExistingFiles(fileManager);
            
            // Create and start transfer speed monitor
            transferSpeedMonitor = new TransferSpeedMonitor(torrentInfo.getName(), 2);
            transferSpeedMonitor.start();
            
            if (allPiecesAvailable) {
                log.info("All pieces already available - ready to seed: {}", torrentInfo.getName());
                downloadObserver.onDownloadCompleted(torrentInfo.getName(), torrentInfo.getTotalLength());
            } else {
                log.info("Starting download for missing pieces: {}", torrentInfo.getName());
                downloadObserver.onDownloadStarted(torrentInfo.getName(), torrentInfo.getTotalLength());
                downloadAnnouncementManager.startAnnouncements();
            }
            
            // Start seeding for any pieces we already have
            startSeedingIfNeeded();

            // Start download workers if we need to download pieces
            List<Future<Void>> downloadFutures = null;
            if (!pieceManager.isDownloadComplete()) {
                downloadFutures = startDownloadWorkers();
                
                // Monitor download progress
                monitorProgress();
                
                // Wait for download completion
                waitForDownloadCompletion(downloadFutures);
                
                log.info("Download completed: {}", torrentInfo.getName());
                downloadObserver.onDownloadCompleted(torrentInfo.getName(), torrentInfo.getTotalLength());
                
                if (downloadAnnouncementManager.isRunning()) {
                    log.info("Download complete - transitioning from download announcements to seeding-only announcements");
                    downloadAnnouncementManager.stopAnnouncements();
                }
                
                // Announce completion to trackers
                if (seedingAnnouncementManager.isRunning()) {
                    seedingAnnouncementManager.announceCompleted();
                }
            }

            // Clean up download state file since we're complete
            pieceManager.cleanupStateFile();
            
            // Continue seeding indefinitely
            log.info("Download complete, continuing to seed: {}", torrentInfo.getName());
            
            // Keep the upload manager running for seeding
            // The application will handle shutdown via the upload manager

        } catch (Exception e) {
            log.error("Unified torrent manager failed: {}", e.getMessage(), e);
            shutdown();
            throw e;
        }
    }

    /**
     * Start seeding if we have any pieces available.
     */
    private void startSeedingIfNeeded() throws Exception {
        if (pieceManager.getCompletedPiecesCount() > 0 && !seedingStarted) {
            log.info("Starting seeding for {} available pieces", pieceManager.getCompletedPiecesCount());
            uploadManager.startSeeding();
            seedingObserver.onSeedingStarted(torrentInfo.getName(), torrentInfo.getTotalLength());
            seedingStarted = true;
        }
    }

    /**
     * Start download workers.
     */
    private List<Future<Void>> startDownloadWorkers() {
        List<Future<Void>> futures = new ArrayList<>();
        
        for (int i = 0; i < maxConcurrentConnections; i++) {
            UnifiedPeerWorker worker = new UnifiedPeerWorker(
                i,
                peers,
                infoHash,
                pieceManager,
                fileManager,
                hashService,
                this::onPieceCompleted,
                torrentInfo
            );
            
            futures.add(downloadWorkerExecutor.submit(worker));
        }
        
        log.info("Started {} download workers", maxConcurrentConnections);
        return futures;
    }
    
    /**
     * Called when a piece is completed - starts seeding if not already started.
     */
    private void onPieceCompleted(int pieceIndex, long bytesDownloaded) {
        try {
            // Update progress
            totalDownloaded.addAndGet(bytesDownloaded);

            // Update transfer speed monitor with download stats
            transferSpeedMonitor.addDownloadedBytes(bytesDownloaded);

            // Update download announcement manager with download stats
            if (downloadAnnouncementManager.isRunning()) {
                downloadAnnouncementManager.updateDownloadStats(bytesDownloaded);
            }

            // Update seeding announcement manager with download stats if seeding has started
            if (uploadManager.getSeedingAnnouncementManager().isRunning()) {
                uploadManager.getSeedingAnnouncementManager().updateDownloadStats(bytesDownloaded);
            }
            
            // Start seeding if not already started
            if (!seedingStarted) {
                startSeedingIfNeeded();
            }
            
            // Notify observers
            downloadObserver.onPieceCompleted(pieceIndex, 0, bytesDownloaded);
            
            double progress = (double) pieceManager.getCompletedPiecesCount() / torrentInfo.getNumPieces() * 100.0;
            downloadObserver.onProgressUpdate(progress, transferSpeedMonitor.getCurrentDownloadSpeed());
            
            log.info("Piece {} completed and available for seeding ({}/{} pieces, {}%)",
                    pieceIndex, pieceManager.getCompletedPiecesCount(), torrentInfo.getNumPieces(), FormatUtils.formatPercentage(progress));
            
        } catch (Exception e) {
            log.error("Error handling completed piece {}: {}", pieceIndex, e.getMessage(), e);
        }
    }

    /**
     * Monitor download progress.
     */
    private void monitorProgress() {
        // Progress is monitored via piece completion callbacks
        // Could add periodic progress reporting here if needed
    }

    /**
     * Wait for download completion.
     */
    private void waitForDownloadCompletion(List<Future<Void>> downloadFutures) throws Exception {
        if (downloadFutures == null) return;
        
        for (Future<Void> future : downloadFutures) {
            try {
                future.get();
            } catch (ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof Exception) {
                    throw (Exception) cause;
                } else {
                    throw new RuntimeException("Download worker failed", cause);
                }
            }
        }
    }

    /**
     * Shutdown the manager.
     */
    public void shutdown() {
        log.info("Shutting down unified torrent manager");
        
        // Stop download announcements
        if (downloadAnnouncementManager.isRunning()) {
            downloadAnnouncementManager.stopAnnouncements();
        }
        
        // Stop transfer speed monitor
        transferSpeedMonitor.stop();
        
        // Shutdown download workers
        downloadWorkerExecutor.shutdown();
        try {
            if (!downloadWorkerExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                downloadWorkerExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            downloadWorkerExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // Shutdown piece manager
        pieceManager.shutdown();
        
        // Keep upload manager running - it will be shut down by the service
        log.info("Unified torrent manager shutdown complete");
    }


    /**
     * Adapter to make PieceManager compatible with existing UploadManager.
     * This bridges the old BitfieldManager interface with the new unified PieceManager.
     */
    private static class BitfieldManagerAdapter extends BitfieldManager {
        private final PieceManager pieceManager;
        
        public BitfieldManagerAdapter(PieceManager pieceManager) {
            super(pieceManager.torrentInfo);
            this.pieceManager = pieceManager;
        }
        
        @Override
        public boolean hasPiece(int pieceIndex) {
            return pieceManager.hasPiece(pieceIndex);
        }
        
        @Override
        public int getAvailablePiecesCount() {
            return pieceManager.getCompletedPiecesCount();
        }

    }
}