package me.bihan.service.strategy;

import lombok.extern.log4j.Log4j2;
import me.bihan.torrent.TorrentFile;
import me.bihan.torrent.TorrentInfo;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages file allocation, reading, and writing for torrent downloads.
 * Handles both single-file and multi-file torrents with proper disk-based storage.
 */
@Log4j2
public class FileManager {

    private final TorrentInfo torrentInfo;
    private final String downloadDirectory;
    private final List<TorrentFileInfo> fileInfos;

    /**
     * Internal class to track file information and channels.
     */
    private static class TorrentFileInfo {
        final String filePath;
        final long length;
        final long startOffset;  // Offset within the entire torrent data
        RandomAccessFile randomAccessFile;
        FileChannel channel;

        TorrentFileInfo(String filePath, long length, long startOffset) {
            this.filePath = filePath;
            this.length = length;
            this.startOffset = startOffset;
        }

        void close() throws IOException {
            if (channel != null) {
                channel.close();
            }
            if (randomAccessFile != null) {
                randomAccessFile.close();
            }
        }
    }

    public FileManager(TorrentInfo torrentInfo, String downloadDirectory) {
        this.torrentInfo = torrentInfo;
        this.downloadDirectory = downloadDirectory;
        this.fileInfos = new ArrayList<>();
        buildFileInfos();
    }

    /**
     * Build internal file information structure.
     */
    private void buildFileInfos() {
        long currentOffset = 0;

        if (torrentInfo.getLength() != null) {
            // Single file torrent
            String fileName = torrentInfo.getName();
            String filePath = Paths.get(downloadDirectory, fileName).toString();
            fileInfos.add(new TorrentFileInfo(filePath, torrentInfo.getLength(), currentOffset));
        } else if (torrentInfo.getFiles() != null) {
            // Multi-file torrent
            String torrentName = torrentInfo.getName();

            for (TorrentFile file : torrentInfo.getFiles()) {
                String fileName = file.getFullPath();
                String filePath = Paths.get(downloadDirectory, torrentName, fileName).toString();
                long fileLength = file.getLength() != null ? file.getLength() : 0;

                fileInfos.add(new TorrentFileInfo(filePath, fileLength, currentOffset));
                currentOffset += fileLength;
            }
        }

        log.info("Built file structure: {} files, total size: {} bytes",
                fileInfos.size(), currentOffset);
    }

    /**
     * Allocate files on disk with proper directory structure.
     */
    public void allocateFiles() throws IOException {
        log.info("Allocating files for torrent: {}", torrentInfo.getName());

        for (TorrentFileInfo fileInfo : fileInfos) {
            Path filePath = Paths.get(fileInfo.filePath);

            // Create parent directories if they don't exist
            Path parentDir = filePath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
                log.debug("Created directory: {}", parentDir);
            }

            // Create file if it doesn't exist or if it's smaller than expected
            if (!Files.exists(filePath) || Files.size(filePath) < fileInfo.length) {
                // Create/open file for writing
                fileInfo.randomAccessFile = new RandomAccessFile(fileInfo.filePath, "rw");
                fileInfo.channel = fileInfo.randomAccessFile.getChannel();

                // Set file length (allocate space)
                fileInfo.randomAccessFile.setLength(fileInfo.length);

                log.info("Allocated file: {} ({} bytes)", filePath.getFileName(), fileInfo.length);
            } else {
                // File exists with correct size, open for read/write
                fileInfo.randomAccessFile = new RandomAccessFile(fileInfo.filePath, "rw");
                fileInfo.channel = fileInfo.randomAccessFile.getChannel();

                log.debug("Opened existing file: {} ({} bytes)", filePath.getFileName(), fileInfo.length);
            }
        }

