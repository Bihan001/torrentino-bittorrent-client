package me.bihan.service.impl;

import lombok.extern.log4j.Log4j2;
import me.bihan.service.TorrentParserService;
import me.bihan.torrent.*;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Implementation of TorrentParserService.
 * Follows Single Responsibility Principle.
 */
@Log4j2
public class TorrentParserServiceImpl implements TorrentParserService {

    private final Map<Path, Torrent> cacheMap = new HashMap<>();
    
    @Override
    public Torrent parseTorrentFile(Path filePath) throws Exception {
        if (cacheMap.containsKey(filePath)) {
            return cacheMap.get(filePath);
        }
        log.info("Parsing torrent file: {}", filePath);
        try {
            Torrent torrent = TorrentParser.parseTorrentFile(filePath);
            
            log.info("Parsed torrent: {} ({} bytes, {} pieces)", 
                    torrent.getInfo().getName(), 
                    torrent.getInfo().getTotalLength(),
                    torrent.getInfo().getNumPieces());

            cacheMap.put(filePath, torrent);

            return torrent;
        } catch (Exception e) {
            log.error("Failed to parse torrent file {}: {}", filePath, e.getMessage());
            throw new Exception("Failed to parse torrent file: " + e.getMessage(), e);
        }
    }
}
