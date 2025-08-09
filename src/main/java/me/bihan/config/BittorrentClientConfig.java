package me.bihan.config;

import lombok.Getter;
import me.bihan.service.*;
import me.bihan.service.impl.*;
import me.bihan.tracker.HttpTrackerClient;
import me.bihan.tracker.TrackerClient;
import me.bihan.tracker.UdpTrackerClient;
import me.bihan.util.FormatUtils;

import java.util.List;

/**
 * Enhanced configuration class that creates and wires dependencies for both downloading and
 * seeding. Implements manual Dependency Injection following DIP. Extended to support seeding
 * functionality while maintaining backward compatibility.
 */
@Getter
public class BittorrentClientConfig {

  // Configuration properties
  private final String downloadDirectory;
  private final int baseListenPort;
  private final int maxConcurrentDownloads;
  private final int maxConcurrentUploadsPerTorrent;
  private final int seedingAnnouncementIntervalMinutes;

  public BittorrentClientConfig(
      String downloadDirectory,
      int baseListenPort,
      int maxConcurrentDownloads,
      int maxConcurrentUploadsPerTorrent,
      int seedingAnnouncementIntervalMinutes) {
    this.downloadDirectory = downloadDirectory;
    this.baseListenPort = baseListenPort;
    this.maxConcurrentDownloads = maxConcurrentDownloads;
    this.maxConcurrentUploadsPerTorrent = maxConcurrentUploadsPerTorrent;
    this.seedingAnnouncementIntervalMinutes = seedingAnnouncementIntervalMinutes;
  }

  /**
   * Creates the simplified BitTorrent service with unified download and seeding. This replaces the
   * complex multi-service architecture with a single, intelligent service.
   */
  public SimpleBittorrentService createSimpleBittorrentService() {
    return new SimpleBittorrentService(
        createTorrentParserService(),
        createTrackerService(),
        createHashCalculatorService(),
        downloadDirectory,
        maxConcurrentDownloads,
        maxConcurrentUploadsPerTorrent,
        baseListenPort,
        seedingAnnouncementIntervalMinutes);
  }

  /** Creates a torrent parser service. */
  public TorrentParserService createTorrentParserService() {
    return new TorrentParserServiceImpl();
  }

  /** Creates a tracker service. */
  public TrackerService createTrackerService() {
    TrackerClient httpClient = new HttpTrackerClient();
    TrackerClient udpClient = new UdpTrackerClient();
    return new TrackerServiceImpl(List.of(httpClient, udpClient));
  }

  /** Creates a hash calculator service. */
  public HashCalculatorService createHashCalculatorService() {
    return new HashCalculatorServiceImpl();
  }

  /** Creates a console-based download progress observer. */
  public DownloadProgressObserver createConsoleProgressObserver() {
    return new DownloadProgressObserver() {
      @Override
      public void onDownloadStarted(String torrentName, long totalSize) {
        System.out.println("\n=== Download Started ===");
        System.out.println("Torrent: " + torrentName);
        System.out.println("Size: " + FormatUtils.formatBytes(totalSize));
        System.out.println();
      }

      @Override
      public void onPieceCompleted(int pieceIndex, int totalPieces, long bytesDownloaded) {
        System.out.println("Piece " + pieceIndex + "/" + totalPieces + " Completed");
      }

      @Override
      public void onDownloadCompleted(String torrentName, long totalSize) {
        System.out.println("\n\n=== Download Completed ===");
        System.out.println("Torrent: " + torrentName);
        System.out.println("Size: " + FormatUtils.formatBytes(totalSize));
        System.out.println();
      }

      @Override
      public void onDownloadFailed(String torrentName, Exception error) {
        System.err.println("\n=== Download Failed ===");
        System.err.println("Torrent: " + torrentName);
        System.err.println("Error: " + error.getMessage());
        System.err.println();
      }

      @Override
      public void onProgressUpdate(double progressPercentage, long downloadSpeed) {
        System.out.println(FormatUtils.formatPercentage(progressPercentage) + "% Complete, Download Speed: " + FormatUtils.formatBytes(downloadSpeed) + "ps");
      }
    };
  }

  /** Creates a console-based seeding progress observer. */
  public SeedingProgressObserver createConsoleSeedingProgressObserver() {
    return new ConsoleSeedingProgressObserver();
  }
}
