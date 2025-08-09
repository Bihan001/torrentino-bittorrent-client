package me.bihan.service;

import lombok.extern.log4j.Log4j2;
import me.bihan.peer.PeerIdGenerator;
import me.bihan.torrent.Torrent;
import me.bihan.torrent.TorrentInfo;
import me.bihan.tracker.Peer;
import me.bihan.tracker.TrackerRequest;
import lombok.RequiredArgsConstructor;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * Simplified BitTorrent service with unified download and seeding.
 * Single entry point that intelligently handles file verification, resuming, downloading, and seeding.
 * Eliminates the need for separate download/seed commands and complex state management.
 */
@Log4j2
@RequiredArgsConstructor
public class SimpleBittorrentService {
    
    private final TorrentParserService parserService;
    private final TrackerService trackerService;
    private final HashCalculatorService hashService;
    private final String downloadDirectory;
    private final int maxConcurrentConnections;
    private final int maxConcurrentUploads;
    private final int baseListenPort;
    private final int announcementIntervalMinutes;
    
    // Track active torrents
    private final ConcurrentHashMap<String, UnifiedTorrentManager> activeTorrents = new ConcurrentHashMap<>();
    
    // Executor service for parallel tracker requests
    private final ExecutorService trackerExecutor = Executors.newCachedThreadPool();
    
