package me.bihan.torrent;

import com.dampcake.bencode.Bencode;
import com.dampcake.bencode.Type;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Parses .torrent files using dampcake bencode library.
 * Implements correct infohash calculation following the Python reference approach.
 */
@Log4j2
public class TorrentParser {

    /**
     * Parses a torrent file from the given path.
     */
    public static Torrent parseTorrentFile(Path filePath) throws IOException {
        byte[] data = Files.readAllBytes(filePath);
        return parseTorrentData(data);
    }

    /**
     * Parses torrent data from byte array.
     */
    public static Torrent parseTorrentData(byte[] data) {
        try {
            Bencode bencode = new Bencode(true);

            // Decode the entire torrent file - this is like bencoding.bdecode() in Python
            Map<String, Object> torrentMap = bencode.decode(data, Type.DICTIONARY);

            // Debug: Calculate infohash directly from the decoded map
            {
              Map<String, Object> info = (Map<String, Object>) torrentMap.get("info");
              byte[] res = DigestUtils.sha1(bencode.encode(info));
              String hex = Hex.encodeHexString(res);
              log.info("SHA-1 Hash Hex from bencode map: {}", hex);
            }

            // Debug: Show types of decoded objects
            log.debug("Torrent map keys and types:");
            for (Map.Entry<String, Object> entry : torrentMap.entrySet()) {
                Object value = entry.getValue();
                String type = value != null ? value.getClass().getSimpleName() : "null";
                log.debug("  {} -> {}", entry.getKey(), type);
            }

            return Torrent.fromMap(torrentMap);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse torrent data", e);
        }
    }

 }