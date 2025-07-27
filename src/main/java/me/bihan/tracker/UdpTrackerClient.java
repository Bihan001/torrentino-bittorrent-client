package me.bihan.tracker;

import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * UDP tracker client implementation.
 * Handles communication with UDP-based BitTorrent trackers.
 */
@Log4j2
public class UdpTrackerClient implements TrackerClient {
    
    private static final int CONNECT_ACTION = 0;
    private static final int ANNOUNCE_ACTION = 1;
    private static final int CONNECT_REQUEST_SIZE = 16;
    private static final int CONNECT_RESPONSE_SIZE = 16;
    private static final int ANNOUNCE_REQUEST_SIZE = 98;
    private static final int PEER_SIZE = 6; // 4 bytes IP + 2 bytes port
    
    private final SecureRandom random = new SecureRandom();
    
    @Override
    public TrackerResponse announce(String trackerUrl, TrackerRequest request) throws IOException {
        return performAnnounce(trackerUrl, request, null);
    }
    
    @Override
    public TrackerResponse announceCompleted(String trackerUrl, TrackerRequest request) throws IOException {
        return performAnnounce(trackerUrl, request, "completed");
    }
    
    @Override
    public TrackerResponse announceStopped(String trackerUrl, TrackerRequest request) throws IOException {
        return performAnnounce(trackerUrl, request, "stopped");
    }

    @Override
    public TrackerResponse announceStarted(String trackerUrl, TrackerRequest request) throws IOException {
        return performAnnounce(trackerUrl, request, "started");
    }

    @Override
    public boolean canHandle(String trackerUrl) {
        return trackerUrl != null && trackerUrl.toLowerCase().startsWith("udp://");
    }
    
    @Override
    public String getScheme() {
        return "udp";
    }
    
