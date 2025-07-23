package me.bihan.peer.message;

/**
 * Represents a have none message indicating the peer has no pieces.
 * Message ID: 15
 * Payload: empty
 * Part of the Fast Extension (optional)
 */
public class HaveNoneMessage extends PeerMessage {
    
    @Override
    public MessageType getMessageType() {
        return MessageType.HAVE_NONE;
    }
    
    @Override
    public byte[] getPayload() {
        return new byte[0]; // Empty payload
    }
    
    @Override
    public String toString() {
        return "HaveNoneMessage{}";
    }
} 