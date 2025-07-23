package me.bihan.peer.message;

/**
 * Enumeration of BitTorrent peer message types.
 */
public enum MessageType {
    CHOKE((byte) 0),
    UNCHOKE((byte) 1),
    INTERESTED((byte) 2),
    NOT_INTERESTED((byte) 3),
    HAVE((byte) 4),
    BITFIELD((byte) 5),
    REQUEST((byte) 6),
    PIECE((byte) 7),
    CANCEL((byte) 8),
    PORT((byte) 9),                 // DHT port (BEP 5)
    SUGGEST_PIECE((byte) 13),       // rare
    HAVE_ALL((byte) 14),            // optional
    HAVE_NONE((byte) 15),           // optional
    REJECT_REQUEST((byte) 16),      // libtorrent extension
    ALLOWED_FAST((byte) 17);        // BEP 6
    // Note: 20 is not a message type but indicates extended messages (BEP 10)
    
    private final byte id;
    
    MessageType(byte id) {
        this.id = id;
    }
    
    public byte getId() {
        return id;
    }
    
    /**
     * Gets the message type from its ID.
     */
    public static MessageType fromId(byte id) {
        for (MessageType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown message type ID: " + id);
    }
} 