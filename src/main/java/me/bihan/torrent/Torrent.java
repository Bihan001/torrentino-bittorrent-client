package me.bihan.torrent;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Value;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

/**
 * Represents a complete torrent file structure.
 */
@Value
@Builder
@Jacksonized
@AllArgsConstructor
public class Torrent {
    
    String announce;
    List<List<String>> announceList;
    TorrentInfo info;
    String comment;
    String createdBy;
    Long creationDate;
    String encoding;
    
    /**
     * Returns the primary tracker URL, falling back to announce-list if needed.
     */
    public String getPrimaryTracker() {
        if (announce != null && !announce.trim().isEmpty()) {
            return announce;
        }
        
        if (announceList != null && !announceList.isEmpty()) {
            for (List<String> tier : announceList) {
                if (!tier.isEmpty()) {
                    return tier.get(0);
                }
            }
        }
        
        return null;
    }
    
    /**
     * Returns all tracker URLs from both announce and announce-list.
     */
    public List<String> getAllTrackers() {
        List<String> trackers = new ArrayList<>();
        
        if (announce != null && !announce.trim().isEmpty()) {
            trackers.add(announce);
        }
        
        if (announceList != null) {
            for (List<String> tier : announceList) {
                for (String tracker : tier) {
                    if (!trackers.contains(tracker)) {
                        trackers.add(tracker);
                    }
                }
            }
        }
        
        return trackers;
    }
    
    /**
     * Validates that this torrent has required fields.
     */
    public boolean isValid() {
        return info != null && 
               info.getName() != null && 
               info.getPieceLength() != null && 
               info.getPieces() != null &&
               getPrimaryTracker() != null;
    }
    
    /**
     * Creates a Torrent from a Map (decoded from bencode).
     * Handles ByteBuffer objects returned by new Bencode(true).
     */
    @SuppressWarnings("unchecked")
    public static Torrent fromMap(Map<String, Object> map) {
        TorrentBuilder builder = Torrent.builder();
        
        // Basic torrent fields
        if (map.containsKey("announce")) {
            Object announceObj = map.get("announce");
            if (announceObj instanceof String) {
                builder.announce((String) announceObj);
            } else if (announceObj instanceof ByteBuffer) {
                ByteBuffer buffer = (ByteBuffer) announceObj;
                builder.announce(new String(buffer.array(), StandardCharsets.UTF_8));
            }
        }
        
        if (map.containsKey("announce-list")) {
            Object announceListObj = map.get("announce-list");
            if (announceListObj instanceof List) {
                List<?> announceRawList = (List<?>) announceListObj;
                List<List<String>> announceList = new ArrayList<>();
                
                for (Object tierObj : announceRawList) {
                    if (tierObj instanceof List) {
                        List<?> tierRawList = (List<?>) tierObj;
                        List<String> tier = new ArrayList<>();
                        
                        for (Object trackerObj : tierRawList) {
                            if (trackerObj instanceof String) {
                                tier.add((String) trackerObj);
                            } else if (trackerObj instanceof ByteBuffer) {
                                ByteBuffer buffer = (ByteBuffer) trackerObj;
                                tier.add(new String(buffer.array(), StandardCharsets.UTF_8));
                            }
                        }
                        
                        announceList.add(tier);
                    }
                }
                
                builder.announceList(announceList);
            }
        }
        
        if (map.containsKey("comment")) {
            Object commentObj = map.get("comment");
            if (commentObj instanceof String) {
                builder.comment((String) commentObj);
            } else if (commentObj instanceof ByteBuffer) {
                ByteBuffer buffer = (ByteBuffer) commentObj;
                builder.comment(new String(buffer.array(), StandardCharsets.UTF_8));
            }
        }
        
        if (map.containsKey("created by")) {
            Object createdByObj = map.get("created by");
            if (createdByObj instanceof String) {
                builder.createdBy((String) createdByObj);
            } else if (createdByObj instanceof ByteBuffer) {
                ByteBuffer buffer = (ByteBuffer) createdByObj;
                builder.createdBy(new String(buffer.array(), StandardCharsets.UTF_8));
            }
        }
        
        if (map.containsKey("creation date")) {
            Object creationDateObj = map.get("creation date");
            if (creationDateObj instanceof Number) {
                builder.creationDate(((Number) creationDateObj).longValue());
            }
        }
        
        if (map.containsKey("encoding")) {
            Object encodingObj = map.get("encoding");
            if (encodingObj instanceof String) {
                builder.encoding((String) encodingObj);
            } else if (encodingObj instanceof ByteBuffer) {
                ByteBuffer buffer = (ByteBuffer) encodingObj;
                builder.encoding(new String(buffer.array(), StandardCharsets.UTF_8));
            }
        }
        
        // Parse info section
        if (map.containsKey("info")) {
            Object infoObj = map.get("info");
            if (infoObj instanceof Map) {
                Map<String, Object> infoMap = (Map<String, Object>) infoObj;
                TorrentInfo info = TorrentInfo.fromMap(infoMap);
                builder.info(info);
            }
        }
        
        return builder.build();
    }
} 