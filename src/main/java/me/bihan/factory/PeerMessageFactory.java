package me.bihan.factory;

import lombok.extern.log4j.Log4j2;
import me.bihan.peer.message.*;

import java.nio.ByteBuffer;

/**
 * Concrete factory for creating peer messages.
 * Implements Factory pattern and follows Open/Closed Principle.
 */
@Log4j2
public class PeerMessageFactory implements MessageFactory {
    
    @Override
    public PeerMessage createMessage(MessageType messageType, byte[] payload) {
        return switch (messageType) {
            case CHOKE -> new ChokeMessage();
            case UNCHOKE -> new UnchokeMessage();
            case INTERESTED -> new InterestedMessage();
            case NOT_INTERESTED -> new NotInterestedMessage();
            case HAVE -> HaveMessage.fromPayload(payload);
            case BITFIELD -> new BitfieldMessage(payload);
            case REQUEST -> RequestMessage.fromPayload(payload);
            case PIECE -> PieceMessage.fromPayload(payload);
            case CANCEL -> CancelMessage.fromPayload(payload);
            case HAVE_ALL -> new HaveAllMessage();
            case HAVE_NONE -> new HaveNoneMessage();
            default -> throw new IllegalArgumentException("Unsupported message type: " + messageType);
        };
    }
    
    @Override
    public PeerMessage parseMessage(byte[] messageData) {
        if (messageData == null || messageData.length < 5) {
            throw new IllegalArgumentException("Invalid message data");
        }
        
        ByteBuffer buffer = ByteBuffer.wrap(messageData);
        
        // Read length prefix
        int length = buffer.getInt();
        if (length < 1) {
            return null; // Keep-alive message
        }
        
        // Read message ID
        byte messageId = buffer.get();
        
        // Special handling for extended messages (ID 20)
        if (messageId == 20) {
            if (length < 2) {
                throw new IllegalArgumentException("Extended message must have at least extended message ID");
            }
            
            // Read the actual extended message ID from the next byte
            byte extendedMessageId = buffer.get();
            
            // Read the remaining payload
            int payloadLength = length - 2; // Subtract 1 for messageId and 1 for extendedMessageId
            byte[] payload = new byte[payloadLength];
            if (payloadLength > 0) {
                buffer.get(payload);
            }
            
            log.debug("Received extended message with ID: {} and payload length: {}", 
                     extendedMessageId & 0xFF, payloadLength);

            MessageType messageType = MessageType.fromId(extendedMessageId);
            return createMessage(messageType, payload);
        }
        
        // Regular message handling
        MessageType messageType = MessageType.fromId(messageId);
        
        // Read payload
        int payloadLength = length - 1;
        byte[] payload = new byte[payloadLength];
        if (payloadLength > 0) {
            buffer.get(payload);
        }
        
        return createMessage(messageType, payload);
    }
    
} 