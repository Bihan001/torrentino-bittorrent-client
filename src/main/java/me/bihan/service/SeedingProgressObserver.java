package me.bihan.service;

/**
 * Observer interface for seeding progress events.
 * Follows Observer pattern for loose coupling.
 */
public interface SeedingProgressObserver {
    
    /**
     * Called when seeding starts for a torrent.
     * @param torrentName Name of the torrent
     * @param totalSize Total size in bytes
     */
    void onSeedingStarted(String torrentName, long totalSize);
    
    /**
     * Called when a piece is uploaded to a peer.
     * @param pieceIndex Index of the uploaded piece
     * @param bytesUploaded Number of bytes uploaded
     * @param peerAddress Address of the peer that received the piece
     */
    void onPieceUploaded(int pieceIndex, long bytesUploaded, String peerAddress);
    
    /**
     * Called when a new peer connects for downloading.
     * @param peerAddress Address of the connected peer
     */
    void onPeerConnected(String peerAddress);
    
    /**
     * Called when a peer disconnects.
     * @param peerAddress Address of the disconnected peer
     */
    void onPeerDisconnected(String peerAddress);
    
    /**
     * Called when seeding stops for a torrent.
     * @param torrentName Name of the torrent
     * @param totalUploaded Total bytes uploaded during this seeding session
     */
    void onSeedingStopped(String torrentName, long totalUploaded);
    
    /**
     * Called when seeding encounters an error.
     * @param torrentName Name of the torrent
     * @param error The error that occurred
     */
    void onSeedingError(String torrentName, Exception error);
    
    /**
     * Called periodically with seeding statistics.
     * @param torrentName Name of the torrent
     * @param stats Current seeding statistics
     */
    void onSeedingProgress(String torrentName, SeedingStats stats);
}