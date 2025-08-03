package me.bihan.service;

import lombok.extern.log4j.Log4j2;
import me.bihan.service.strategy.FileManager;
import me.bihan.service.strategy.PieceInfo;
import me.bihan.torrent.TorrentInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.BitSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Unified piece state management for both downloading and seeding.
 * Single source of truth for piece availability, download queue, and persistence.
 * Eliminates the need for separate PieceQueue, BitfieldManager, and DownloadState.
 */
@Log4j2
public class PieceManager {
    
    public enum PieceState {
        NOT_HAVE,      // We don't have this piece
        DOWNLOADING,   // Currently being downloaded
        HAVE          // We have this piece (available for seeding)
    }
    
    final TorrentInfo torrentInfo; // Package-private for adapter access
    private final String downloadDirectory;
    private final HashCalculatorService hashService;
    
    // Single source of truth for piece states
    private final PieceState[] pieceStates;
    private final BitSet havePieces;
    
    // Download queue for pieces that need to be downloaded
    private final BlockingQueue<PieceInfo> downloadQueue;
    private final AtomicInteger remainingToDownload;
    
    // State file for persistence
    private final Path stateFilePath;
    private volatile boolean shutdown = false;
    
    // Retry configuration
    private static final int MAX_RETRIES = 5;
    private static final long RETRY_DELAY_MS = 2000;
    
    public PieceManager(TorrentInfo torrentInfo, String downloadDirectory, HashCalculatorService hashService) {
        this.torrentInfo = torrentInfo;
        this.downloadDirectory = downloadDirectory;
        this.hashService = hashService;
        
        int numPieces = torrentInfo.getNumPieces();
        this.pieceStates = new PieceState[numPieces];
        this.havePieces = new BitSet(numPieces);
        this.downloadQueue = new LinkedBlockingQueue<>();
        this.remainingToDownload = new AtomicInteger(0);
        
        // Initialize all pieces as NOT_HAVE
        Arrays.fill(pieceStates, PieceState.NOT_HAVE);
        
        this.stateFilePath = Paths.get(downloadDirectory, torrentInfo.getName() + ".state");
    }
    
    /**
     * Initialize by checking existing files and loading previous state.
     * Returns true if all pieces are already available (full seed), false if download needed.
     */
    public boolean initializeAndVerifyExistingFiles(FileManager fileManager) throws IOException {
        log.info("Initializing piece manager and checking existing files for: {}", torrentInfo.getName());
        
        // First check if files exist on disk
        boolean allFilesExist = checkFilesExistOnDisk();
        
        if (allFilesExist) {
            log.info("All files exist on disk, verifying piece hashes...");
            return verifyAllPiecesFromDisk(fileManager);
        } else {
            log.info("Not all files exist, loading previous download state...");
            loadPreviousDownloadState(fileManager);
            queueMissingPiecesForDownload();
            return false;
        }
    }
    
