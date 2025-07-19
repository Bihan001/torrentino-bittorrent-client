package me.bihan.torrent;

import lombok.Value;
import lombok.Builder;
import lombok.extern.jackson.Jacksonized;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a file in a multi-file torrent.
 */
@Value
@Builder
@Jacksonized
public class TorrentFile {
    Long length;
    List<String> path;

    /**
     * Gets the full file path as a string.
     */
    public String getFullPath() {
        if (path == null || path.isEmpty()) {
            return "";
        }
        return String.join("/", path);
    }
    
    /**
     * Converts this TorrentFile to a Map with proper bencode field names.
     */
    public Map<String, Object> toBencodeMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        
        if (length != null) {
            map.put("length", length);
        }
        
        if (path != null) {
            map.put("path", path);
        }
        
        return map;
    }
    
    /**
     * Creates a TorrentFile from a Map (decoded from bencode).
     * Handles ByteBuffer objects returned by new Bencode(true).
     */
    @SuppressWarnings("unchecked")
    public static TorrentFile fromMap(Map<String, Object> map) {
        TorrentFileBuilder builder = TorrentFile.builder();
        
        if (map.containsKey("length")) {
            Object lengthObj = map.get("length");
            if (lengthObj instanceof Number) {
                builder.length(((Number) lengthObj).longValue());
            }
        }
        
        if (map.containsKey("path")) {
            Object pathObj = map.get("path");
            if (pathObj instanceof List) {
                List<?> pathRawList = (List<?>) pathObj;
                List<String> pathList = new java.util.ArrayList<>();
                
                for (Object pathElement : pathRawList) {
                    if (pathElement instanceof String) {
                        pathList.add((String) pathElement);
                    } else if (pathElement instanceof ByteBuffer) {
                        // Convert ByteBuffer to String using UTF-8 for path components
                        ByteBuffer buffer = (ByteBuffer) pathElement;
                        pathList.add(new String(buffer.array(), StandardCharsets.UTF_8));
                    }
                }
                
                builder.path(pathList);
            }
        }
        
        return builder.build();
    }
} 