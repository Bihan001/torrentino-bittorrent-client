package me.bihan.service.seeding;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import me.bihan.service.BitfieldManager;
import me.bihan.service.HashCalculatorService;
import me.bihan.service.SeedingProgressObserver;
import me.bihan.service.SeedingStats;
import me.bihan.service.TrackerService;
import me.bihan.service.TransferSpeedMonitor;
import me.bihan.service.download.DownloadAnnouncementManager;
import me.bihan.torrent.TorrentInfo;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages incoming peer connections and coordinates upload operations.
 * Implements the Manager pattern to coordinate multiple SeedingWorkers.
 * Follows Single Responsibility Principle - only manages upload coordination.
 */
@Log4j2
public class UploadManager {
    
    private final TorrentInfo torrentInfo;
    private final byte[] infoHash;
    private final String downloadDirectory;
    private final int listenPort;
    private final int maxConcurrentUploads;
    private final SeedingProgressObserver observer;
    private final BitfieldManager bitfieldManager;
    
    // Transfer speed monitor for real speed calculations
    private final TransferSpeedMonitor transferSpeedMonitor;
    
    private final ExecutorService workerExecutor;
    private final ExecutorService connectionAcceptor;
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicLong totalUploaded = new AtomicLong(0);
    private final ConcurrentHashMap<String, SeedingWorker> activeWorkers = new ConcurrentHashMap<>();
    
    private ServerSocket serverSocket;
    private SeedingStats currentStats;

    @Getter
    private final DownloadAnnouncementManager downloadAnnouncementManager;
    @Getter
    private final SeedingAnnouncementManager seedingAnnouncementManager;


    public UploadManager(TorrentInfo torrentInfo, byte[] infoHash, String downloadDirectory,
                         int listenPort, int maxConcurrentUploads,
                         SeedingProgressObserver observer, BitfieldManager bitfieldManager, TransferSpeedMonitor transferSpeedMonitor, DownloadAnnouncementManager downloadAnnouncementManager, SeedingAnnouncementManager seedingAnnouncementManager) {
        this.torrentInfo = torrentInfo;
        this.infoHash = infoHash;
        this.downloadDirectory = downloadDirectory;
        this.listenPort = listenPort;
        this.maxConcurrentUploads = maxConcurrentUploads;
        this.observer = observer;
        this.bitfieldManager = bitfieldManager;
        this.transferSpeedMonitor = transferSpeedMonitor;
        
        this.workerExecutor = Executors.newFixedThreadPool(maxConcurrentUploads);
        this.connectionAcceptor = Executors.newSingleThreadExecutor();
        this.currentStats = SeedingStats.createInitial();
        
        // Create announcement manager
        this.downloadAnnouncementManager = downloadAnnouncementManager;
        this.seedingAnnouncementManager = seedingAnnouncementManager;
    }
    
    /**
     * Start accepting incoming peer connections.
     */
    public void startSeeding() throws IOException {
        if (isRunning.get()) {
            log.warn("Upload manager is already running for torrent: {}", torrentInfo.getName());
            return;
        }
        
        log.info("Starting seeding for torrent: {} on port {}", torrentInfo.getName(), listenPort);
        
        // Create server socket
        serverSocket = new ServerSocket(listenPort);
        isRunning.set(true);
        
        // Start tracker announcements
        seedingAnnouncementManager.startAnnouncements();
        
        // Notify observer
        observer.onSeedingStarted(torrentInfo.getName(), torrentInfo.getTotalLength());
        
        // Start accepting connections
        connectionAcceptor.submit(this::acceptConnections);
        
        // Start statistics monitoring
        startStatsMonitoring();
        
        log.info("Seeding started successfully for: {}", torrentInfo.getName());
    }
    
