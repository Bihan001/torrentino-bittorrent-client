package me.bihan.service.impl;

import lombok.extern.log4j.Log4j2;
import me.bihan.service.HashCalculatorService;
import org.apache.commons.codec.digest.DigestUtils;

/**
 * Implementation of HashCalculatorService.
 * Provides basic hash calculation utilities.
 */
@Log4j2
public class HashCalculatorServiceImpl implements HashCalculatorService {
    
    @Override
    public byte[] sha1Hash(byte[] data) {
        return DigestUtils.sha1(data);
    }

} 