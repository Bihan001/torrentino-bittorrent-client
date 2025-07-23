package me.bihan.peer.message;

import java.nio.ByteBuffer;

/**
 * Base class for all BitTorrent peer protocol messages.
 * Message format: <length prefix><message ID><payload>
 */
public abstract class PeerMessage {
    
    /**
     * Gets the message ID for this message type.
     */
    public abstract MessageType getMessageType();
    
    /**
     * Gets the payload of this message.
     */
    public abstract byte[] getPayload();
    
    /**
     * Serializes the message to byte array for sending.
     */
    public byte[] serialize() {
        byte[] payload = getPayload();
        int payloadLength = payload != null ? payload.length : 0;
        
        // Calculate total length: message ID (1 byte) + payload
        int totalLength = 1 + payloadLength;
        
        ByteBuffer buffer = ByteBuffer.allocate(4 + totalLength);
        
        // Write length prefix (4 bytes)
        buffer.putInt(totalLength);
        
        // Write message ID (1 byte)
        buffer.put(getMessageType().getId());
        
        // Write payload
        if (payload != null) {
            buffer.put(payload);
        }
        
        return buffer.array();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "messageType=" + getMessageType() +
                ", payloadLength=" + (getPayload() != null ? getPayload().length : 0) +
                '}';
    }
} 