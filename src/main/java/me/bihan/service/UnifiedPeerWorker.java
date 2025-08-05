package me.bihan.service;

import lombok.extern.log4j.Log4j2;
import me.bihan.factory.MessageFactory;
import me.bihan.factory.PeerMessageFactory;
import me.bihan.peer.Handshake;
import me.bihan.peer.PeerIdGenerator;
import me.bihan.peer.message.*;
import me.bihan.service.strategy.FileManager;
import me.bihan.service.strategy.PieceInfo;
import me.bihan.torrent.TorrentInfo;
import me.bihan.tracker.Peer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;

/**
 * Unified peer worker that downloads pieces and immediately makes them available for seeding.
 * Uses the unified PieceManager for single state management.
 * Simplified version that eliminates the dual state management problem.
 */
@Log4j2
public class UnifiedPeerWorker implements Callable<Void> {

    private static final int BLOCK_SIZE = 16 * 1024; // 16 KB
    private static final int SOCKET_TIMEOUT = 30000; // 30 seconds
    private static final int MAX_PEER_FAILURES = 3;
    private static final long PEER_RETRY_DELAY = 5000; // 5 seconds

    private final int workerId;
    private final List<Peer> peers;
    private final byte[] infoHash;
    private final PieceManager pieceManager;
    private final FileManager fileManager;
    private final HashCalculatorService hashService;
    private final BiConsumer<Integer, Long> onPieceCompleted;
    private final TorrentInfo torrentInfo;

    private final MessageFactory messageFactory;
    private final byte[] peerId;

    public UnifiedPeerWorker(int workerId, List<Peer> peers, byte[] infoHash, 
                            PieceManager pieceManager, FileManager fileManager,
                            HashCalculatorService hashService,
                            BiConsumer<Integer, Long> onPieceCompleted, TorrentInfo torrentInfo) {
        this.workerId = workerId;
        this.peers = peers;
        this.infoHash = infoHash;
        this.pieceManager = pieceManager;
        this.fileManager = fileManager;
        this.hashService = hashService;
        this.onPieceCompleted = onPieceCompleted;
        this.torrentInfo = torrentInfo;
        
        this.messageFactory = new PeerMessageFactory();
        this.peerId = PeerIdGenerator.generatePeerIdBytes();
    }

    @Override
    public Void call() {
        log.info("Unified worker {} started", workerId);

        try {
            if (peers.isEmpty()) {
                log.error("Worker {} has no peers available", workerId);
                return null;
            }
            
            int peerIndex = workerId % peers.size();
            int peerFailures = 0;

            while (!pieceManager.isDownloadComplete() && !Thread.currentThread().isInterrupted()) {
                Peer currentPeer = peers.get(peerIndex);
                
                try {
                    downloadFromPeer(currentPeer);
                    peerFailures = 0;
                    
                } catch (Exception e) {
                    log.warn("Worker {} failed to download from peer {}: {}", 
                             workerId, currentPeer.getAddress(), e.getMessage());
                    
                    peerFailures++;
                    if (peerFailures >= MAX_PEER_FAILURES) {
                        log.warn("Worker {} reached max failures for peer {}, switching peer", 
                                 workerId, currentPeer.getAddress());
                        peerIndex = (peerIndex + 1) % peers.size();
                        peerFailures = 0;
                    }
                    
                    Thread.sleep(PEER_RETRY_DELAY);
                }
            }

        } catch (InterruptedException e) {
            log.info("Worker {} interrupted", workerId);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.error("Worker {} failed with error: {}", workerId, e.getMessage(), e);
            throw e;
        }

        log.info("Unified worker {} finished", workerId);
        return null;
    }

    private void downloadFromPeer(Peer peer) throws Exception {
        log.debug("Worker {} connecting to peer: {}", workerId, peer.getAddress());

        try (Socket socket = new Socket()) {
            socket.setSoTimeout(SOCKET_TIMEOUT);
            socket.connect(new InetSocketAddress(peer.getIp(), peer.getPort()), SOCKET_TIMEOUT);
            
            InputStream input = socket.getInputStream();
            OutputStream output = socket.getOutputStream();
            
            performHandshake(input, output, peer);
            exchangeInitialMessages(input, output, peer);
            downloadPiecesFromPeer(input, output, peer);
        }
    }

