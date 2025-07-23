package me.bihan.peer.message;

/**
 * Represents a not interested message indicating the peer is not interested in the remote peer.
 * Message ID: 3
 * Payload: empty
 */
public class NotInterestedMessage extends PeerMessage {
    
    @Override
    public MessageType getMessageType() {
        return MessageType.NOT_INTERESTED;
    }
    
    @Override
    public byte[] getPayload() {
        return new byte[0]; // Empty payload
    }
    
    @Override
    public String toString() {
        return "NotInterestedMessage{}";
    }
} 