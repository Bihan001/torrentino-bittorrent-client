package me.bihan.tracker;

import lombok.Builder;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a response from a BitTorrent tracker.
 */
@Value
@Builder
public class TrackerResponse {
    
    String failureReason;
    String warningMessage;
    @Builder.Default
    int interval = 1800; // Default 30 minutes
    @Builder.Default
    int minInterval = 300; // Default 5 minutes
    String trackerId;
    @Builder.Default
    int complete = 0;
    @Builder.Default
    int incomplete = 0;
    @Builder.Default
    List<Peer> peers = new ArrayList<>();
    
    public boolean isSuccess() {
        return failureReason == null;
    }
} 