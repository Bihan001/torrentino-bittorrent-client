package me.bihan.tracker;

import java.io.IOException;

/**
 * Interface for tracker communication.
 * Handles announce requests to torrent trackers.
 */
public interface TrackerClient {
    
    /**
     * Sends an announce request to the tracker.
     * 
     * @param trackerUrl The tracker URL
     * @param request The tracker request
     * @return TrackerResponse containing peer list and other data
     * @throws IOException If network communication fails
     */
    TrackerResponse announce(String trackerUrl, TrackerRequest request) throws IOException;
    
    /**
     * Sends a completed announce request to the tracker.
     * This notifies the tracker that the download has completed.
     * 
     * @param trackerUrl The tracker URL
     * @param request The tracker request
     * @return TrackerResponse containing peer list and other data
     * @throws IOException If network communication fails
     */
    TrackerResponse announceCompleted(String trackerUrl, TrackerRequest request) throws IOException;
    
    /**
     * Sends a stopped announce request to the tracker.
     * This notifies the tracker that the client is stopping.
     * 
     * @param trackerUrl The tracker URL
     * @param request The tracker request
     * @return TrackerResponse containing peer list and other data
     * @throws IOException If network communication fails
     */
    TrackerResponse announceStopped(String trackerUrl, TrackerRequest request) throws IOException;


    TrackerResponse announceStarted(String trackerUrl, TrackerRequest request) throws  IOException;
    
    /**
     * Determines if this client can handle the given tracker URL.
     */
    boolean canHandle(String trackerUrl);
    
    /**
     * Gets the tracker URL scheme this client handles (e.g., "http", "udp").
     */
    String getScheme();
} 