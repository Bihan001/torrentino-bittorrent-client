package me.bihan.service.impl;

import lombok.extern.log4j.Log4j2;
import me.bihan.service.SeedingProgressObserver;
import me.bihan.service.SeedingStats;
import me.bihan.util.FormatUtils;

import java.time.format.DateTimeFormatter;

/**
 * Console-based implementation of SeedingProgressObserver.
 * Provides user-friendly output for seeding operations.
 * Follows Observer pattern and Single Responsibility Principle.
 */
@Log4j2
public class ConsoleSeedingProgressObserver implements SeedingProgressObserver {
    
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    
    @Override
    public void onSeedingStarted(String torrentName, long totalSize) {
        System.out.println("\n=== Seeding Started ===");
        System.out.println("Torrent: " + torrentName);
        System.out.println("Size: " + FormatUtils.formatBytes(totalSize));
        System.out.println("Waiting for peer connections...\n");
        
        log.info("Started seeding: {} ({} bytes)", torrentName, totalSize);
    }
    
    @Override
    public void onPieceUploaded(int pieceIndex, long bytesUploaded, String peerAddress) {
        System.out.printf("Uploaded piece %d (%s) to %s%n", 
                         pieceIndex, FormatUtils.formatBytes(bytesUploaded), peerAddress);
        
        log.debug("Uploaded piece {} ({} bytes) to {}", pieceIndex, bytesUploaded, peerAddress);
    }
    
    @Override
    public void onPeerConnected(String peerAddress) {
        System.out.println("Peer connected: " + peerAddress);
        log.info("Peer connected: {}", peerAddress);
    }
    
    @Override
    public void onPeerDisconnected(String peerAddress) {
        System.out.println("Peer disconnected: " + peerAddress);
        log.info("Peer disconnected: {}", peerAddress);
    }
    
    @Override
    public void onSeedingStopped(String torrentName, long totalUploaded) {
        System.out.println("\n=== Seeding Stopped ===");
        System.out.println("Torrent: " + torrentName);
        System.out.println("Total uploaded: " + FormatUtils.formatBytes(totalUploaded));
        System.out.println();
        
        log.info("Stopped seeding: {} (uploaded {} bytes)", torrentName, totalUploaded);
    }
    
    @Override
    public void onSeedingError(String torrentName, Exception error) {
        System.err.println("\n=== Seeding Error ===");
        System.err.println("Torrent: " + torrentName);
        System.err.println("Error: " + error.getMessage());
        System.err.println();
        
        log.error("Seeding error for {}: {}", torrentName, error.getMessage(), error);
    }
    
    @Override
    public void onSeedingProgress(String torrentName, SeedingStats stats) {
        // Print progress every few updates to avoid spam
        if (stats.getActivePeers() > 0 || stats.getTotalUploaded() > 0) {
            System.out.printf("Seeding %s: %d peers, %s uploaded, %s/s%n",
                             torrentName,
                             stats.getActivePeers(),
                             FormatUtils.formatBytes(stats.getTotalUploaded()),
                             FormatUtils.formatBytes(stats.getUploadSpeed()));
        }
        
        log.debug("Seeding progress for {}: {} peers, {} uploaded", 
                 torrentName, stats.getActivePeers(), stats.getTotalUploaded());
    }
    


}