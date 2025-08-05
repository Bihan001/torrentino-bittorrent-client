package me.bihan.service;

/**
 * Observer interface for download progress updates.
 */
public interface DownloadProgressObserver {
    
    /**
     * Called when download starts.
     */
    void onDownloadStarted(String torrentName, long totalSize);
    
    /**
     * Called when a piece is completed.
     */
    void onPieceCompleted(int pieceIndex, int totalPieces, long bytesDownloaded);
    
    /**
     * Called when download completes.
     */
    void onDownloadCompleted(String torrentName, long totalSize);
    
    /**
     * Called when download fails.
     */
    void onDownloadFailed(String torrentName, Exception error);
    
    /**
     * Called for periodic progress updates.
     */
    void onProgressUpdate(double progressPercentage, long downloadSpeed);
    
}