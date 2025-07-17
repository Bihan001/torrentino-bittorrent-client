package me.bihan.torrent;

import lombok.Data;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;
import com.dampcake.bencode.Bencode;
import com.dampcake.bencode.Type;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

/**
 * Represents the 'info' section of a torrent file.
 * Contains methods for calculating piece hashes and info hash.
 */
@Data
@Builder
@Jacksonized
public class TorrentInfo {
    private final String name;
    private final Long length;  // For single file torrents
    private final Long pieceLength;
    private final String pieces;  // SHA-1 hashes of all pieces concatenated
    private final List<TorrentFile> files;  // For multi-file torrents

    /**
     * Gets the total length of all files in the torrent.
     */
    public long getTotalLength() {
        if (length != null) {
            // Single file torrent
            return length;
        } else if (files != null) {
            // Multi-file torrent
            return files.stream()
                    .mapToLong(file -> file.getLength() != null ? file.getLength() : 0)
                    .sum();
        }
        return 0;
    }

    /**
     * Gets the number of pieces in the torrent.
     */
    public int getNumPieces() {
        if (pieces == null) {
            return 0;
        }
        // Each piece hash is 20 bytes (SHA-1)
        return pieces.length() / 20;
    }

    /**
     * Gets the SHA-1 hash for a specific piece.
     */
    public byte[] getPieceHash(int pieceIndex) {
        if (pieces == null || pieceIndex < 0 || pieceIndex >= getNumPieces()) {
            throw new IllegalArgumentException("Invalid piece index: " + pieceIndex);
        }

        int startIndex = pieceIndex * 20;
        byte[] hash = new byte[20];
        
        for (int i = 0; i < 20; i++) {
            hash[i] = (byte) pieces.charAt(startIndex + i);
        }
        
        return hash;
    }

    /**
     * Calculates the length of a specific piece.
     * The last piece might be smaller than the standard piece length.
     */
    public long getPieceLength(int pieceIndex) {
        if (pieceIndex < 0 || pieceIndex >= getNumPieces()) {
            throw new IllegalArgumentException("Invalid piece index: " + pieceIndex);
        }

        // Last piece might be smaller
        if (pieceIndex == getNumPieces() - 1) {
            long lastPieceLength = getTotalLength() % pieceLength;
            return lastPieceLength != 0 ? lastPieceLength : pieceLength;
        }

        return pieceLength;
    }

        /**
     * Calculates and returns the info hash for this TorrentInfo.
     * Converts the object to proper bencode field names, then encodes and hashes.
     */
    public byte[] getHash() {
        try {
            // Convert to proper bencode field mapping
            Map<String, Object> infoMap = toBencodeMap();
            
            // Encode to bytes using dampcake bencode library
            Bencode bencode = new Bencode(true);
            byte[] encodedBytes = bencode.encode(infoMap);
            
            // Calculate SHA-1 hash using Apache Commons Codec
            return DigestUtils.sha1(encodedBytes);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate info hash", e);
        }
    }
    
    /**
     * Gets the info hash as a hex string using Apache Commons Codec.
     */
    public String getHashHex() {
        byte[] hash = getHash();
        return Hex.encodeHexString(hash);
    }

    /**
     * Gets the info hash as a URL-encoded string for tracker requests.
     */
    public String getHashUrlEncoded() {
        byte[] hash = getHash();
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%%%02X", b & 0xFF));
        }
        return sb.toString();
    }
    
    /**
     * Converts this TorrentInfo to a Map with proper bencode field names.
     */
    private Map<String, Object> toBencodeMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        
        if (name != null) {
            map.put("name", name);
        }
        
        if (length != null) {
            map.put("length", length);
        }
        
        if (pieceLength != null) {
            map.put("piece length", pieceLength);  // Note: space in field name
        }
        
        if (pieces != null) {
            // Convert pieces string back to byte array for proper bencode encoding
            // Use ISO-8859-1 to preserve exact byte values (pieces contains binary SHA-1 hashes)
            // This is critical for correct infohash calculation!
            map.put("pieces", pieces.getBytes(StandardCharsets.ISO_8859_1));
        }
        
        if (files != null && !files.isEmpty()) {
            List<Map<String, Object>> filesList = files.stream()
                    .map(TorrentFile::toBencodeMap)
                    .toList();
            map.put("files", filesList);
        }
        
        return map;
    }
    
    /**
     * Creates a TorrentInfo from a Map (decoded from bencode).
     * Handles ByteBuffer objects returned by new Bencode(true).
     */
    @SuppressWarnings("unchecked")
    public static TorrentInfo fromMap(Map<String, Object> infoMap) {
        TorrentInfoBuilder builder = TorrentInfo.builder();
        
        if (infoMap.containsKey("name")) {
            Object nameObj = infoMap.get("name");
            if (nameObj instanceof String) {
                builder.name((String) nameObj);
            } else if (nameObj instanceof ByteBuffer) {
                // Convert ByteBuffer to String using UTF-8 for text fields
                ByteBuffer buffer = (ByteBuffer) nameObj;
                builder.name(new String(buffer.array(), StandardCharsets.UTF_8));
            }
        }
        
        if (infoMap.containsKey("length")) {
            Object lengthObj = infoMap.get("length");
            if (lengthObj instanceof Number) {
                builder.length(((Number) lengthObj).longValue());
            }
        }
        
        if (infoMap.containsKey("piece length")) {
            Object pieceLengthObj = infoMap.get("piece length");
            if (pieceLengthObj instanceof Number) {
                builder.pieceLength(((Number) pieceLengthObj).longValue());
            }
        }
        
        if (infoMap.containsKey("pieces")) {
            Object piecesObj = infoMap.get("pieces");
            if (piecesObj instanceof String) {
                builder.pieces((String) piecesObj);
            } else if (piecesObj instanceof byte[]) {
                // Convert byte array to string for pieces (binary data)
                // Use ISO-8859-1 to preserve byte values
                builder.pieces(new String((byte[]) piecesObj, StandardCharsets.ISO_8859_1));
            } else if (piecesObj instanceof ByteBuffer) {
                // Convert ByteBuffer to String using ISO-8859-1 for binary data (pieces)
                ByteBuffer buffer = (ByteBuffer) piecesObj;
                builder.pieces(new String(buffer.array(), StandardCharsets.ISO_8859_1));
            }
        }
        
        // Parse files for multi-file torrents
        if (infoMap.containsKey("files")) {
            Object filesObj = infoMap.get("files");
            if (filesObj instanceof List) {
                List<Map<String, Object>> filesList = (List<Map<String, Object>>) filesObj;
                List<TorrentFile> files = new ArrayList<>();
                
                for (Map<String, Object> fileMap : filesList) {
                    TorrentFile file = TorrentFile.fromMap(fileMap);
                    files.add(file);
                }
                
                builder.files(files);
            }
        }
        
        return builder.build();
    }
} 