package me.bihan.peer.message;

import java.util.Arrays;

/**
 * Represents a bitfield message that indicates which pieces a peer has.
 * Message ID: 5
 */
public class BitfieldMessage extends PeerMessage {
    
    private final byte[] bitfield;
    
    public BitfieldMessage(byte[] bitfield) {
        this.bitfield = Arrays.copyOf(bitfield, bitfield.length);
    }
    
    @Override
    public MessageType getMessageType() {
        return MessageType.BITFIELD;
    }
    
    @Override
    public byte[] getPayload() {
        return Arrays.copyOf(bitfield, bitfield.length);
    }
    
    public byte[] getBitfield() {
        return Arrays.copyOf(bitfield, bitfield.length);
    }
    
    /**
     * Checks if the peer has a specific piece.
     */
    public boolean hasPiece(int pieceIndex) {
        int byteIndex = pieceIndex / 8;
        int bitIndex = pieceIndex % 8;
        
        if (byteIndex >= bitfield.length) {
            return false;
        }
        
        return (bitfield[byteIndex] & (1 << (7 - bitIndex))) != 0;
    }
    
    /**
     * Gets the number of pieces this bitfield covers.
     */
    public int getNumPieces() {
        return bitfield.length * 8;
    }
    
    @Override
    public String toString() {
        return "BitfieldMessage{" +
                "bitfieldLength=" + bitfield.length +
                ", numPieces=" + getNumPieces() +
                '}';
    }
} 