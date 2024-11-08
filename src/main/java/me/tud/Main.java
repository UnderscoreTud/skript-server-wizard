package me.tud;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.*;

public class Main {

    public static final String LIST_VERSIONS = "-versions";
    public static final String CANCEL = "-cancel";
    public static final String RUN_SCRIPT = """
        java -Xmx2G -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 -jar server.jar nogui
        PAUSE""";

    public static final String RESET = "\u001B[0m";
    public static final String BOLD = "\u001B[1m";
    public static final String INFO = "\u001B[90m";
    public static final String WARN = "\u001B[93m";
    public static final String ERROR = "\u001B[91m";
    public static final String SUCCESS = "\u001B[92m";

    public static void main(String[] args) throws IOException, URISyntaxException, InterruptedException {
        Console console = System.console();
        if (console == null) {
            File source = new File(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            ProcessBuilder builder = new ProcessBuilder("cmd", "/c", "start", "cmd.exe", "/k", "java -jar \"" + source.getName() + "\" && exit");
            builder.start();
            return;
        }

        Terminal terminal = TerminalBuilder.terminal();
        LineReader reader = LineReaderBuilder.builder().terminal(terminal).build();

        ServerInfo serverInfo = startWizard(terminal, reader);
        try {
            if (serverInfo != null)
                serverInfo.setup(reader);
        } catch (IOException | InterruptedException e) {
            handleException(e, terminal, reader, serverInfo);
        }

        reader.printAbove("");
        terminal.writer().print("Press any key to exit...");
        //noinspection ResultOfMethodCallIgnored
        terminal.reader().read();
        terminal.close();
    }

    public static void handleException(Exception exception, Terminal terminal, LineReader reader, ServerInfo serverInfo) {
        reader.printAbove(ERROR);
        reader.printAbove("Failed to setup server");
        exception.printStackTrace(terminal.writer());
        if (serverInfo == null)
            return;

        if (!serverInfo.folder().exists())
            return;

        reader.printAbove("");
        String cleanup;
        do {
            cleanup = reader.readLine("Cleanup server folder? (y/n): ");
        } while (!cleanup.equals("y") && !cleanup.equals("n"));
        if (cleanup.equals("n"))
            return;
        reader.printAbove(INFO + "Cleaning up server folder..." + RESET);
        if (deleteFolder(serverInfo.folder())) reader.printAbove(SUCCESS + "Server folder cleaned up!" + RESET);
        else reader.printAbove(ERROR + "Failed to clean up server folder" + RESET);
    }

    private static boolean deleteFolder(File folder) {
        File[] files = folder.listFiles();
        if (files == null)
            return folder.delete();
        for (File file : files) {
            if (!deleteFolder(file))
                return false;
        }
        return folder.delete();
    }

    private static ServerInfo startWizard(Terminal terminal, LineReader reader) throws IOException, InterruptedException, URISyntaxException {
        printWarningBlock(reader, new String[]{
            "By using this wizard, you implicitly agree to the Minecraft EULA",
            "If you don't agree to the EULA, do not use this wizard",
            "https://account.mojang.com/documents/minecraft_eula"
        });
        reader.printAbove(BOLD + "Skript Server Setup Wizard" + RESET);
        reader.printAbove("");

        String serverName;
        while ((serverName = reader.readLine("Server name: ")).isBlank() || new File(serverName).exists()) {
            if (serverName.isBlank()) reader.printAbove(ERROR + "Server name cannot be empty" + RESET);
            else reader.printAbove(ERROR + "Server '" + serverName + "' already exists" + RESET);
            reader.printAbove("");
        }
        reader.printAbove("");

        PaperAPI.Version paperVersion = pickPaperVersion(reader, true);
        reader.printAbove("");

        GitHubAPI.Release skriptRelease = pickRelease(reader, GitHubAPI.SKRIPT_REPO, true);
        GitHubAPI.Release.Asset skriptAsset = pickAsset(reader, skriptRelease);
        if (skriptAsset == null)
            return null;
        ServerInfo.Addon skript = new ServerInfo.Addon(GitHubAPI.SKRIPT_REPO.name(), skriptRelease.tagName(), skriptAsset.downloadURL());
        reader.printAbove("");

        Set<ServerInfo.Addon> addons = new LinkedHashSet<>();
        reader.printAbove(INFO + "Add extra Skript addons" + RESET);
        while (true) {
            reader.printAbove(INFO + "Current addons: " + addons.stream()
                .map(addon -> addon.name() + " " + addon.version())
                .toList() + RESET);
            String addonName = reader.readLine("Addon name: ");
            if (addonName.isBlank())
                break;

            GitHubAPI.Repository repository = pickRepository(reader, addonName);
            if (repository == null)
                continue;

            reader.printAbove("");
            GitHubAPI.Release addonRelease = pickCancellableRelease(reader, repository, true);
            if (addonRelease == null) {
                reader.printAbove("");
                continue;
            }
            GitHubAPI.Release.Asset addonAsset = pickAsset(reader, addonRelease);
            if (addonAsset == null) {
                reader.printAbove("");
                continue;
            }
            addons.add(new ServerInfo.Addon(repository.name(), addonRelease.tagName(), addonAsset.downloadURL()));
            reader.printAbove("");
        }

        reader.printAbove("");
        reader.printAbove(INFO + "Server setup summary" + RESET);
        reader.printAbove(INFO + " - Server name: " + serverName + RESET);
        reader.printAbove(INFO + " - Paper version: " + paperVersion.version() + RESET);
        reader.printAbove(INFO + " - Skript version: " + skriptRelease.tagName() + RESET);
        reader.printAbove(INFO + " - Skript addons: " + addons.stream()
            .map(addon -> addon.name() + " " + addon.version())
            .toList() + RESET);
        reader.printAbove("");
        String confirmation;
        do {
            confirmation = reader.readLine("Confirm server setup? (y/n): ").toLowerCase(Locale.ENGLISH);
        } while (!confirmation.equals("y") && !confirmation.equals("n"));
        reader.printAbove("");
        if (confirmation.equals("n")) {
            reader.printAbove(ERROR + "Server setup cancelled" + RESET);
            return null;
        }
        return new ServerInfo(serverName, paperVersion, skript, new HashSet<>(addons));
    }

    private static void printWarningBlock(LineReader reader, String[] content) {
        int length = Arrays.stream(content).mapToInt(String::length).max().orElse(0);
        int lengthWithBorders = length + 6;
        int padding = (reader.getTerminal().getWidth() - lengthWithBorders) / 2;
        String title = " WARNING ";
        int topBorderPart = (lengthWithBorders - title.length()) / 2;
        int extra = (lengthWithBorders - title.length()) % 2;
        reader.printAbove(WARN + " ".repeat(padding) + "#".repeat(topBorderPart) + title + "#".repeat(topBorderPart + extra));
        for (String warning : content) {
            int diff = length - warning.length();
            int offset = diff / 2;
            reader.printAbove(" ".repeat(padding) + "#! " + " ".repeat(offset + (diff % 2)) + warning + " ".repeat(offset) + " !#");
        }
        reader.printAbove(" ".repeat(padding) + "#".repeat(lengthWithBorders) + RESET);
    }

    private static PaperAPI.Version pickPaperVersion(LineReader reader, boolean showHint) throws IOException, InterruptedException {
        if (showHint)
            reader.printAbove(INFO + "Use '" + LIST_VERSIONS + "' to list all available versions" + RESET);
        String stringVersion = reader.readLine("Paper version (default: latest): ");
        if (stringVersion.isBlank() || stringVersion.equalsIgnoreCase("latest")) {
            PaperAPI.Version version = PaperAPI.latestVersion();
            reader.printAbove(INFO + "Using latest Paper version: " + version.version() + RESET);
            return version;
        } else if (stringVersion.equals(LIST_VERSIONS)) {
            List<PaperAPI.Version> versions = PaperAPI.versions();
            PaperAPI.Version latest = PaperAPI.latestVersion();
            reader.printAbove(INFO + "Paper versions:" + RESET);
            for (PaperAPI.Version version : versions) {
                String versionName = version.version();
                if (version.equals(latest))
                    versionName += " (latest)";
                reader.printAbove(INFO + " - " + versionName + RESET);
            }
            reader.printAbove("");
            return pickPaperVersion(reader, false);
        }
        PaperAPI.Version version = PaperAPI.version(stringVersion);
        if (!version.valid()) {
            reader.printAbove(ERROR + "Paper version '" + stringVersion + "' is not valid" + RESET);
            reader.printAbove("");
            return pickPaperVersion(reader, true);
        }
        return version;
    }

    private static GitHubAPI.Repository pickRepository(LineReader reader, String name) throws IOException, InterruptedException, URISyntaxException {
        List<GitHubAPI.Repository> repositories = GitHubAPI.searchRepositories(name);
        if (repositories.isEmpty()) {
            reader.printAbove(ERROR + "No repositories found for '" + name + "'" + RESET);
            reader.printAbove("");
            return null;
        }

        if (repositories.size() == 1) {
            reader.printAbove(INFO + "Selected repository: " + repositories.get(0).getFullName() + RESET);
            return repositories.get(0);
        }

        reader.printAbove("");
        reader.printAbove(INFO + "Multiple repositories found for '" + name + "'" + RESET);
        for (int i = 0; i < repositories.size(); i++)
            reader.printAbove(INFO + (i + 1) + ") " + repositories.get(i).getFullName() + RESET);
        int index;
        while (true) {
            try {
                String string = reader.readLine("Select a repository by index: ");
                if (string.isBlank()) {
                    reader.printAbove("");
                    return null;
                }
                index = Integer.parseInt(string);
                if (index >= 1 && index <= repositories.size())
                    break;
            } catch (NumberFormatException ignored) {}
            reader.printAbove(ERROR + "Invalid repository index" + RESET);
            reader.printAbove("");
        }
        GitHubAPI.Repository repository = repositories.get(index - 1);
        reader.printAbove(INFO + "Selected repository: " + repository.getFullName() + RESET);
        return repository;
    }

    private static GitHubAPI.Release pickRelease(LineReader reader, GitHubAPI.Repository repository, boolean showHint) throws IOException, InterruptedException {
        return pickRelease(reader, repository, showHint, false);
    }
    
    private static GitHubAPI.Release pickCancellableRelease(LineReader reader, GitHubAPI.Repository repository, boolean showHint) throws IOException, InterruptedException {
        return pickRelease(reader, repository, showHint, true);
    }

    private static GitHubAPI.Release pickRelease(LineReader reader, GitHubAPI.Repository repository, boolean showHint, boolean cancellable) throws IOException, InterruptedException {
        String name = repository.name();
        if (showHint)
            reader.printAbove(INFO + "Use '" + LIST_VERSIONS + "' to list all available versions" + RESET);
        if (cancellable)
            reader.printAbove(INFO + "Use '" + CANCEL + "' to cancel" + RESET);
        String stringVersion = reader.readLine(name + " version (default: latest): ");
        if (stringVersion.isBlank() || stringVersion.equalsIgnoreCase("latest")) {
            GitHubAPI.Release release = repository.getLatestRelease();
            if (release == null) {
                reader.printAbove(ERROR + name + " doesn't have a latest release" + RESET);
                return null;
            }
            reader.printAbove(INFO + "Using latest " + name + " version: " + release.tagName() + RESET);
            return release;
        } else if (cancellable && stringVersion.equals(CANCEL)) {
            return null;
        } else if (stringVersion.equals(LIST_VERSIONS)) {
            List<GitHubAPI.Release> versions = repository.getReleases();
            if (versions.isEmpty()) {
                reader.printAbove(ERROR + "No releases found for " + name + RESET);
                return null;
            }
            GitHubAPI.Release latest = repository.getLatestRelease();
            reader.printAbove(INFO + name + " versions:" + RESET);
            for (int i = versions.size() - 1; i >= 0; i--) {
                GitHubAPI.Release version = versions.get(i);
                String versionName = version.tagName();
                if (version.equals(latest))
                    versionName += " (latest)";
                if (version.prerelease())
                    versionName += " (prerelease)";
                reader.printAbove(INFO + " - " + versionName + RESET);
            }
            reader.printAbove("");
            return pickRelease(reader, repository, false);
        }
        GitHubAPI.Release version = repository.getRelease(stringVersion);
        if (version == null) {
            reader.printAbove(ERROR + name + " version '" + stringVersion + "' is not valid" + RESET);
            reader.printAbove("");
            return pickRelease(reader, repository, true);
        }
        return version;
    }

    private static GitHubAPI.Release.Asset pickAsset(LineReader reader, GitHubAPI.Release release) throws IOException, InterruptedException {
        List<GitHubAPI.Release.Asset> assets = release.getJarAssets();
        GitHubAPI.Repository repository = release.repository();
        if (assets.isEmpty()) {
            reader.printAbove(ERROR + "No jars found for '" + repository.getFullName() + "' version '" + release.tagName() + "'" + RESET);
            reader.printAbove("");
            return null;
        }

        if (assets.size() == 1)
            return assets.get(0);

        reader.printAbove("");
        reader.printAbove(INFO + "Multiple assets found for " + repository.name() + " " + release.tagName() + RESET);
        for (int i = 0; i < assets.size(); i++)
            reader.printAbove(INFO + (i + 1) + ") " + assets.get(i).name() + RESET);
        int index;
        while (true) {
            try {
                index = Integer.parseInt(reader.readLine("Select an asset by index: "));
                if (index >= 1 && index <= assets.size())
                    break;
            } catch (NumberFormatException ignored) {}
            reader.printAbove(ERROR + "Invalid asset index" + RESET);
            reader.printAbove("");
        }
        GitHubAPI.Release.Asset asset = assets.get(index - 1);
        reader.printAbove(INFO + "Selected asset: " + asset.name() + RESET);
        return asset;
    }

}