    private TrackerResponse performAnnounce(String trackerUrl, TrackerRequest request, String event) 
        throws IOException {
        
        try {
            // Parse URL
            URI uri = URI.create(trackerUrl);
            InetAddress address = InetAddress.getByName(uri.getHost());
            int port = uri.getPort();
            
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setSoTimeout(15000); // 15 second timeout
                
                // Step 1: Connect to tracker
                long connectionId = connectToTracker(socket, address, port);
                
                // Step 2: Send announce request
                return sendAnnounceRequest(socket, address, port, connectionId, request, event);
            }
            
        } catch (Exception e) {
            log.error("UDP tracker communication failed: {}", e.getMessage());
            throw new IOException("UDP tracker communication failed", e);
        }
    }
    
    private long connectToTracker(DatagramSocket socket, InetAddress address, int port) throws IOException {
        // Create connect request
        ByteBuffer buffer = ByteBuffer.allocate(CONNECT_REQUEST_SIZE);
        buffer.putLong(0x41727101980L); // Connection ID magic constant
        buffer.putInt(CONNECT_ACTION);
        int transactionId = random.nextInt();
        buffer.putInt(transactionId);

        // Send connect request
        DatagramPacket request = new DatagramPacket(
            buffer.array(), CONNECT_REQUEST_SIZE, address, port);
        socket.send(request);
        
        // Receive connect response
        byte[] responseData = new byte[CONNECT_RESPONSE_SIZE];
        DatagramPacket response = new DatagramPacket(responseData, CONNECT_RESPONSE_SIZE);
        socket.receive(response);
        
        // Parse connect response
        ByteBuffer responseBuffer = ByteBuffer.wrap(responseData);
        long responseAction = responseBuffer.getInt();
        int responseTransactionId = responseBuffer.getInt();
        
        if (responseAction != CONNECT_ACTION) {
            throw new IOException("Invalid connect response action: " + responseAction);
        }
        
        if (responseTransactionId != transactionId) {
            throw new IOException("Connect response transaction ID mismatch");
        }
        
        return responseBuffer.getLong(); // Connection ID
    }
    
    private TrackerResponse sendAnnounceRequest(DatagramSocket socket, InetAddress address, int port,
                                               long connectionId, TrackerRequest request, String event) 
        throws IOException {
        
        // Create announce request
        ByteBuffer buffer = ByteBuffer.allocate(ANNOUNCE_REQUEST_SIZE);
        buffer.putLong(connectionId);
        buffer.putInt(ANNOUNCE_ACTION);
        int transactionId = random.nextInt();
        buffer.putInt(transactionId);
        
        // Info hash (20 bytes)
        byte[] infoHash = request.getTorrentInfo().getHash();
        buffer.put(infoHash);
        
        // Peer ID (20 bytes)
        buffer.put(request.getPeerId().getBytes());
        
        // Downloaded, left, uploaded
        buffer.putLong(request.getDownloaded());
        buffer.putLong(request.getLeft());
        buffer.putLong(request.getUploaded());
        
        // Event
        int eventValue = 0; // None
        if ("completed".equals(event)) {
            eventValue = 1;
        } else if ("started".equals(event)) {
            eventValue = 2;
        } else if ("stopped".equals(event)) {
            eventValue = 3;
        }
        buffer.putInt(eventValue);
        
        // IP address (0 = default)
        buffer.putInt(0);
        
        // Key (random)
        buffer.putInt(random.nextInt());
        
        // Num want
        buffer.putInt(request.getNumwant());
        
        // Port
        buffer.putShort((short) request.getPort());
        
        // Send announce request
        DatagramPacket announceRequest = new DatagramPacket(
            buffer.array(), ANNOUNCE_REQUEST_SIZE, address, port);
        socket.send(announceRequest);
        
        // Receive announce response
        byte[] responseData = new byte[1024]; // Variable size response
        DatagramPacket response = new DatagramPacket(responseData, responseData.length);
        socket.receive(response);
        
        return parseAnnounceResponse(responseData, response.getLength(), transactionId);
    }
    
    private TrackerResponse parseAnnounceResponse(byte[] data, int length, int expectedTransactionId) {
        if (length < 20) {
            throw new RuntimeException("UDP tracker response too short");
        }
        
        ByteBuffer buffer = ByteBuffer.wrap(data);
        
        int action = buffer.getInt();
        int transactionId = buffer.getInt();
        
        if (transactionId != expectedTransactionId) {
            throw new RuntimeException("UDP tracker response transaction ID mismatch");
        }
        
        if (action != ANNOUNCE_ACTION) {
            // Error response
            if (length >= 8) {
                byte[] errorMessage = new byte[length - 8];
                buffer.get(errorMessage);
                String error = new String(errorMessage);
                return TrackerResponse.builder()
                    .failureReason(error)
                    .build();
            } else {
                return TrackerResponse.builder()
                    .failureReason("Unknown UDP tracker error")
                    .build();
            }
        }
        
        // Parse successful announce response
        int interval = buffer.getInt();
        int leechers = buffer.getInt();
        int seeders = buffer.getInt();
        
        // Parse peers
        List<Peer> peers = new ArrayList<>();
        int remaining = length - 20; // 20 bytes for header + interval + leechers + seeders
        
        while (remaining >= PEER_SIZE) {
            // Read IP (4 bytes)
            byte[] ipBytes = new byte[4];
            buffer.get(ipBytes);
            
            // Read port (2 bytes)
            int port = buffer.getShort() & 0xFFFF;
            
            try {
                InetAddress ip = InetAddress.getByAddress(ipBytes);
                peers.add(new Peer(ip.getHostAddress(), port));
            } catch (Exception e) {
                log.warn("Failed to parse peer IP from UDP response", e);
            }
            
            remaining -= PEER_SIZE;
        }
        
        return TrackerResponse.builder()
            .interval(interval)
            .incomplete(leechers)
            .complete(seeders)
            .peers(peers)
            .build();
    }
} 