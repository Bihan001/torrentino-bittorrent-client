package me.bihan.service;

import me.bihan.torrent.Torrent;

import java.nio.file.Path;

/**
 * Interface for torrent file parsing operations.
 * Simplified to work directly with Torrent objects.
 */
public interface TorrentParserService {
    
    /**
     * Parses a torrent file from the given path.
     */
    Torrent parseTorrentFile(Path filePath) throws Exception;
}