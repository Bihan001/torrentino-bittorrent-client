package me.bihan.tracker;

import com.dampcake.bencode.Bencode;
import com.dampcake.bencode.Type;
import lombok.extern.log4j.Log4j2;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * HTTP/HTTPS tracker client implementation.
 * Handles communication with HTTP-based BitTorrent trackers.
 */
@Log4j2
public class HttpTrackerClient implements TrackerClient {
    
    private final CloseableHttpClient httpClient;
    
    public HttpTrackerClient() {
        this.httpClient = HttpClients.createDefault();
    }
    
    public HttpTrackerClient(CloseableHttpClient httpClient) {
        this.httpClient = httpClient;
    }
    
    @Override
    public TrackerResponse announce(String trackerUrl, TrackerRequest request) throws IOException {
        return sendTrackerRequest(trackerUrl, request);
    }
    
    @Override
    public TrackerResponse announceCompleted(String trackerUrl, TrackerRequest request) throws IOException {
        TrackerRequest completedRequest = TrackerRequest.builder()
                .torrentInfo(request.getTorrentInfo())
                .peerId(request.getPeerId())
                .port(request.getPort())
                .uploaded(request.getUploaded())
                .downloaded(request.getDownloaded())
                .left(0) // Download completed
                .event("completed")
                .compact(request.isCompact())
                .numwant(request.getNumwant())
                .build();
        
        return sendTrackerRequest(trackerUrl, completedRequest);
    }
    
    @Override
    public TrackerResponse announceStopped(String trackerUrl, TrackerRequest request) throws IOException {
        TrackerRequest stoppedRequest = TrackerRequest.builder()
                .torrentInfo(request.getTorrentInfo())
                .peerId(request.getPeerId())
                .port(request.getPort())
                .uploaded(request.getUploaded())
                .downloaded(request.getDownloaded())
                .left(request.getLeft())
                .event("stopped")
                .compact(request.isCompact())
                .numwant(0) // Don't need peers when stopping
                .build();
        
        return sendTrackerRequest(trackerUrl, stoppedRequest);
    }

    @Override
    public TrackerResponse announceStarted(String trackerUrl, TrackerRequest request) throws IOException {
        TrackerRequest stoppedRequest = TrackerRequest.builder()
                .torrentInfo(request.getTorrentInfo())
                .peerId(request.getPeerId())
                .port(request.getPort())
                .uploaded(request.getUploaded())
                .downloaded(request.getDownloaded())
                .left(request.getLeft())
                .event("started")
                .compact(request.isCompact())
                .numwant(0) // Don't need peers when stopping
                .build();

        return sendTrackerRequest(trackerUrl, stoppedRequest);
    }

    @Override
    public boolean canHandle(String trackerUrl) {
        if (trackerUrl == null) {
            return false;
        }
        String scheme = trackerUrl.toLowerCase();
        return scheme.startsWith("http://") || scheme.startsWith("https://");
    }
    
    @Override
    public String getScheme() {
        return "http";
    }
    
    public void close() throws IOException {
        if (httpClient != null) {
            httpClient.close();
        }
    }
    