    private void performHandshake(InputStream input, OutputStream output, Peer peer) throws IOException {
        log.debug("Worker {} performing handshake with {}", workerId, peer.getAddress());
        
        Handshake handshake = new Handshake(infoHash, peerId);
        output.write(handshake.serialize());
        output.flush();
        
        byte[] handshakeResponse = new byte[68];
        int bytesRead = 0;
        while (bytesRead < 68) {
            int read = input.read(handshakeResponse, bytesRead, 68 - bytesRead);
            if (read == -1) {
                throw new IOException("Peer closed connection during handshake");
            }
            bytesRead += read;
        }
        
        Handshake responseHandshake = Handshake.parse(handshakeResponse);
        if (!Arrays.equals(responseHandshake.getInfoHash(), infoHash)) {
            throw new IOException("Info hash mismatch in handshake response");
        }
        
        log.debug("Worker {} handshake successful with {}", workerId, peer.getAddress());
    }

    private void exchangeInitialMessages(InputStream input, OutputStream output, Peer peer) throws IOException {
        log.debug("Worker {} exchanging initial messages with {}", workerId, peer.getAddress());
        
        // Wait for bitfield message
        while (true) {
            PeerMessage message = readPeerMessage(input);
            
            if (message instanceof BitfieldMessage) {
                log.debug("Worker {} received bitfield from {}", workerId, peer.getAddress());
                break;
            } else if (message != null) {
                log.debug("Worker {} received unexpected message type: {}, continuing to wait for bitfield", 
                         workerId, message.getClass().getSimpleName());
            }
        }
        
        // Send interested message
        log.debug("Worker {} sending interested message to {}", workerId, peer.getAddress());
        InterestedMessage interested = new InterestedMessage();
        output.write(interested.serialize());
        output.flush();
        
        // Wait for unchoke message
        while (true) {
            PeerMessage message = readPeerMessage(input);
            
            if (message instanceof UnchokeMessage) {
                log.debug("Worker {} received unchoke from {}", workerId, peer.getAddress());
                break;
            } else if (message instanceof ChokeMessage) {
                log.debug("Worker {} received choke from {}, continuing to wait for unchoke", workerId, peer.getAddress());
            } else if (message != null) {
                log.debug("Worker {} received unexpected message type: {}, continuing to wait for unchoke", 
                         workerId, message.getClass().getSimpleName());
            }
        }
        
        log.debug("Worker {} successfully completed initial message exchange with {}", workerId, peer.getAddress());
    }

    private void downloadPiecesFromPeer(InputStream input, OutputStream output, Peer peer) throws Exception {
        log.debug("Worker {} starting piece download from {}", workerId, peer.getAddress());
        
        while (!Thread.currentThread().isInterrupted()) {
            if (pieceManager.isDownloadComplete()) {
                log.debug("Worker {} detected download completion, exiting download loop", workerId);
                break;
            }
            
            PieceInfo pieceInfo = pieceManager.getNextPieceToDownload();
            if (pieceInfo == null) {
                log.debug("Worker {} received null piece from manager, exiting download loop", workerId);
                break;
            }
            
            try {
                byte[] pieceData = downloadPieceFollowingProtocol(pieceInfo, input, output, peer);
                
                // Verify piece integrity
                byte[] expectedHash = getPieceHash(pieceInfo.getIndex());
                byte[] actualHash = hashService.sha1Hash(pieceData);
                
                if (!Arrays.equals(expectedHash, actualHash)) {
                    log.warn("Worker {} piece {} hash mismatch from {}, retrying", 
                             workerId, pieceInfo.getIndex(), peer.getAddress());
                    pieceManager.returnPieceForRetry(pieceInfo);
                    continue;
                }
                
                // Write piece to disk
                fileManager.writePiece(pieceInfo.getIndex(), pieceData);
                
                // Mark as completed in piece manager - this makes it immediately available for seeding
                pieceManager.markPieceCompleted(pieceInfo.getIndex());
                
                // Notify completion callback
                onPieceCompleted.accept(pieceInfo.getIndex(), pieceInfo.getLength());
                
                log.info("Worker {} completed piece {} from {} ({} bytes) - immediately available for seeding", 
                         workerId, pieceInfo.getIndex(), peer.getAddress(), pieceData.length);
                
            } catch (Exception e) {
                log.warn("Worker {} failed to download piece {} from {}: {}", 
                         workerId, pieceInfo.getIndex(), peer.getAddress(), e.getMessage());
                
                pieceManager.returnPieceForRetry(pieceInfo);
                
                if (e instanceof IOException) {
                    throw e;
                }
            }
        }
        
        log.debug("Worker {} finished downloading pieces from {}", workerId, peer.getAddress());
    }