        log.info("File allocation completed for {} files", fileInfos.size());
    }

    /**
     * Write a piece to the appropriate file(s) on disk.
     */
    public synchronized void writePiece(int pieceIndex, byte[] pieceData) throws IOException {
        long pieceOffset = (long) pieceIndex * torrentInfo.getPieceLength();
        long remainingData = pieceData.length;
        long dataOffset = 0;

        log.debug("Writing piece {} at offset {} ({} bytes)", pieceIndex, pieceOffset, pieceData.length);

        for (TorrentFileInfo fileInfo : fileInfos) {
            if (pieceOffset >= (fileInfo.startOffset + fileInfo.length)) {
                // This piece starts after this file
                continue;
            }

            if ((pieceOffset + pieceData.length) <= fileInfo.startOffset) {
                // This piece ends before this file
                break;
            }

            // Calculate overlap with this file
            long fileStartInPiece = Math.max(0, fileInfo.startOffset - pieceOffset);
            long fileEndInPiece = Math.min(pieceData.length,
                                          fileInfo.startOffset + fileInfo.length - pieceOffset);

            if (fileEndInPiece <= fileStartInPiece) {
                continue;
            }

            long writeLength = fileEndInPiece - fileStartInPiece;
            long writeOffsetInFile = Math.max(0, pieceOffset - fileInfo.startOffset);

            // Write data to file
            ByteBuffer buffer = ByteBuffer.wrap(pieceData, (int) fileStartInPiece, (int) writeLength);
            int written = fileInfo.channel.write(buffer, writeOffsetInFile);

            if (written != writeLength) {
                throw new IOException("Failed to write complete data to file: " + fileInfo.filePath);
            }

            log.debug("Wrote {} bytes to file {} at offset {}",
                     writeLength, Paths.get(fileInfo.filePath).getFileName(), writeOffsetInFile);

            dataOffset += writeLength;
            remainingData -= writeLength;

            if (remainingData <= 0) {
                break;
            }
        }

        // Force data to disk
        for (TorrentFileInfo fileInfo : fileInfos) {
            if (fileInfo.channel != null) {
                fileInfo.channel.force(false);
            }
        }
    }

    /**
     * Read a piece from the appropriate file(s) on disk.
     */
    public synchronized byte[] readPiece(int pieceIndex) throws IOException {
        long pieceLength = torrentInfo.getPieceLength(pieceIndex);
        byte[] pieceData = new byte[(int) pieceLength];
        long pieceOffset = (long) pieceIndex * torrentInfo.getPieceLength();
        long remainingData = pieceLength;
        int dataOffset = 0;

        log.debug("Reading piece {} at offset {} ({} bytes)", pieceIndex, pieceOffset, pieceLength);

        for (TorrentFileInfo fileInfo : fileInfos) {
            if (pieceOffset >= (fileInfo.startOffset + fileInfo.length)) {
                // This piece starts after this file
                continue;
            }

            if ((pieceOffset + pieceLength) <= fileInfo.startOffset) {
                // This piece ends before this file
                break;
            }

            // Calculate overlap with this file
            long fileStartInPiece = Math.max(0, fileInfo.startOffset - pieceOffset);
            long fileEndInPiece = Math.min(pieceLength, fileInfo.startOffset + fileInfo.length - pieceOffset);

            if (fileEndInPiece <= fileStartInPiece) {
                continue;
            }

            long readLength = fileEndInPiece - fileStartInPiece;
            long readOffsetInFile = Math.max(0, pieceOffset - fileInfo.startOffset);

            // Read data from file
            ByteBuffer buffer = ByteBuffer.allocate((int) readLength);
            int bytesRead = fileInfo.channel.read(buffer, readOffsetInFile);

            if (bytesRead != readLength) {
                throw new IOException("Failed to read complete data from file: " + fileInfo.filePath);
            }

            // Copy to piece data
            System.arraycopy(buffer.array(), 0, pieceData, dataOffset, (int) readLength);

            dataOffset += (int) readLength;
            remainingData -= readLength;

            if (remainingData <= 0) {
                break;
            }
        }

        return pieceData;
    }

    /**
     * Check if a piece exists on disk (all required bytes are available).
     */
    public boolean isPieceOnDisk(int pieceIndex) {
        try {
            long pieceLength = torrentInfo.getPieceLength(pieceIndex);
            long pieceOffset = (long) pieceIndex * torrentInfo.getPieceLength();

            for (TorrentFileInfo fileInfo : fileInfos) {
                if (pieceOffset >= fileInfo.startOffset + fileInfo.length) {
                    continue;
                }

                if (pieceOffset + pieceLength <= fileInfo.startOffset) {
                    break;
                }

                Path filePath = Paths.get(fileInfo.filePath);
                if (!Files.exists(filePath)) {
                    return false;
                }

                // Check if file has enough data for this piece
                long fileSize = Files.size(filePath);
                long requiredSize = Math.min(fileInfo.length,
                                           pieceOffset + pieceLength - fileInfo.startOffset);

                if (fileSize < requiredSize) {
                    return false;
                }
            }

            return true;
        } catch (IOException e) {
            log.warn("Error checking if piece {} exists on disk: {}", pieceIndex, e.getMessage());
            return false;
        }
    }

    /**
     * Close all file handles.
     */
    public void close() {
        log.info("Closing file manager");
        for (TorrentFileInfo fileInfo : fileInfos) {
            try {
                fileInfo.close();
            } catch (IOException e) {
                log.warn("Error closing file {}: {}", fileInfo.filePath, e.getMessage());
            }
        }
    }

    /**
     * Get list of file paths that will be created.
     */
    public List<String> getFilePaths() {
        return fileInfos.stream()
                .map(fileInfo -> fileInfo.filePath)
                .toList();
    }
}