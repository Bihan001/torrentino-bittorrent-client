package me.bihan.peer.message;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Represents a piece message containing a block of data.
 * Message ID: 7
 * Payload: <index><begin><block>
 */
public class PieceMessage extends PeerMessage {
    
    private final int index;     // Piece index
    private final int begin;     // Byte offset within piece
    private final byte[] block;  // Block data
    
    public PieceMessage(int index, int begin, byte[] block) {
        this.index = index;
        this.begin = begin;
        this.block = Arrays.copyOf(block, block.length);
    }
    
    @Override
    public MessageType getMessageType() {
        return MessageType.PIECE;
    }
    
    @Override
    public byte[] getPayload() {
        ByteBuffer buffer = ByteBuffer.allocate(8 + block.length);
        buffer.putInt(index);
        buffer.putInt(begin);
        buffer.put(block);
        return buffer.array();
    }
    
    /**
     * Creates a PieceMessage from payload bytes.
     */
    public static PieceMessage fromPayload(byte[] payload) {
        if (payload.length < 8) {
            throw new IllegalArgumentException("Piece message payload must be at least 8 bytes");
        }
        
        ByteBuffer buffer = ByteBuffer.wrap(payload);
        int index = buffer.getInt();
        int begin = buffer.getInt();
        
        byte[] block = new byte[payload.length - 8];
        buffer.get(block);
        
        return new PieceMessage(index, begin, block);
    }
    
    public int getIndex() { return index; }
    public int getBegin() { return begin; }
    public byte[] getBlock() { return Arrays.copyOf(block, block.length); }
    public int getBlockLength() { return block.length; }
    
    @Override
    public String toString() {
        return "PieceMessage{" +
                "index=" + index +
                ", begin=" + begin +
                ", blockLength=" + block.length +
                '}';
    }
} 