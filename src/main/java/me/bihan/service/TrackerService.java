package me.bihan.service;

import me.bihan.tracker.TrackerRequest;
import me.bihan.tracker.TrackerResponse;

/**
 * Interface for tracker communication following ISP.
 */
public interface TrackerService {
    
    /**
     * Announces to tracker and retrieves peer list.
     */
    TrackerResponse announce(String trackerUrl, TrackerRequest request) throws Exception;
    
    /**
     * Sends a completed event to tracker.
     */
    TrackerResponse announceCompleted(String trackerUrl, TrackerRequest request) throws Exception;
    
    /**
     * Sends a stopped event to tracker.
     */
    TrackerResponse announceStopped(String trackerUrl, TrackerRequest request) throws Exception;


    TrackerResponse announceStarted(String trackerUrl, TrackerRequest request) throws Exception;
} 