package me.bihan.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import lombok.Getter;

import java.io.File;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * CLI command definition for the BitTorrent client using PicoCLI.
 * Defines all configurable options with appropriate defaults and help messages.
 */
@Command(
    name = "bittorrent-client",
    mixinStandardHelpOptions = true,
    version = "BitTorrent Client 1.0.0",
    description = "A BitTorrent client that automatically handles file verification, downloading, and seeding.",
    headerHeading = "%nUsage:%n%n",
    synopsisHeading = "",
    descriptionHeading = "%nDescription:%n%n",
    parameterListHeading = "%nParameters:%n",
    optionListHeading = "%nOptions:%n",
    commandListHeading = "%nCommands:%n",
    footerHeading = "%nExamples:%n",
    footer = {
        "  # Process a single torrent file:",
        "  java -jar bittorrent-client.jar example.torrent",
        "",
        "  # Process multiple torrents with custom download directory:",
        "  java -jar bittorrent-client.jar -d /path/to/downloads torrent1.torrent torrent2.torrent",
        "",
        "  # Use custom port and connection limits:",
        "  java -jar bittorrent-client.jar --port 7000 --max-downloads 32 --max-uploads 5 example.torrent",
        ""
    }
)
@Getter
public class BittorrentClientCommand implements Callable<Integer> {

    @Parameters(
        arity = "1..*",
        paramLabel = "<torrent-files>",
        description = "One or more .torrent files to process"
    )
    private List<String> torrentFiles;

    @Option(
        names = {"-d", "--download-dir"},
        paramLabel = "<directory>",
        description = "Directory to download files to (default: ~/Downloads/BitTorrent)"
    )
    private String downloadDirectory = System.getProperty("user.home") + "/Downloads/BitTorrent";

    @Option(
        names = {"-p", "--port"},
        paramLabel = "<port>",
        description = "Base port number for listening (default: ${DEFAULT-VALUE})",
        defaultValue = "6881"
    )
    private int baseListenPort;

    @Option(
        names = {"-md", "--max-downloads"},
        paramLabel = "<count>",
        description = "Maximum concurrent download connections per torrent (default: ${DEFAULT-VALUE})",
        defaultValue = "48"
    )
    private int maxConcurrentDownloads;

    @Option(
        names = {"-mu", "--max-uploads"},
        paramLabel = "<count>",
        description = "Maximum concurrent upload connections per torrent (default: ${DEFAULT-VALUE})",
        defaultValue = "10"
    )
    private int maxConcurrentUploadsPerTorrent;

    @Option(
        names = {"-ai", "--announce-interval"},
        paramLabel = "<minutes>",
        description = "Tracker announcement interval in minutes (default: ${DEFAULT-VALUE})",
        defaultValue = "1"
    )
    private int seedingAnnouncementIntervalMinutes;

    @Option(
        names = {"-v", "--verbose"},
        description = "Enable verbose logging output"
    )
    private boolean verbose;

    @Override
    public Integer call() throws Exception {
        // This method will be called by the main application
        // The actual business logic will be handled in the main application
        return 0;
    }

    /**
     * Validates the command line arguments.
     * @return true if all arguments are valid, false otherwise
     */
    public boolean validateArguments() {
        // Validate download directory
        File downloadDir = new File(downloadDirectory);
        if (!downloadDir.exists() && !downloadDir.mkdirs()) {
            System.err.println("Error: Cannot create download directory: " + downloadDirectory);
            return false;
        }

        if (!downloadDir.canWrite()) {
            System.err.println("Error: Download directory is not writable: " + downloadDirectory);
            return false;
        }

        // Validate port range
        if (baseListenPort < 1024 || baseListenPort > 65535) {
            System.err.println("Error: Port must be between 1024 and 65535");
            return false;
        }

        // Validate connection limits
        if (maxConcurrentDownloads < 1 || maxConcurrentDownloads > 200) {
            System.err.println("Error: Max downloads must be between 1 and 200");
            return false;
        }

        if (maxConcurrentUploadsPerTorrent < 1 || maxConcurrentUploadsPerTorrent > 50) {
            System.err.println("Error: Max uploads must be between 1 and 50");
            return false;
        }

        // Validate announcement interval
        if (seedingAnnouncementIntervalMinutes < 1 || seedingAnnouncementIntervalMinutes > 1440) {
            System.err.println("Error: Announcement interval must be between 1 and 1440 minutes");
            return false;
        }

        // Validate torrent files
        for (String torrentFilePath : torrentFiles) {
            File torrentFile = new File(torrentFilePath);
            
            if (!torrentFile.exists()) {
                System.err.println("Error: Torrent file does not exist: " + torrentFilePath);
                return false;
            }

            if (!torrentFile.canRead()) {
                System.err.println("Error: Cannot read torrent file: " + torrentFilePath);
                return false;
            }

            if (!torrentFilePath.toLowerCase().endsWith(".torrent")) {
                System.err.println("Warning: File does not have .torrent extension: " + torrentFilePath);
            }
        }

        return true;
    }

    /**
     * Returns torrent file paths as an array for backward compatibility.
     */
    public String[] getTorrentFilePaths() {
        return torrentFiles.toArray(new String[0]);
    }
}