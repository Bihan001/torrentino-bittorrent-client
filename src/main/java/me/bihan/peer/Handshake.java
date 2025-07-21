package me.bihan.peer;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Represents a BitTorrent handshake message.
 * Format: <pstrlen><pstr><reserved><info_hash><peer_id>
 */
public class Handshake {
    
    private static final String PROTOCOL_STRING = "BitTorrent protocol";
    private static final int HANDSHAKE_LENGTH = 49 + PROTOCOL_STRING.length();
    
    private final byte protocolLength;
    private final String protocol;
    private final byte[] reserved;
    private final byte[] infoHash;
    private final byte[] peerId;
    
    /**
     * Creates a handshake for sending to a peer.
     */
    public Handshake(byte[] infoHash, byte[] peerId) {
        this.protocolLength = (byte) PROTOCOL_STRING.length();
        this.protocol = PROTOCOL_STRING;
        this.reserved = new byte[8]; // All zeros
        this.infoHash = Arrays.copyOf(infoHash, 20);
        this.peerId = Arrays.copyOf(peerId, 20);
    }
    
    /**
     * Creates a handshake from received data.
     */
    private Handshake(byte protocolLength, String protocol, byte[] reserved, 
                     byte[] infoHash, byte[] peerId) {
        this.protocolLength = protocolLength;
        this.protocol = protocol;
        this.reserved = Arrays.copyOf(reserved, 8);
        this.infoHash = Arrays.copyOf(infoHash, 20);
        this.peerId = Arrays.copyOf(peerId, 20);
    }
    
    /**
     * Serializes the handshake to byte array for sending.
     */
    public byte[] serialize() {
        ByteBuffer buffer = ByteBuffer.allocate(HANDSHAKE_LENGTH);
        
        buffer.put(protocolLength);
        buffer.put(protocol.getBytes(StandardCharsets.UTF_8));
        buffer.put(reserved);
        buffer.put(infoHash);
        buffer.put(peerId);
        
        return buffer.array();
    }
    
    /**
     * Parses a handshake from received byte data.
     */
    public static Handshake parse(byte[] data) throws IllegalArgumentException {
        if (data == null || data.length < 1) {
            throw new IllegalArgumentException("Invalid handshake data");
        }
        
        ByteBuffer buffer = ByteBuffer.wrap(data);
        
        // Read protocol length
        byte pstrlen = buffer.get();
        if (pstrlen != PROTOCOL_STRING.length()) {
            throw new IllegalArgumentException("Invalid protocol length: " + pstrlen);
        }
        
        // Check if we have enough data
        int expectedLength = 1 + pstrlen + 8 + 20 + 20;
        if (data.length < expectedLength) {
            throw new IllegalArgumentException("Incomplete handshake data");
        }
        
        // Read protocol string
        byte[] protocolBytes = new byte[pstrlen];
        buffer.get(protocolBytes);
        String protocol = new String(protocolBytes, StandardCharsets.UTF_8);
        
        if (!PROTOCOL_STRING.equals(protocol)) {
            throw new IllegalArgumentException("Invalid protocol: " + protocol);
        }
        
        // Read reserved bytes
        byte[] reserved = new byte[8];
        buffer.get(reserved);
        
        // Read info hash
        byte[] infoHash = new byte[20];
        buffer.get(infoHash);
        
        // Read peer ID
        byte[] peerId = new byte[20];
        buffer.get(peerId);
        
        return new Handshake(pstrlen, protocol, reserved, infoHash, peerId);
    }
    
    /**
     * Validates if this handshake matches the expected info hash.
     */
    public boolean isValidFor(byte[] expectedInfoHash) {
        return Arrays.equals(this.infoHash, expectedInfoHash);
    }

    public byte[] getInfoHash() { return Arrays.copyOf(infoHash, infoHash.length); }
    public byte[] getPeerId() { return Arrays.copyOf(peerId, peerId.length); }
    
    @Override
    public String toString() {
        return "Handshake{" +
                "protocol='" + protocol + '\'' +
                ", infoHash=" + bytesToHex(infoHash) +
                ", peerId=" + new String(peerId, StandardCharsets.UTF_8) +
                '}';
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
} 