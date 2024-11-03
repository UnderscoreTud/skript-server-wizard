package me.tud;

import org.jline.reader.LineReader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static me.tud.Main.*;

public record ServerInfo(File folder, PaperAPI.Version version, Addon skript, Set<Addon> addons) {

    public ServerInfo(String name, PaperAPI.Version version, Addon skript, Set<Addon> addons) {
        this(new File(name), version, skript, addons);
    }

    public void setup(LineReader reader) throws IOException, InterruptedException {
        reader.printAbove(INFO + "Setting up server..." + RESET);
        reader.printAbove(INFO + "Creating server folder..." + RESET);
        if (!folder.mkdir())
            throw new IOException("Failed to create server folder");
        reader.printAbove(SUCCESS + "Server folder created!" + RESET);

        reader.printAbove(INFO + "Creating plugins folder..." + RESET);
        File pluginsFolder = new File(folder, "plugins");
        if (!pluginsFolder.mkdir())
            throw new IOException("Failed to create plugins folder");
        reader.printAbove(SUCCESS + "Plugins folder created!" + RESET);

        reader.printAbove(INFO + "Downloading server..." + RESET);
        downloadPaper(folder);
        reader.printAbove(SUCCESS + "Server downloaded!" + RESET);

        reader.printAbove(INFO + "Downloading " + skript.nameAndVersion() + "..." + RESET);
        File skriptFile = skript.download(pluginsFolder);
        reader.printAbove(SUCCESS + skript.nameAndVersion() + " downloaded!" + RESET);

        reader.printAbove(INFO + "Configuring Skript..." + RESET);
        config:
        try (JarFile jarFile = new JarFile(skriptFile)) {
            JarEntry jarEntry = jarFile.getJarEntry("config.sk");
            if (jarEntry == null) {
                reader.printAbove(WARN + "Failed to find config.sk in Skript jar!" + RESET);
                break config;
            }
            File skriptFolder = new File(pluginsFolder, "Skript");
            if (!skriptFolder.exists() && !skriptFolder.mkdir()) {
                reader.printAbove(WARN + "Failed to create Skript folder!" + RESET);
                break config;
            }
            File configFile = new File(skriptFolder, "config.sk");
            try (InputStream input = jarFile.getInputStream(jarEntry)) {
                String content = new String(input.readAllBytes());
                content = content
                    .replace("enable effect commands: false", "enable effect commands: true")
                    .replace("allow ops to use effect commands: false", "allow ops to use effect commands: true")
                    .replace("pattern: .*", "pattern: (?!-).*");
                Files.write(configFile.getAbsoluteFile().toPath(), content.getBytes());
            }
            reader.printAbove(SUCCESS + "Skript configured!" + RESET);
        }

        if (!addons.isEmpty()) {
            reader.printAbove(INFO + "Downloading addons..." + RESET);
            for (Addon addon : addons) {
                reader.printAbove(INFO + "Downloading " + addon.nameAndVersion() + "..." + RESET);
                addon.download(pluginsFolder);
                reader.printAbove(SUCCESS + addon.nameAndVersion() + " downloaded!" + RESET);
            }
            reader.printAbove(SUCCESS + "Addons downloaded!" + RESET);
        }

        reader.printAbove(INFO + "Creating eula..." + RESET);
        createEula(folder);
        reader.printAbove(SUCCESS + "Eula created!" + RESET);

        reader.printAbove(INFO + "Creating run script..." + RESET);
        createRunScript(folder);
        reader.printAbove(SUCCESS + "Run script created!" + RESET);

        reader.printAbove(SUCCESS + "Server setup complete!" + RESET);
    }

    private void downloadPaper(File directory) throws IOException, InterruptedException {
        download(version.downloadURL(), new File(directory, "server.jar"));
    }

    private void createEula(File directory) throws IOException {
        File eula = new File(directory, "eula.txt");
        try (FileOutputStream output = new FileOutputStream(eula)) {
            output.write("eula=true".getBytes());
        }
    }

    private void createRunScript(File directory) throws IOException {
        File run = new File(directory, "run.bat");
        try (FileOutputStream output = new FileOutputStream(run)) {
            output.write(RUN_SCRIPT.getBytes());
        }
    }

    public record Addon(String name, String version, URL downloadURL) {

        public String nameAndVersion() {
            return name + " " + version;
        }

        public File download(File directory) throws IOException {
            File file = new File(directory, name + "-" + version + ".jar");
            ServerInfo.download(downloadURL, file);
            return file;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null || obj.getClass() != Addon.class) return false;
            Addon addon = (Addon) obj;
            return name.equals(addon.name);
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

    }
    
    private static void download(URL url, File output) throws IOException {
        try (InputStream input = url.openStream(); FileOutputStream outputStream = new FileOutputStream(output)) {
            input.transferTo(outputStream);
        }
    }

}