    private byte[] downloadPieceFollowingProtocol(PieceInfo pieceInfo, InputStream input, OutputStream output, Peer peer) 
            throws IOException {
        
        int pieceIndex = pieceInfo.getIndex();
        long pieceLength = pieceInfo.getLength();
        
        log.debug("Worker {} downloading piece {} from {} ({} bytes)", 
                 workerId, pieceIndex, peer.getAddress(), pieceLength);
        
        int numBlocks = (int) Math.ceil((double) pieceLength / BLOCK_SIZE);
        byte[] pieceData = new byte[(int) pieceLength];
        int pieceOffset = 0;
        
        for (int blockIndex = 0; blockIndex < numBlocks; blockIndex++) {
            int begin = blockIndex * BLOCK_SIZE;
            int blockLength = (int) Math.min(BLOCK_SIZE, pieceLength - begin);
            
            log.debug("Worker {} requesting block {} of piece {}: begin={}, length={}", 
                     workerId, blockIndex + 1, pieceIndex, begin, blockLength);
            
            RequestMessage request = new RequestMessage(pieceIndex, begin, blockLength);
            output.write(request.serialize());
            output.flush();
            
            PeerMessage responseMessage = readPeerMessage(input);
            
            if (!(responseMessage instanceof PieceMessage)) {
                throw new IOException("Expected piece message (ID 7), got: " + 
                                    (responseMessage != null ? responseMessage.getClass().getSimpleName() : "null"));
            }
            
            PieceMessage pieceMessage = (PieceMessage) responseMessage;
            
            if (pieceMessage.getIndex() != pieceIndex) {
                throw new IOException("Piece message index mismatch: expected " + pieceIndex + 
                                    ", got " + pieceMessage.getIndex());
            }
            
            if (pieceMessage.getBegin() != begin) {
                throw new IOException("Piece message begin mismatch: expected " + begin + 
                                    ", got " + pieceMessage.getBegin());
            }
            
            byte[] blockData = pieceMessage.getBlock();
            if (blockData.length != blockLength) {
                throw new IOException("Block length mismatch: expected " + blockLength + 
                                    ", got " + blockData.length);
            }
            
            System.arraycopy(blockData, 0, pieceData, pieceOffset, blockData.length);
            pieceOffset += blockData.length;
            
            log.debug("Worker {} received block {} of piece {} ({} bytes)", 
                     workerId, blockIndex + 1, pieceIndex, blockData.length);
        }
        
        log.debug("Worker {} completed downloading piece {} ({} bytes total)", 
                 workerId, pieceIndex, pieceData.length);
        
        return pieceData;
    }

    private PeerMessage readPeerMessage(InputStream input) throws IOException {
        // Read message length (4 bytes)
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
            return null; // Keep-alive message
        }
        
        if (messageLength > 1024 * 1024) {
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
        
        // Create full message with length prefix for parsing
        byte[] fullMessage = new byte[4 + messageLength];
        System.arraycopy(lengthBytes, 0, fullMessage, 0, 4);
        System.arraycopy(messageData, 0, fullMessage, 4, messageLength);
        
        return messageFactory.parseMessage(fullMessage);
    }

    private byte[] getPieceHash(int pieceIndex) {
        return torrentInfo.getPieceHash(pieceIndex);
    }
}