    private TrackerResponse sendTrackerRequest(String trackerUrl, TrackerRequest request) throws IOException {
        String url = buildTrackerUrl(trackerUrl, request);
        
        log.debug("Sending HTTP tracker request: {}", url);
        
        HttpGet httpGet = new HttpGet(url);
        httpGet.setHeader("User-Agent", "bittorrent-client-java/2.0");
        
        try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
            HttpEntity entity = response.getEntity();
            if (entity == null) {
                throw new IOException("Empty response from tracker");
            }
            
            byte[] responseData = EntityUtils.toByteArray(entity);
            
            // Log raw response for debugging
            if (log.isDebugEnabled()) {
                log.debug("Raw HTTP tracker response ({} bytes): {}", 
                         responseData.length, 
                         new String(responseData, StandardCharsets.ISO_8859_1));
            }
            
            return parseTrackerResponse(responseData);
        }
    }
    
    private String buildTrackerUrl(String baseUrl, TrackerRequest request) {
        StringBuilder url = new StringBuilder(baseUrl);
        
        // Add separator (? or &)
        url.append(baseUrl.contains("?") ? "&" : "?");
        
        // Add required parameters
        url.append("info_hash=").append(request.getTorrentInfo().getHashUrlEncoded());
        url.append("&peer_id=").append(urlEncode(request.getPeerId()));
        url.append("&port=").append(request.getPort());
        url.append("&uploaded=").append(request.getUploaded());
        url.append("&downloaded=").append(request.getDownloaded());
        url.append("&left=").append(request.getLeft());
        url.append("&compact=").append(request.isCompact() ? "1" : "0");
        
        if (request.getEvent() != null) {
            url.append("&event=").append(urlEncode(request.getEvent()));
        }
        
        url.append("&numwant=").append(request.getNumwant());
        
        return url.toString();
    }
    
    @SuppressWarnings("unchecked")
    private TrackerResponse parseTrackerResponse(byte[] data) {
        try {
            Bencode bencode = new Bencode(true);
            Map<String, Object> responseMap = bencode.decode(data, Type.DICTIONARY);
        
        log.debug("HTTP tracker response keys: {}", responseMap.keySet());
        log.debug("HTTP tracker response: complete={}, incomplete={}, interval={}", 
                 responseMap.get("complete"), responseMap.get("incomplete"), responseMap.get("interval"));
        
        TrackerResponse.TrackerResponseBuilder builder = TrackerResponse.builder();
        
        // Check for failure
        if (responseMap.containsKey("failure reason")) {
            String failureReason = (String) responseMap.get("failure reason");
            return builder.failureReason(failureReason).build();
        }
        
        // Parse success response
        if (responseMap.containsKey("warning message")) {
            builder.warningMessage((String) responseMap.get("warning message"));
        }
        
        if (responseMap.containsKey("interval")) {
            builder.interval(((Long) responseMap.get("interval")).intValue());
        }
        
        if (responseMap.containsKey("min interval")) {
            builder.minInterval(((Long) responseMap.get("min interval")).intValue());
        }
        
        if (responseMap.containsKey("tracker id")) {
            builder.trackerId((String) responseMap.get("tracker id"));
        }
        
        if (responseMap.containsKey("complete")) {
            builder.complete(((Long) responseMap.get("complete")).intValue());
        }
        
        if (responseMap.containsKey("incomplete")) {
            builder.incomplete(((Long) responseMap.get("incomplete")).intValue());
        }
        
        // Parse peers
        if (responseMap.containsKey("peers")) {
            Object peersData = responseMap.get("peers");
            log.debug("Found peers key in HTTP tracker response. Peers data type: {}, value: {}", 
                     peersData != null ? peersData.getClass().getSimpleName() : "null", peersData);
            List<Peer> peers = parsePeers(peersData);
            builder.peers(peers);
        } else {
            log.warn("No 'peers' key found in HTTP tracker response. Available keys: {}", responseMap.keySet());
            builder.peers(new ArrayList<>()); // Explicitly set empty list
        }
        
        return builder.build();
        
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse tracker response", e);
        }
    }
    
    @SuppressWarnings("unchecked")
    private List<Peer> parsePeers(Object peersData) {
        List<Peer> peers = new ArrayList<>();
        
        if (peersData == null) {
            log.warn("Peers data is null");
            return peers;
        }
        
        log.debug("Parsing peers data of type: {}", peersData.getClass().getSimpleName());
        
        if (peersData instanceof String) {
            // Compact format: binary string with 6 bytes per peer (4 for IP, 2 for port)
            String compactPeers = (String) peersData;
            byte[] peerBytes = compactPeers.getBytes(StandardCharsets.ISO_8859_1);
            
            log.debug("Compact peers format: {} bytes total", peerBytes.length);
            
            if (peerBytes.length % 6 != 0) {
                log.warn("Invalid compact peers format: length {} is not divisible by 6", peerBytes.length);
            }
            
            for (int i = 0; i < peerBytes.length; i += 6) {
                if (i + 5 < peerBytes.length) {
                    try {
                        // Extract IP address (4 bytes)
                        int ip1 = peerBytes[i] & 0xFF;
                        int ip2 = peerBytes[i + 1] & 0xFF;
                        int ip3 = peerBytes[i + 2] & 0xFF;
                        int ip4 = peerBytes[i + 3] & 0xFF;
                        String ip = ip1 + "." + ip2 + "." + ip3 + "." + ip4;
                        
                        // Extract port (2 bytes, big-endian)
                        int port = ((peerBytes[i + 4] & 0xFF) << 8) | (peerBytes[i + 5] & 0xFF);
                        
                        if (port > 0 && port < 65536 && !ip.equals("0.0.0.0")) {
                            Peer peer = Peer.builder().ip(ip).port(port).build();
                            peers.add(peer);
                            log.debug("Parsed peer: {}:{}", ip, port);
                        } else {
                            log.debug("Skipping invalid peer: {}:{}", ip, port);
                        }
                    } catch (Exception e) {
                        log.warn("Error parsing peer at offset {}: {}", i, e.getMessage());
                    }
                }
            }
        } else if (peersData instanceof List) {
            // Dictionary format: list of dictionaries with ip, port, and peer id
            List<Map<String, Object>> peerList = (List<Map<String, Object>>) peersData;
            
            log.debug("Dictionary peers format: {} peer entries", peerList.size());
            
            for (int i = 0; i < peerList.size(); i++) {
                try {
                    Map<String, Object> peerMap = peerList.get(i);
                    String ip = (String) peerMap.get("ip");
                    Object portObj = peerMap.get("port");
                    String peerId = (String) peerMap.get("peer id");
                    
                    if (ip == null || portObj == null) {
                        log.warn("Peer {} missing required fields: ip={}, port={}", i, ip, portObj);
                        continue;
                    }
                    
                    int port = (portObj instanceof Long) ? ((Long) portObj).intValue() : (Integer) portObj;
                    
                    if (port > 0 && port < 65536) {
                        Peer peer = Peer.builder().ip(ip).port(port).peerId(peerId).build();
                        peers.add(peer);
                        log.debug("Parsed peer: {}:{} ({})", ip, port, peerId != null ? peerId : "no-id");
                    } else {
                        log.debug("Skipping invalid peer: {}:{}", ip, port);
                    }
                } catch (Exception e) {
                    log.warn("Error parsing peer {}: {}", i, e.getMessage());
                }
            }
        } else if (peersData instanceof ByteBuffer) {
            // Extended format: byte array with 6 bytes per peer (4 for IP, 2 for port)
            ByteBuffer extendedPeers = (ByteBuffer) peersData;
            for (int i = 0; i < extendedPeers.capacity(); i += 6) {
                if (i + 5 < extendedPeers.capacity()) {
                    try {
                        // Extract IP address (4 bytes)
                        int ip1 = extendedPeers.get(i) & 0xFF;
                        int ip2 = extendedPeers.get(i + 1) & 0xFF;
                        int ip3 = extendedPeers.get(i + 2) & 0xFF;
                        int ip4 = extendedPeers.get(i + 3) & 0xFF;
                        String ip = ip1 + "." + ip2 + "." + ip3 + "." + ip4;
                        
                        // Extract port (2 bytes, big-endian)
                        int port = ((extendedPeers.get(i + 4) & 0xFF) << 8) | (extendedPeers.get(i + 5) & 0xFF);
                        
                        if (port > 0 && port < 65536 && !ip.equals("0.0.0.0")) {
                            Peer peer = Peer.builder().ip(ip).port(port).build();
                            peers.add(peer);
                            log.debug("Parsed peer: {}:{}", ip, port);
                        } else {
                            log.debug("Skipping invalid peer: {}:{}", ip, port);
                        }
                    } catch (Exception e) {
                        log.warn("Error parsing peer at offset {}: {}", i, e.getMessage());
                    }
                }
            }
        } else {
            log.warn("Unknown peers data format: {}. Expected String (compact) or List (dictionary)", 
                    peersData.getClass().getSimpleName());
        }
        
        log.info("Parsed {} peers from HTTP tracker response", peers.size());
        return peers;
    }
    
    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
} 