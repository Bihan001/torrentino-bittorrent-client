package me.bihan.service.seeding;

import lombok.extern.log4j.Log4j2;
import me.bihan.factory.PeerMessageFactory;
import me.bihan.peer.Handshake;
import me.bihan.peer.PeerIdGenerator;
import me.bihan.peer.message.*;
import me.bihan.service.BitfieldManager;
import me.bihan.torrent.TorrentInfo;

import java.io.*;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.Callable;

/**
 * Worker thread that handles upload requests from a single peer.
 * Mirrors PeerWorker but for uploads instead of downloads.
 * Follows Single Responsibility Principle - handles one peer connection.
 */
@Log4j2
public class SeedingWorker implements Callable<Void> {
    
    private static final int SOCKET_TIMEOUT = 30000; // 30 seconds
    private static final int BLOCK_SIZE = 16 * 1024; // 16 KB
    
    private final Socket peerSocket;
    private final byte[] infoHash;
    private final TorrentInfo torrentInfo;
    private final String downloadDirectory;
    private final BitfieldManager bitfieldManager;
    private final TriConsumer<Integer, Long, String> onPieceUploaded;
    private final java.util.function.Consumer<String> onWorkerFinished;
    
    private final PeerMessageFactory messageFactory;
    private final byte[] peerId;
    private final String peerAddress;
    
    private boolean peerInterested = false;
    private boolean peerChoked = true;
    private volatile boolean interrupted = false;
    
    public SeedingWorker(Socket peerSocket, byte[] infoHash, TorrentInfo torrentInfo,
                        String downloadDirectory, BitfieldManager bitfieldManager,
                        TriConsumer<Integer, Long, String> onPieceUploaded,
                        java.util.function.Consumer<String> onWorkerFinished) {
        this.peerSocket = peerSocket;
        this.infoHash = infoHash;
        this.torrentInfo = torrentInfo;
        this.downloadDirectory = downloadDirectory;
        this.bitfieldManager = bitfieldManager;
        this.onPieceUploaded = onPieceUploaded;
        this.onWorkerFinished = onWorkerFinished;
        
        this.messageFactory = new PeerMessageFactory();
        this.peerId = PeerIdGenerator.generatePeerIdBytes();
        this.peerAddress = peerSocket.getRemoteSocketAddress().toString();
    }
    
    @Override
    public Void call() throws Exception {
        log.info("Seeding worker started for peer: {}", peerAddress);
        
        try {
            peerSocket.setSoTimeout(SOCKET_TIMEOUT);
            
            InputStream input = peerSocket.getInputStream();
            OutputStream output = peerSocket.getOutputStream();
            
            // Perform handshake
            performHandshake(input, output);
            
            // Send initial messages
            sendInitialMessages(output);
            
            // Handle peer messages
            handlePeerMessages(input, output);
            
        } catch (Exception e) {
            if (!interrupted) {
                log.warn("Seeding worker error for peer {}: {}", peerAddress, e.getMessage());
            }
        } finally {
            cleanup();
            onWorkerFinished.accept(peerAddress);
        }
        
        log.info("Seeding worker finished for peer: {}", peerAddress);
        return null;
    }
    
    /**
     * Perform BitTorrent handshake with peer.
     */
    private void performHandshake(InputStream input, OutputStream output) throws IOException {
        log.debug("Performing handshake with peer: {}", peerAddress);
        
        // Read handshake from peer
        byte[] handshakeData = new byte[68];
        int bytesRead = 0;
        while (bytesRead < 68) {
            int read = input.read(handshakeData, bytesRead, 68 - bytesRead);
            if (read == -1) {
                throw new IOException("Peer closed connection during handshake");
            }
            bytesRead += read;
        }
        
        // Validate handshake
        Handshake peerHandshake = Handshake.parse(handshakeData);
        if (!Arrays.equals(peerHandshake.getInfoHash(), infoHash)) {
            throw new IOException("Info hash mismatch in handshake");
        }
        
        // Send our handshake response
        Handshake ourHandshake = new Handshake(infoHash, peerId);
        output.write(ourHandshake.serialize());
        output.flush();
        
        log.debug("Handshake completed with peer: {}", peerAddress);
    }
    
    /**
     * Send initial messages to peer.
     */
    private void sendInitialMessages(OutputStream output) throws IOException {
        log.debug("Sending initial messages to peer: {}", peerAddress);
        
        // Send bitfield message
        BitfieldMessage bitfield = new BitfieldMessage(bitfieldManager.generateBitfieldPayload());
        output.write(bitfield.serialize());
        output.flush();
        
        log.debug("Sent bitfield to peer: {}", peerAddress);
    }
    
    /**
     * Handle incoming messages from peer.
     */
    private void handlePeerMessages(InputStream input, OutputStream output) throws Exception {
        log.debug("Starting message handling loop for peer: {}", peerAddress);
        
        while (!interrupted && !peerSocket.isClosed()) {
            try {
                PeerMessage message = readPeerMessage(input);
                
                if (message == null) {
                    // Keep-alive message, continue
                    continue;
                }
                
                handleMessage(message, output);
                
            } catch (IOException e) {
                if (!interrupted) {
                    log.debug("Connection error with peer {}: {}", peerAddress, e.getMessage());
                }
                break;
            }
        }
    }
    
