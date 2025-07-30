package me.bihan.service;

/**
 * Interface for hash calculation operations.
 * Simplified to provide basic hash utilities.
 */
public interface HashCalculatorService {
    
    /**
     * Calculates SHA-1 hash of the given data.
     */
    byte[] sha1Hash(byte[] data);
    
}