    /**
     * Stop seeding and close all connections.
     */
    public void stopSeeding() {
        if (!isRunning.get()) {
            log.warn("Upload manager is not running for torrent: {}", torrentInfo.getName());
            return;
        }
        
        log.info("Stopping seeding for torrent: {}", torrentInfo.getName());
        isRunning.set(false);
        
        // Stop tracker announcements
        seedingAnnouncementManager.stopAnnouncements();
        
        // Close server socket
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                log.warn("Error closing server socket: {}", e.getMessage());
            }
        }
        
        // Shutdown workers
        shutdownWorkers();
        
        // Notify observer
        observer.onSeedingStopped(torrentInfo.getName(), totalUploaded.get());
        
        log.info("Seeding stopped for: {}", torrentInfo.getName());
    }
    
    /**
     * Accept incoming peer connections.
     */
    private void acceptConnections() {
        log.info("Started accepting connections on port {}", listenPort);
        
        while (isRunning.get() && !Thread.currentThread().isInterrupted()) {
            try {
                Socket peerSocket = serverSocket.accept();
                
                // Check if we have room for more connections
                if (activeWorkers.size() >= maxConcurrentUploads) {
                    log.warn("Maximum concurrent uploads reached, rejecting connection from: {}", 
                             peerSocket.getRemoteSocketAddress());
                    peerSocket.close();
                    continue;
                }
                
                String peerAddress = peerSocket.getRemoteSocketAddress().toString();
                log.info("Accepted connection from: {}", peerAddress);
                
                // Create and start seeding worker
                SeedingWorker worker = new SeedingWorker(
                    peerSocket,
                    infoHash,
                    torrentInfo,
                    downloadDirectory,
                    bitfieldManager,
                    this::onPieceUploaded,
                    this::onWorkerFinished
                );
                
                activeWorkers.put(peerAddress, worker);
                workerExecutor.submit(worker);
                
                // Notify observer
                observer.onPeerConnected(peerAddress);
                
            } catch (IOException e) {
                if (isRunning.get()) {
                    log.error("Error accepting connection: {}", e.getMessage());
                }
                break;
            }
        }
        
        log.info("Stopped accepting connections");
    }
    
    /**
     * Called when a worker uploads a piece.
     */
    private void onPieceUploaded(int pieceIndex, long bytesUploaded, String peerAddress) {
        totalUploaded.addAndGet(bytesUploaded);
        observer.onPieceUploaded(pieceIndex, bytesUploaded, peerAddress);
        
        // Update transfer speed monitor
        transferSpeedMonitor.addUploadedBytes(bytesUploaded);

        // Update tracker statistics
        downloadAnnouncementManager.updateUploadStats(bytesUploaded);
        seedingAnnouncementManager.updateUploadStats(bytesUploaded);
        
        // Update stats
        updateStats();
    }
    
    /**
     * Called when a worker finishes (connection closed).
     */
    private void onWorkerFinished(String peerAddress) {
        activeWorkers.remove(peerAddress);
        observer.onPeerDisconnected(peerAddress);
        
        log.debug("Worker finished for peer: {} ({} active workers remaining)", 
                 peerAddress, activeWorkers.size());
    }
    
    /**
     * Start monitoring and reporting statistics.
     */
    private void startStatsMonitoring() {
        ScheduledExecutorService statsExecutor = Executors.newSingleThreadScheduledExecutor();
        
        statsExecutor.scheduleAtFixedRate(() -> {
            if (isRunning.get()) {
                updateStats();
                observer.onSeedingProgress(torrentInfo.getName(), currentStats);
            } else {
                statsExecutor.shutdown();
            }
        }, 5, 5, TimeUnit.SECONDS);
    }
    
    /**
     * Update current statistics.
     */
    private synchronized void updateStats() {
        long currentUploadRate = transferSpeedMonitor.getCurrentUploadSpeed();
        
        int interestedPeers = activeWorkers.size();
        
        currentStats = currentStats.withUpdatedValues(
            0L, // We track total separately
            activeWorkers.size(),
            interestedPeers,
            currentUploadRate
        );
    }

    /**
     * Shutdown all workers.
     */
    private void shutdownWorkers() {
        log.info("Shutting down {} active workers", activeWorkers.size());
        
        // Stop accepting new connections
        connectionAcceptor.shutdown();
        
        // Interrupt all workers
        activeWorkers.values().forEach(SeedingWorker::interrupt);
        
        // Shutdown worker executor
        workerExecutor.shutdown();
        
        try {
            if (!workerExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                workerExecutor.shutdownNow();
                if (!workerExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("Worker executor did not terminate cleanly");
                }
            }
        } catch (InterruptedException e) {
            workerExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        activeWorkers.clear();
    }
    
    /**
     * Get current seeding statistics with real data.
     */
    public SeedingStats getCurrentStats() {
        long uploadSpeed = 0;
        long downloadSpeed = 0;
        long actualTotalUploaded = totalUploaded.get();
        long totalDownloaded = 0;
        
        if (transferSpeedMonitor != null) {
            uploadSpeed = transferSpeedMonitor.getCurrentUploadSpeed();
            downloadSpeed = transferSpeedMonitor.getCurrentDownloadSpeed();
            actualTotalUploaded = transferSpeedMonitor.getTotalUploaded();
            totalDownloaded = transferSpeedMonitor.getTotalDownloaded();
        }
        
        return currentStats.toBuilder()
                .totalUploaded(actualTotalUploaded)
                .totalDownloaded(totalDownloaded)
                .activePeers(activeWorkers.size())
                .interestedPeers(activeWorkers.size()) // Simplified - assume all active peers are interested
                .uploadSpeed(uploadSpeed)
                .downloadSpeed(downloadSpeed)
                .build();
    }

}