    /**
     * Handle a specific peer message.
     */
    private void handleMessage(PeerMessage message, OutputStream output) throws Exception {
        log.debug("Handling message from peer {}: {}", peerAddress, message.getClass().getSimpleName());
        
        switch (message.getMessageType()) {
            case INTERESTED -> {
                peerInterested = true;
                log.debug("Peer {} is interested", peerAddress);
                
                // Send unchoke message
                UnchokeMessage unchoke = new UnchokeMessage();
                output.write(unchoke.serialize());
                output.flush();
                peerChoked = false;
                
                log.debug("Unchoked peer: {}", peerAddress);
            }
            
            case NOT_INTERESTED -> {
                peerInterested = false;
                log.debug("Peer {} is not interested", peerAddress);
            }
            
            case REQUEST -> {
                if (peerInterested && !peerChoked) {
                    handlePieceRequest((RequestMessage) message, output);
                } else {
                    log.debug("Ignoring request from choked/uninterested peer: {}", peerAddress);
                }
            }
            
            case CANCEL -> {
                // Handle cancel request - for now just log it
                CancelMessage cancel = (CancelMessage) message;
                log.debug("Peer {} cancelled request for piece {}", peerAddress, cancel.getIndex());
            }
            
            default -> {
                log.debug("Unhandled message type from peer {}: {}", 
                         peerAddress, message.getMessageType());
            }
        }
    }
    
    /**
     * Handle a piece request from peer.
     */
    private void handlePieceRequest(RequestMessage request, OutputStream output) throws Exception {
        int pieceIndex = request.getIndex();
        int begin = request.getBegin();
        int length = request.getLength();
        
        log.debug("Peer {} requested piece {}, begin={}, length={}", 
                 peerAddress, pieceIndex, begin, length);
        
        // Validate request
        if (!bitfieldManager.hasPiece(pieceIndex)) {
            log.warn("Peer {} requested piece {} that we don't have", peerAddress, pieceIndex);
            return;
        }
        
        if (length > BLOCK_SIZE) {
            log.warn("Peer {} requested block size {} > max {}", peerAddress, length, BLOCK_SIZE);
            return;
        }
        
        try {
            // Read piece data from disk
            byte[] pieceData = readPieceFromDisk(pieceIndex, begin, length);
            
            // Send piece message
            PieceMessage pieceMessage = new PieceMessage(pieceIndex, begin, pieceData);
            output.write(pieceMessage.serialize());
            output.flush();
            
            // Update statistics
            onPieceUploaded.accept(pieceIndex, (long) length, peerAddress);
            
            log.debug("Sent piece {} block (begin={}, length={}) to peer {}", 
                     pieceIndex, begin, length, peerAddress);
            
        } catch (IOException e) {
            log.error("Failed to read piece {} from disk for peer {}: {}", 
                     pieceIndex, peerAddress, e.getMessage());
        }
    }
    
    /**
     * Read piece data from disk.
     */
    private byte[] readPieceFromDisk(int pieceIndex, int begin, int length) throws IOException {
        // Calculate file position
        long pieceLength = torrentInfo.getPieceLength();
        long absoluteOffset = pieceIndex * pieceLength + begin;
        
        // For simplicity, assume single file torrent
        // In a real implementation, handle multi-file torrents
        Path filePath = Paths.get(downloadDirectory, torrentInfo.getName());
        
        try (RandomAccessFile file = new RandomAccessFile(filePath.toFile(), "r")) {
            file.seek(absoluteOffset);
            
            byte[] data = new byte[length];
            int bytesRead = file.read(data);
            
            if (bytesRead != length) {
                throw new IOException("Could not read full block: expected " + length + 
                                    ", got " + bytesRead);
            }
            
            return data;
        }
    }
    
    /**
     * Read a peer message from input stream.
     */
    private PeerMessage readPeerMessage(InputStream input) throws IOException {
        // Read message length
        byte[] lengthBytes = new byte[4];
        int bytesRead = 0;
        while (bytesRead < 4) {
            int read = input.read(lengthBytes, bytesRead, 4 - bytesRead);
            if (read == -1) {
                throw new IOException("Peer closed connection while reading message length");
            }
            bytesRead += read;
        }
        
        int messageLength = ByteBuffer.wrap(lengthBytes).getInt();
        
        if (messageLength == 0) {
            // Keep-alive message
            return null;
        }
        
        if (messageLength > 1024 * 1024) { // 1MB max
            throw new IOException("Message too large: " + messageLength);
        }
        
        // Read message data
        byte[] messageData = new byte[messageLength];
        bytesRead = 0;
        while (bytesRead < messageLength) {
            int read = input.read(messageData, bytesRead, messageLength - bytesRead);
            if (read == -1) {
                throw new IOException("Peer closed connection while reading message data");
            }
            bytesRead += read;
        }
        
        // Create full message for parsing
        byte[] fullMessage = new byte[4 + messageLength];
        System.arraycopy(lengthBytes, 0, fullMessage, 0, 4);
        System.arraycopy(messageData, 0, fullMessage, 4, messageLength);
        
        return messageFactory.parseMessage(fullMessage);
    }
    
    /**
     * Interrupt the worker.
     */
    public void interrupt() {
        interrupted = true;
    }
    
    /**
     * Clean up resources.
     */
    private void cleanup() {
        if (peerSocket != null && !peerSocket.isClosed()) {
            try {
                peerSocket.close();
            } catch (IOException e) {
                log.debug("Error closing socket: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Functional interface for three-parameter consumer.
     */
    @FunctionalInterface
    public interface TriConsumer<T, U, V> {
        void accept(T t, U u, V v);
    }
}