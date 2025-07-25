package me.bihan.tracker;

import lombok.Value;
import lombok.Builder;

/**
 * Represents a peer in the BitTorrent network.
 */
@Value
@Builder
public class Peer {
    
    String ip;
    int port;
    String peerId;
    
    /**
     * Constructor for peers without peer ID.
     */
    public Peer(String ip, int port) {
        this.ip = ip;
        this.port = port;
        this.peerId = null;
    }

    /**
     * Constructor for peers with peer ID.
     */
    public Peer(String ip, int port, String peerId) {
        this.ip = ip;
        this.port = port;
        this.peerId = peerId;
    }
    
    /**
     * Returns the peer address as "ip:port".
     */
    public String getAddress() {
        return (ip != null ? ip : "unknown") + ":" + port;
    }
} 