package me.bihan;

import lombok.extern.log4j.Log4j2;
import me.bihan.cli.BittorrentClientCommand;
import me.bihan.config.BittorrentClientConfig;
import me.bihan.service.SimpleBittorrentService;
import me.bihan.service.DownloadProgressObserver;
import me.bihan.service.SeedingProgressObserver;
import me.bihan.util.FormatUtils;
import org.apache.logging.log4j.core.config.Configurator;
import picocli.CommandLine;

/**
 * BitTorrent client application.
 * Single command that intelligently handles everything:
 * - Checks existing files and verifies hashes
 * - Seeds any valid pieces immediately
 * - Downloads any missing pieces
 * - Continues seeding indefinitely
 *
 * No need for separate download/seed commands - one command does it all.
 */
@Log4j2
public class BittorrentClientApplication {

    public static void main(String[] args) {
        // Parse command line arguments with picocli
        BittorrentClientCommand command = new BittorrentClientCommand();
        CommandLine commandLine = new CommandLine(command);
        
        try {
            // Parse the command line arguments
            commandLine.parseArgs(args);

            // Check if help or version was requested
            if (commandLine.isUsageHelpRequested()) {
                commandLine.usage(System.out);
                System.exit(0);
            }

            if (commandLine.isVersionHelpRequested()) {
                commandLine.printVersionHelp(System.out);
                System.exit(0);
            }

            // Validate arguments
            if (!command.validateArguments()) {
                System.exit(1);
            }
            
            // Set up logging verbosity
            if (command.isVerbose()) {
                Configurator.setRootLevel(org.apache.logging.log4j.Level.DEBUG);
                System.out.println("Verbose logging enabled");
            }
            
            log.info("=== BitTorrent Client Starting ===");
            log.info("Download directory: {}", command.getDownloadDirectory());
            log.info("Base port: {}", command.getBaseListenPort());
            log.info("Max downloads: {}", command.getMaxConcurrentDownloads());
            log.info("Max uploads: {}", command.getMaxConcurrentUploadsPerTorrent());

            // Create configuration with CLI arguments
            BittorrentClientConfig config = new BittorrentClientConfig(
                command.getDownloadDirectory(),
                command.getBaseListenPort(),
                command.getMaxConcurrentDownloads(),
                command.getMaxConcurrentUploadsPerTorrent(),
                command.getSeedingAnnouncementIntervalMinutes()
            );
            
            SimpleBittorrentService service = config.createSimpleBittorrentService();

            // Create observers
            DownloadProgressObserver downloadObserver = config.createConsoleProgressObserver();
            SeedingProgressObserver seedingObserver = config.createConsoleSeedingProgressObserver();

            // Setup shutdown hook for graceful cleanup
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("\nShutting down BitTorrent client...");
                service.stopAll();
                System.out.println("Shutdown complete.");
            }));

            String[] torrentFilePaths = command.getTorrentFilePaths();
            
            if (torrentFilePaths.length == 1) {
                // Single torrent
                processSingleTorrent(service, torrentFilePaths[0], downloadObserver, seedingObserver);
            } else {
                // Multiple torrents
                processMultipleTorrents(service, torrentFilePaths, downloadObserver, seedingObserver);
            }

            log.info("=== Simple BitTorrent Client Finished ===");

        } catch (CommandLine.ParameterException ex) {
            // Handle picocli parameter exceptions
            System.err.println("Error: " + ex.getMessage());
            if (!commandLine.isUsageHelpRequested()) {
                System.err.println();
                commandLine.usage(System.err);
            }
            System.exit(2);
        } catch (Exception e) {
            log.error("Application error: {}", e.getMessage(), e);
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }

    /**
     * Process a single torrent file.
     */
    private static void processSingleTorrent(SimpleBittorrentService service, String torrentFile,
                                           DownloadProgressObserver downloadObserver,
                                           SeedingProgressObserver seedingObserver) throws Exception {
        System.out.println("Processing torrent: " + torrentFile);
        System.out.println("Checking existing files, downloading missing pieces, and seeding...");

        service.processTorrent(torrentFile, downloadObserver, seedingObserver);

        // Keep running and show periodic status
        keepRunningAndShowStatus(service, new String[]{torrentFile});
    }

    /**
     * Process multiple torrent files.
     */
    private static void processMultipleTorrents(SimpleBittorrentService service, String[] torrentFiles,
                                              DownloadProgressObserver downloadObserver,
                                              SeedingProgressObserver seedingObserver) {
        System.out.println("Processing " + torrentFiles.length + " torrents...");

        // Start processing all torrents
        for (String torrentFile : torrentFiles) {
            try {
                System.out.println("Starting: " + torrentFile);
                service.processTorrent(torrentFile, downloadObserver, seedingObserver);
            } catch (Exception e) {
                System.err.println("Failed to start " + torrentFile + ": " + e.getMessage());
                log.error("Failed to start {}: {}", torrentFile, e.getMessage(), e);
            }
        }

        // Keep running and show periodic status
        keepRunningAndShowStatus(service, torrentFiles);
    }

    /**
     * Keep the application running and show periodic status updates.
     */
    private static void keepRunningAndShowStatus(SimpleBittorrentService service, String[] torrentFiles) {
        System.out.println("\nTorrents are now processing. Press Ctrl+C to stop...");

        try {
            while (true) {
                Thread.sleep(10000); // 10 seconds

                // Show status for active torrents
                int activeTorrentsCount = service.getActiveTorrentCount();
                if (activeTorrentsCount > 0) {
                    System.out.printf("\n--- Status Update ---\n");
                    System.out.printf("Active torrents: %d\n", activeTorrentsCount);

                    for (String torrentFile : torrentFiles) {
                        if (service.isProcessing(torrentFile)) {
                            var stats = service.getSeedingStats(torrentFile);
                            if (stats != null && stats.getActivePeers() > 0) {
                                System.out.printf("%s: %d peers, %s uploaded\n",
                                                getFileName(torrentFile),
                                                stats.getActivePeers(),
                                                FormatUtils.formatBytes(stats.getTotalUploaded()));
                            } else {
                                System.out.printf("%s: Processing...\n", getFileName(torrentFile));
                            }
                        }
                    }
                    System.out.println();
                } else {
                    System.out.println("No active torrents. Exiting...");
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("\nInterrupted, shutting down...");
        }
    }

    /**
     * Extract filename from path.
     */
    private static String getFileName(String filePath) {
        int lastSlash = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        return lastSlash >= 0 ? filePath.substring(lastSlash + 1) : filePath;
    }


}