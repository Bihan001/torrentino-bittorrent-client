package me.bihan.service;

import lombok.extern.log4j.Log4j2;
import me.bihan.torrent.TorrentInfo;

import java.util.BitSet;

/**
 * Manages the bitfield representing which pieces we have available.
 * Used for both tracking download progress and advertising available pieces to peers.
 * Follows Single Responsibility Principle - only manages piece availability.
 */
@Log4j2
public class BitfieldManager {
    
    private final TorrentInfo torrentInfo;
    private final BitSet availablePieces;
    
    public BitfieldManager(TorrentInfo torrentInfo) {
        this.torrentInfo = torrentInfo;
        this.availablePieces = new BitSet(torrentInfo.getNumPieces());
    }
    
    /**
     * Mark a piece as available.
     * @param pieceIndex The piece index to mark as available
     */
    public synchronized void markPieceAvailable(int pieceIndex) {
        if (isValidPieceIndex(pieceIndex)) {
            availablePieces.set(pieceIndex);
            log.debug("Marked piece {} as available ({}/{} total)", 
                     pieceIndex, availablePieces.cardinality(), torrentInfo.getNumPieces());
        }
    }
    
    /**
     * Mark a piece as unavailable.
     * @param pieceIndex The piece index to mark as unavailable
     */
    public synchronized void markPieceUnavailable(int pieceIndex) {
        if (isValidPieceIndex(pieceIndex)) {
            availablePieces.clear(pieceIndex);
            log.debug("Marked piece {} as unavailable", pieceIndex);
        }
    }
    
    /**
     * Check if we have a specific piece.
     * @param pieceIndex The piece index to check
     * @return true if we have the piece
     */
    public boolean hasPiece(int pieceIndex) {
        return isValidPieceIndex(pieceIndex) && availablePieces.get(pieceIndex);
    }
    
    /**
     * Get the number of pieces we have.
     * @return number of available pieces
     */
    public int getAvailablePiecesCount() {
        return availablePieces.cardinality();
    }
    
    /**
     * Check if we have all pieces (complete download/seed).
     * @return true if we have all pieces
     */
    public boolean isComplete() {
        return availablePieces.cardinality() == torrentInfo.getNumPieces();
    }
    
    /**
     * Get completion percentage.
     * @return percentage of pieces we have (0.0 to 100.0)
     */
    public double getCompletionPercentage() {
        if (torrentInfo.getNumPieces() == 0) {
            return 0.0;
        }
        return (double) availablePieces.cardinality() / torrentInfo.getNumPieces() * 100.0;
    }
    
    /**
     * Generate bitfield message payload for sending to peers.
     * @return byte array representing our bitfield
     */
    public byte[] generateBitfieldPayload() {
        int numPieces = torrentInfo.getNumPieces();
        // Number of bytes needed for payload.
        // Rounding up to extra bit if numPieces is not a multiple of 8. For example, for numPieces = 90 -> We would need 12 bytes ((11 * 8) + 2)
        int numBytes = (numPieces + 7) / 8;
        byte[] bitfield = new byte[numBytes];
        
        for (int pieceIndex = 0; pieceIndex < numPieces; pieceIndex++) {
            if (availablePieces.get(pieceIndex)) {
                int byteIndex = pieceIndex / 8;
                int bitIndex = 7 - (pieceIndex % 8); // Most significant bit first
                bitfield[byteIndex] |= (byte) (1 << bitIndex);
            }
        }
        
        return bitfield;
    }

    /**
     * Get a copy of the current bitfield for thread safety.
     * @return copy of the bitfield
     */
    public BitSet getBitfieldCopy() {
        return (BitSet) availablePieces.clone();
    }
    
    private boolean isValidPieceIndex(int pieceIndex) {
        return pieceIndex >= 0 && pieceIndex < torrentInfo.getNumPieces();
    }
    
    @Override
    public String toString() {
        return String.format("BitfieldManager{torrent='%s', available=%d/%d (%.1f%%)}",
                torrentInfo.getName(), availablePieces.cardinality(), 
                torrentInfo.getNumPieces(), getCompletionPercentage());
    }
}