package me.bihan.peer.message;

/**
 * Represents a have all message indicating the peer has all pieces.
 * Message ID: 14
 * Payload: empty
 * Part of the Fast Extension (optional)
 */
public class HaveAllMessage extends PeerMessage {
    
    @Override
    public MessageType getMessageType() {
        return MessageType.HAVE_ALL;
    }
    
    @Override
    public byte[] getPayload() {
        return new byte[0]; // Empty payload
    }
    
    @Override
    public String toString() {
        return "HaveAllMessage{}";
    }
} 