    /**
     * Process a torrent file - the single unified operation.
     * Automatically:
     * 1. Checks if files already exist and verifies hashes
     * 2. Seeds any valid pieces immediately
     * 3. Downloads any missing pieces
     * 4. Continues seeding indefinitely
     * 
     * No need for separate download/seed commands - this does everything intelligently.
     */
    public void processTorrent(String torrentFilePath, DownloadProgressObserver downloadObserver, 
                              SeedingProgressObserver seedingObserver) throws Exception {
        log.info("Processing torrent: {}", torrentFilePath);
        
        // Parse torrent file
        Path path = Paths.get(torrentFilePath);
        Torrent torrent = parserService.parseTorrentFile(path);
        
        if (!torrent.isValid()) {
            throw new IllegalArgumentException("Invalid torrent file: " + torrentFilePath);
        }
        
        TorrentInfo torrentInfo = torrent.getInfo();
        String torrentKey = torrentInfo.getHashHex();
        
        // Check if already processing this torrent
        if (activeTorrents.containsKey(torrentKey)) {
            log.warn("Torrent already being processed: {}", torrentInfo.getName());
            return;
        }
        
        try {
            // Generate peer ID
            String peerId = PeerIdGenerator.generatePeerId();
            log.info("Generated peer ID: {} for torrent: {}", peerId, torrentInfo.getName());
            
            // Get tracker URLs and contact trackers
            List<String> allTrackers = torrent.getAllTrackers();

            // TODO: To be removed once http and https tracker parsing is supported
//            allTrackers = allTrackers.stream().filter(tracker -> !tracker.startsWith("http")).toList();

            log.info("Found {} tracker(s) for {}: {}", allTrackers.size(), torrentInfo.getName(), allTrackers);
            
            if (allTrackers.isEmpty()) {
                throw new RuntimeException("No trackers found in torrent file");
            }
            
            // Create tracker request
            TrackerRequest request = TrackerRequest.builder()
                    .torrentInfo(torrentInfo)
                    .peerId(peerId)
                    .left(torrentInfo.getTotalLength()) // Will be updated based on existing pieces
                    .build();
            
            // Contact trackers to get peers
            List<Peer> allPeers = contactAllTrackers(allTrackers, request);
            
            log.info("Found {} peers for torrent: {}", allPeers.size(), torrentInfo.getName());
            
            if (allPeers.isEmpty()) {
                log.warn("No peers available for torrent: {}", torrentInfo.getName());
                // Continue anyway - we might be able to seed existing files
            }
            
            // Find available port for this torrent
            int listenPort = findAvailablePort();
            
            // Create unified manager
            UnifiedTorrentManager manager = new UnifiedTorrentManager(
                torrentInfo,
                allPeers,
                downloadDirectory,
                maxConcurrentConnections,
                maxConcurrentUploads,
                listenPort,
                hashService,
                downloadObserver,
                seedingObserver,
                allTrackers,
                trackerService,
                peerId,
                announcementIntervalMinutes
            );
            
            // Track the torrent
            activeTorrents.put(torrentKey, manager);
            
            // Start unified processing (download missing pieces, seed available pieces)
            manager.start();
            
            log.info("Successfully started processing torrent: {}", torrentInfo.getName());
            
        } catch (Exception e) {
            activeTorrents.remove(torrentKey);
            log.error("Failed to process torrent {}: {}", torrentInfo.getName(), e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Check if a torrent is currently being processed.
     */
    public boolean isProcessing(String torrentFilePath) {
        try {
            Path path = Paths.get(torrentFilePath);
            Torrent torrent = parserService.parseTorrentFile(path);
            String torrentKey = torrent.getInfo().getHashHex();
            return activeTorrents.containsKey(torrentKey);
        } catch (Exception e) {
            log.warn("Error checking torrent status: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Get seeding statistics for a torrent.
     */
    public SeedingStats getSeedingStats(String torrentFilePath) {
        try {
            Path path = Paths.get(torrentFilePath);
            Torrent torrent = parserService.parseTorrentFile(path);
            String torrentKey = torrent.getInfo().getHashHex();
            
            UnifiedTorrentManager manager = activeTorrents.get(torrentKey);
            if (manager != null && manager.getUploadManager() != null) {
                // Return actual stats from upload manager
                return manager.getUploadManager().getCurrentStats();
            }
            
            return null;
        } catch (Exception e) {
            log.warn("Error getting seeding stats: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Stop processing a specific torrent.
     */
    public void stopTorrent(String torrentFilePath) {
        try {
            Path path = Paths.get(torrentFilePath);
            Torrent torrent = parserService.parseTorrentFile(path);
            String torrentKey = torrent.getInfo().getHashHex();
            
            UnifiedTorrentManager manager = activeTorrents.remove(torrentKey);
            if (manager != null) {
                manager.shutdown();
                log.info("Stopped processing torrent: {}", torrent.getInfo().getName());
            }
        } catch (Exception e) {
            log.warn("Error stopping torrent: {}", e.getMessage());
        }
    }
    
    /**
     * Stop all active torrents.
     */
    public void stopAll() {
        log.info("Stopping all active torrent processing");
        
        activeTorrents.values().forEach(manager -> {
            try {
                manager.shutdown();
            } catch (Exception e) {
                log.warn("Error stopping torrent manager: {}", e.getMessage());
            }
        });
        
        activeTorrents.clear();
        
        // Shutdown the tracker executor
        trackerExecutor.shutdown();
        
        log.info("All torrent processing stopped");
    }
    
    /**
     * Get list of active torrents.
     */
    public int getActiveTorrentCount() {
        return activeTorrents.size();
    }
    
    /**
     * Contact all trackers in parallel and collect peers.
     */
    private List<Peer> contactAllTrackers(List<String> trackerUrls, TrackerRequest request) {
        List<Peer> allPeers = new ArrayList<>();
        
        // Create CompletableFuture for each tracker request
        List<CompletableFuture<List<Peer>>> futures = trackerUrls.stream()
            .map(trackerUrl -> CompletableFuture
                .supplyAsync(() -> {
                    try {
                        var response = trackerService.announce(trackerUrl, request);
                        if (response.getFailureReason() == null && response.getPeers() != null) {
                            log.debug("Got {} peers from tracker: {}", response.getPeers().size(), trackerUrl);
                            return response.getPeers();
                        } else {
                            log.warn("Tracker {} returned failure: {}", trackerUrl, response.getFailureReason());
                            return new ArrayList<Peer>();
                        }
                    } catch (Exception e) {
                        log.warn("Failed to contact tracker {}: {}", trackerUrl, e.getMessage());
                        return new ArrayList<Peer>();
                    }
                }, trackerExecutor))
            .collect(Collectors.toList());
        
        // Wait for all futures to complete and collect results
        CompletableFuture<Void> allOfFutures = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );
        
        try {
            // Wait for all tracker requests to complete
            allOfFutures.get();
            
            // Collect all peers from completed futures
            for (CompletableFuture<List<Peer>> future : futures) {
                allPeers.addAll(future.get());
            }
        } catch (Exception e) {
            log.warn("Error waiting for tracker responses: {}", e.getMessage());
        }
        
        // Remove duplicates
        return allPeers.stream().distinct().collect(Collectors.toList());
    }
    
    /**
     * Find an available port for seeding.
     */
    private int findAvailablePort() {
        // Simple implementation - just increment from base port
        // Could be enhanced to actually check port availability
        int port = baseListenPort + activeTorrents.size();
        log.debug("Using port {} for new torrent", port);
        return port;
    }
}