    /**
     * Check if all required files exist on disk.
     */
    private boolean checkFilesExistOnDisk() {
        try {
            Path basePath = Paths.get(downloadDirectory);
            
            // For single file torrents
            if (torrentInfo.getFiles() == null || torrentInfo.getFiles().isEmpty()) {
                Path filePath = basePath.resolve(torrentInfo.getName());
                return Files.exists(filePath) && Files.size(filePath) == torrentInfo.getTotalLength();
            }
            
            // For multi-file torrents
            for (var file : torrentInfo.getFiles()) {
                Path filePath = basePath.resolve(torrentInfo.getName());
                for (String pathComponent : file.getPath()) {
                    filePath = filePath.resolve(pathComponent);
                }
                
                if (!Files.exists(filePath) || Files.size(filePath) != file.getLength()) {
                    return false;
                }
            }
            
            return true;
            
        } catch (IOException e) {
            log.warn("Error checking file existence: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Verify all pieces from existing files on disk.
     * Returns true if all pieces are valid (full seed).
     */
    private boolean verifyAllPiecesFromDisk(FileManager fileManager) {
        log.info("Verifying all {} pieces from existing files...", torrentInfo.getNumPieces());
        
        int validPieces = 0;
        int corruptedPieces = 0;
        
        for (int pieceIndex = 0; pieceIndex < torrentInfo.getNumPieces(); pieceIndex++) {
            try {
                byte[] pieceData = fileManager.readPiece(pieceIndex);
                byte[] expectedHash = torrentInfo.getPieceHash(pieceIndex);
                byte[] actualHash = hashService.sha1Hash(pieceData);
                
                if (Arrays.equals(expectedHash, actualHash)) {
                    markPieceAsHave(pieceIndex);
                    validPieces++;
                    log.debug("Piece {} is read from disk and verified", pieceIndex);
                } else {
                    log.warn("Piece {} failed hash verification, will need to download", pieceIndex);
                    corruptedPieces++;
                }
            } catch (Exception e) {
                log.warn("Error verifying piece {}: {}", pieceIndex, e.getMessage());
                corruptedPieces++;
            }
        }
        
        log.info("Verification complete: {} valid pieces, {} corrupted/missing pieces", 
                validPieces, corruptedPieces);
        
        if (corruptedPieces > 0) {
            queueMissingPiecesForDownload();
            return false;
        }
        
        return true; // All pieces are valid - ready to seed
    }
    
    /**
     * Load previous download state and verify existing pieces.
     */
    private void loadPreviousDownloadState(FileManager fileManager) throws IOException {
        if (!Files.exists(stateFilePath)) {
            log.info("No previous download state found");
            return;
        }
        
        log.info("Loading previous download state from: {}", stateFilePath);
        
        try {
            byte[] stateData = Files.readAllBytes(stateFilePath);
            BitSet previousState = BitSet.valueOf(stateData);
            
            int verifiedPieces = 0;
            int corruptedPieces = 0;
            
            for (int pieceIndex = 0; pieceIndex < torrentInfo.getNumPieces(); pieceIndex++) {
                if (previousState.get(pieceIndex)) {
                    // Verify this piece still exists and is valid
                    try {
                        byte[] pieceData = fileManager.readPiece(pieceIndex);
                        byte[] expectedHash = torrentInfo.getPieceHash(pieceIndex);
                        byte[] actualHash = hashService.sha1Hash(pieceData);
                        
                        if (Arrays.equals(expectedHash, actualHash)) {
                            markPieceAsHave(pieceIndex);
                            verifiedPieces++;
                        } else {
                            log.warn("Previously completed piece {} is now corrupted", pieceIndex);
                            corruptedPieces++;
                        }
                    } catch (Exception e) {
                        log.warn("Error reading previously completed piece {}: {}", pieceIndex, e.getMessage());
                        corruptedPieces++;
                    }
                }
            }
            
            log.info("State loaded: {} verified pieces, {} corrupted pieces", 
                    verifiedPieces, corruptedPieces);
            
        } catch (Exception e) {
            log.warn("Error loading previous state, starting fresh: {}", e.getMessage());
        }
    }
    
    /**
     * Queue all missing pieces for download.
     */
    private void queueMissingPiecesForDownload() {
        int queuedPieces = 0;
        
        for (int pieceIndex = 0; pieceIndex < torrentInfo.getNumPieces(); pieceIndex++) {
            if (pieceStates[pieceIndex] == PieceState.NOT_HAVE) {
                downloadQueue.offer(new PieceInfo(pieceIndex, torrentInfo.getPieceLength(pieceIndex)));
                queuedPieces++;
            }
        }
        
        remainingToDownload.set(queuedPieces);
        log.info("Queued {} pieces for download", queuedPieces);
    }
    
    /**
     * Get next piece to download. Returns null if all pieces are available or download is complete.
     */
    public PieceInfo getNextPieceToDownload() throws InterruptedException {
        if (isDownloadComplete()) {
            return null;
        }
        
        while (!shutdown) {
            PieceInfo piece = downloadQueue.poll(1000, TimeUnit.MILLISECONDS);
            if (piece != null) {
                // Mark as downloading
                synchronized (this) {
                    if (pieceStates[piece.getIndex()] == PieceState.NOT_HAVE) {
                        pieceStates[piece.getIndex()] = PieceState.DOWNLOADING;
                        return piece;
                    } else {
                        // Piece was completed by another thread, continue
                        continue;
                    }
                }
            }
            
            if (isDownloadComplete()) {
                return null;
            }
        }
        
        return null;
    }
    
    /**
     * Mark a piece as completed after successful download and verification.
     * This makes it immediately available for seeding.
     */
    public synchronized void markPieceCompleted(int pieceIndex) {
        if (isValidPieceIndex(pieceIndex)) {
            markPieceAsHave(pieceIndex);
            remainingToDownload.decrementAndGet();
            
            // Save state periodically
            if (getCompletedPiecesCount() % 10 == 0) {
                saveState();
            }
            
            log.info("Piece {} completed and now available for seeding ({}/{} pieces)", 
                    pieceIndex, getCompletedPiecesCount(), torrentInfo.getNumPieces());
        }
    }
    
    /**
     * Return a piece to download queue for retry.
     */
    public void returnPieceForRetry(PieceInfo piece) {
        synchronized (this) {
            if (pieceStates[piece.getIndex()] == PieceState.DOWNLOADING) {
                pieceStates[piece.getIndex()] = PieceState.NOT_HAVE;
                
                if (piece.canRetry(MAX_RETRIES, RETRY_DELAY_MS)) {
                    piece.incrementRetry();
                    downloadQueue.offer(piece);
                    log.debug("Returned piece {} for retry (attempt {})", 
                             piece.getIndex(), piece.getRetryCount());
                } else {
                    log.warn("Piece {} exceeded max retries, marking as completed to avoid infinite loop", 
                             piece.getIndex());
                    markPieceCompleted(piece.getIndex());
                }
            }
        }
    }
    
    /**
     * Mark piece as having (internal method).
     */
    private void markPieceAsHave(int pieceIndex) {
        pieceStates[pieceIndex] = PieceState.HAVE;
        havePieces.set(pieceIndex);
    }
    
    /**
     * Check if we have a specific piece (for seeding).
     */
    public boolean hasPiece(int pieceIndex) {
        return isValidPieceIndex(pieceIndex) && pieceStates[pieceIndex] == PieceState.HAVE;
    }
    
    /**
     * Get bitfield for sending to peers.
     */
    public BitSet getBitfield() {
        return (BitSet) havePieces.clone();
    }
    
    /**
     * Get completed pieces count.
     */
    public int getCompletedPiecesCount() {
        return havePieces.cardinality();
    }
    
    /**
     * Check if download is complete (all pieces available).
     */
    public boolean isDownloadComplete() {
        return getCompletedPiecesCount() == torrentInfo.getNumPieces();
    }
    
    /**
     * Get pieces remaining to download.
     */
    public int getRemainingToDownload() {
        return remainingToDownload.get();
    }
    
    /**
     * Save current state to disk.
     */
    public void saveState() {
        try {
            Files.createDirectories(stateFilePath.getParent());
            Files.write(stateFilePath, havePieces.toByteArray());
            log.debug("Saved download state: {}/{} pieces completed", 
                     getCompletedPiecesCount(), torrentInfo.getNumPieces());
        } catch (IOException e) {
            log.warn("Failed to save download state: {}", e.getMessage());
        }
    }
    
    /**
     * Clean up state file after download completion.
     */
    public void cleanupStateFile() {
        try {
            if (Files.exists(stateFilePath)) {
                Files.delete(stateFilePath);
                log.info("Cleaned up download state file");
            }
        } catch (IOException e) {
            log.warn("Failed to cleanup state file: {}", e.getMessage());
        }
    }
    
    /**
     * Shutdown the piece manager.
     */
    public void shutdown() {
        shutdown = true;
        saveState();
        log.info("Piece manager shutdown");
    }
    
    private boolean isValidPieceIndex(int pieceIndex) {
        return pieceIndex >= 0 && pieceIndex < torrentInfo.getNumPieces();
    }
}