package me.bihan.peer.message;

/**
 * Represents an unchoke message indicating the peer will accept requests.
 * Message ID: 1
 * Payload: empty
 */
public class UnchokeMessage extends PeerMessage {
    
    @Override
    public MessageType getMessageType() {
        return MessageType.UNCHOKE;
    }
    
    @Override
    public byte[] getPayload() {
        return new byte[0]; // Empty payload
    }
    
    @Override
    public String toString() {
        return "UnchokeMessage{}";
    }
} 