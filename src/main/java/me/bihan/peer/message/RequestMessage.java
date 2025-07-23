package me.bihan.peer.message;

import java.nio.ByteBuffer;

/**
 * Represents a request message for requesting a block of data.
 * Message ID: 6
 * Payload: <index><begin><length>
 */
public class RequestMessage extends PeerMessage {
    
    private final int index;    // Piece index
    private final int begin;    // Byte offset within piece
    private final int length;   // Block length
    
    public RequestMessage(int index, int begin, int length) {
        this.index = index;
        this.begin = begin;
        this.length = length;
    }
    
    @Override
    public MessageType getMessageType() {
        return MessageType.REQUEST;
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
     * Creates a RequestMessage from payload bytes.
     */
    public static RequestMessage fromPayload(byte[] payload) {
        if (payload.length != 12) {
            throw new IllegalArgumentException("Request message payload must be 12 bytes");
        }
        
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        int index = buffer.getInt();
        int begin = buffer.getInt();
        int length = buffer.getInt();
        
        return new RequestMessage(index, begin, length);
    }
    
    public int getIndex() { return index; }
    public int getBegin() { return begin; }
    public int getLength() { return length; }
    
    @Override
    public String toString() {
        return "RequestMessage{" +
                "index=" + index +
                ", begin=" + begin +
                ", length=" + length +
                '}';
    }
} 