package me.bihan.tracker;

import lombok.Builder;
import lombok.Value;
import me.bihan.torrent.TorrentInfo;

/**
 * Represents a request to a BitTorrent tracker.
 */
@Value
@Builder(toBuilder = true)
public class TrackerRequest {

    TorrentInfo torrentInfo;
    String peerId;
    @Builder.Default
    int port = 6881;
    @Builder.Default
    long uploaded = 0;
    @Builder.Default
    long downloaded = 0;
    @Builder.Default
    long left = 0;
    @Builder.Default
    boolean compact = true;
    @Builder.Default
    String event = "started";
    @Builder.Default
    int numwant = 200;
} 