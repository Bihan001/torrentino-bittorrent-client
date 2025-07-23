package me.bihan.peer.message;

/**
 * Represents an interested message indicating we want to download from the peer.
 * Message ID: 2
 * Payload: empty
 */
public class InterestedMessage extends PeerMessage {
    
    @Override
    public MessageType getMessageType() {
        return MessageType.INTERESTED;
    }
    
    @Override
    public byte[] getPayload() {
        return new byte[0]; // Empty payload
    }
    
    @Override
    public String toString() {
        return "InterestedMessage{}";
    }
} 