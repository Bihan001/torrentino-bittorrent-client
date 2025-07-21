package me.bihan.peer;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/**
 * Generates unique peer IDs for BitTorrent protocol.
 */
public class PeerIdGenerator {
    
    private static final String CLIENT_ID = "-BT0001-"; // Our client identifier
    private static final SecureRandom random = new SecureRandom();
    
    /**
     * Generates a unique 20-byte peer ID.
     * Format: -BT0001-XXXXXXXXXXXX where X is random alphanumeric.
     */
    public static String generatePeerId() {
        StringBuilder peerId = new StringBuilder(CLIENT_ID);
        
        // Generate 12 random alphanumeric characters
        String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        for (int i = 0; i < 12; i++) {
            peerId.append(chars.charAt(random.nextInt(chars.length())));
        }
        
        return peerId.toString();
    }
    
    /**
     * Generates a peer ID as byte array.
     */
    public static byte[] generatePeerIdBytes() {
        return generatePeerId().getBytes(StandardCharsets.UTF_8);
    }

} 