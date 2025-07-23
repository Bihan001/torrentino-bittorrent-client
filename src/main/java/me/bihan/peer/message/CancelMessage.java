package me.bihan.peer.message;

import java.nio.ByteBuffer;

/**
 * Represents a cancel message for canceling a previously sent request.
 * Message ID: 8
 * Payload: <index><begin><length>
 */
public class CancelMessage extends PeerMessage {
    
    private final int index;    // Piece index
    private final int begin;    // Byte offset within piece
    private final int length;   // Block length
    
    public CancelMessage(int index, int begin, int length) {
        this.index = index;
        this.begin = begin;
        this.length = length;
    }
    
    @Override
    public MessageType getMessageType() {
        return MessageType.CANCEL;
    }
    
    @Override
    public byte[] getPayload() {
        ByteBuffer buffer = ByteBuffer.allocate(12);
        buffer.putInt(index);
        buffer.putInt(begin);
        buffer.putInt(length);
        return buffer.array();
    }
    
    /**
     * Creates a CancelMessage from payload bytes.
     */
    public static CancelMessage fromPayload(byte[] payload) {
        if (payload.length != 12) {
            throw new IllegalArgumentException("Cancel message payload must be 12 bytes");
        }
        
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        int index = buffer.getInt();
        int begin = buffer.getInt();
        int length = buffer.getInt();
        
        return new CancelMessage(index, begin, length);
    }
    
    public int getIndex() { return index; }
    public int getBegin() { return begin; }
    public int getLength() { return length; }
    
    @Override
    public String toString() {
        return "CancelMessage{" +
                "index=" + index +
                ", begin=" + begin +
                ", length=" + length +
                '}';
    }
} 