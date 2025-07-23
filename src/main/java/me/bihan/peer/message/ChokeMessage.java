package me.bihan.peer.message;

/**
 * Represents a choke message indicating the peer will not accept requests.
 * Message ID: 0
 * Payload: empty
 */
public class ChokeMessage extends PeerMessage {
    
    @Override
    public MessageType getMessageType() {
        return MessageType.CHOKE;
    }
    
    @Override
    public byte[] getPayload() {
        return new byte[0]; // Empty payload
    }
    
    @Override
    public String toString() {
        return "ChokeMessage{}";
    }
} 