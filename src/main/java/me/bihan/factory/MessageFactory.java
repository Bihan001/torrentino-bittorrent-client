package me.bihan.factory;

import me.bihan.peer.message.MessageType;
import me.bihan.peer.message.PeerMessage;

/**
 * Factory interface for creating peer messages.
 */
public interface MessageFactory {
    
    /**
     * Creates a message from type and payload.
     */
    PeerMessage createMessage(MessageType messageType, byte[] payload);
    
    /**
     * Creates a message from raw message data.
     */
    PeerMessage parseMessage(byte[] messageData);
    
    /**
     * Checks if the factory supports this message type.
     */
}