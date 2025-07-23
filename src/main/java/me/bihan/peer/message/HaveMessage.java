package me.bihan.peer.message;

import java.nio.ByteBuffer;

/**
 * Represents a have message indicating the peer has a specific piece.
 * Message ID: 4
 * Payload: piece index (4 bytes)
 */
public class HaveMessage extends PeerMessage {
    
    private final int pieceIndex;
    
    public HaveMessage(int pieceIndex) {
        this.pieceIndex = pieceIndex;
    }
    
    /**
     * Creates a HaveMessage from payload bytes.
     */
    public static HaveMessage fromPayload(byte[] payload) {
        if (payload.length != 4) {
            throw new IllegalArgumentException("Have message payload must be 4 bytes");
        }
        
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        int pieceIndex = buffer.getInt();
        return new HaveMessage(pieceIndex);
    }
    
    @Override
    public MessageType getMessageType() {
        return MessageType.HAVE;
    }
    
    @Override
    public byte[] getPayload() {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.putInt(pieceIndex);
        return buffer.array();
    }
    
    public int getPieceIndex() {
        return pieceIndex;
    }
    
    @Override
    public String toString() {
        return "HaveMessage{pieceIndex=" + pieceIndex + "}";
    }
} 