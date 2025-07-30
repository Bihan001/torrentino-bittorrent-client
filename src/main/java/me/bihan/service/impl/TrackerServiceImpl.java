package me.bihan.service.impl;

import lombok.extern.log4j.Log4j2;
import me.bihan.service.TrackerService;
import me.bihan.tracker.*;

import java.io.IOException;
import java.util.List;

@Log4j2
public class TrackerServiceImpl implements TrackerService {
    
    private final List<TrackerClient> trackerClients;
    
    public TrackerServiceImpl(List<TrackerClient> trackerClients) {
        this.trackerClients = trackerClients;
    }
    
    @Override
    public TrackerResponse announce(String trackerUrl, TrackerRequest request) {
        TrackerClient client = getClientForUrl(trackerUrl);
        if (client == null) {
            return TrackerResponse.builder()
                    .failureReason("No suitable tracker client found for URL: " + trackerUrl)
                    .build();
        }
        
        try {
            return client.announce(trackerUrl, request);
        } catch (IOException e) {
            log.error("Failed to announce to tracker {}: {}", trackerUrl, e.getMessage());
            return TrackerResponse.builder()
                    .failureReason("Announce failed: " + e.getMessage())
                    .build();
        }
    }
    
    @Override
    public TrackerResponse announceCompleted(String trackerUrl, TrackerRequest request) {
        TrackerClient client = getClientForUrl(trackerUrl);
        if (client == null) {
            return TrackerResponse.builder()
                    .failureReason("No suitable tracker client found for URL: " + trackerUrl)
                    .build();
        }
        
        try {
            return client.announceCompleted(trackerUrl, request);
        } catch (IOException e) {
            log.error("Failed to announce completion to tracker {}: {}", trackerUrl, e.getMessage());
            return TrackerResponse.builder()
                    .failureReason("Announce completion failed: " + e.getMessage())
                    .build();
        }
    }
    
    @Override
    public TrackerResponse announceStopped(String trackerUrl, TrackerRequest request) {
        TrackerClient client = getClientForUrl(trackerUrl);
        if (client == null) {
            return TrackerResponse.builder()
                    .failureReason("No suitable tracker client found for URL: " + trackerUrl)
                    .build();
        }
        
        try {
            return client.announceStopped(trackerUrl, request);
        } catch (IOException e) {
            log.error("Failed to announce stop to tracker {}: {}", trackerUrl, e.getMessage());
            return TrackerResponse.builder()
                    .failureReason("Announce stop failed: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public TrackerResponse announceStarted(String trackerUrl, TrackerRequest request) throws Exception {
        TrackerClient client = getClientForUrl(trackerUrl);
        if (client == null) {
            return TrackerResponse.builder()
                    .failureReason("No suitable tracker client found for URL: " + trackerUrl)
                    .build();
        }

        try {
            return client.announceStarted(trackerUrl, request);
        } catch (IOException e) {
            log.error("Failed to announce start to tracker {}: {}", trackerUrl, e.getMessage());
            return TrackerResponse.builder()
                    .failureReason("Announce start failed: " + e.getMessage())
                    .build();
        }
    }

    private TrackerClient getClientForUrl(String trackerUrl) {
        return trackerClients.stream()
                .filter(client -> client.canHandle(trackerUrl))
                .findFirst()
                .orElse(null);
    }
} 