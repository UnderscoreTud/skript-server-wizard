package me.tud;

import com.google.gson.JsonElement;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public final class PaperAPI {
    
    public static final String PAPER_ENDPOINT = "https://papermc.io/api/v2/projects/paper";
    public static final String PAPER_VERSION_ENDPOINT = PAPER_ENDPOINT + "/versions/%s";
    public static final String PAPER_BUILD_ENDPOINT = PAPER_VERSION_ENDPOINT + "/builds/%s";
    public static final String PAPER_DOWNLOAD_ENDPOINT = PAPER_BUILD_ENDPOINT + "/downloads/%s";
    private static final int OK = 200;

    private static List<Version> versions;

    private PaperAPI() {
        throw new UnsupportedOperationException();
    }
    
    public static List<Version> versions() throws IOException, InterruptedException {
        if (versions != null)
            return versions;
        HttpResponse<JsonElement> response = HttpUtils.sendRequest(
            HttpRequest.newBuilder(URI.create(PAPER_ENDPOINT))
                .GET()
                .build()
        );
        if (response.statusCode() != OK)
            throw new IOException("Failed to get versions: " + response.body());
        List<Version> versions = new ArrayList<>();
        for (JsonElement element : response.body().getAsJsonObject().getAsJsonArray("versions"))
            versions.add(new Version(element.getAsString()));
        return PaperAPI.versions = versions;
    }

    public static Version version(String version) {
        return new Version(version);
    }

    public static Version latestVersion() throws IOException, InterruptedException {
        List<Version> versions = versions();
        return versions.get(versions.size() - 1);
    }

    public static class Version {

        private final String version;
        private List<Integer> builds;

        public Version(String version) {
            this.version = version;
        }

        public String version() {
            return version;
        }

        public List<Integer> builds() throws IOException, InterruptedException {
            if (builds != null)
                return builds;
            HttpResponse<JsonElement> response = HttpUtils.sendRequest(
                HttpRequest.newBuilder(URI.create(PAPER_VERSION_ENDPOINT.formatted(version)))
                    .GET()
                    .build()
            );
            if (response.statusCode() != OK)
                return this.builds = List.of();
            List<Integer> builds = new ArrayList<>();
            for (JsonElement element : response.body().getAsJsonObject().getAsJsonArray("builds"))
                builds.add(element.getAsInt());
            return this.builds = builds;
        }

        public int latestBuild() throws IOException, InterruptedException {
            return builds().get(builds.size() - 1);
        }

        public URL downloadURL() throws IOException, InterruptedException {
            int latestBuild = latestBuild();
            String name = "paper-" + version + "-" + latestBuild + ".jar";
            try {
                return new URL(PAPER_DOWNLOAD_ENDPOINT.formatted(version, latestBuild, name));
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }

        public boolean valid() throws IOException, InterruptedException {
            return !builds().isEmpty();
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("Version{");
            sb.append("version='").append(version).append('\'');
            sb.append(", builds=").append(builds);
            sb.append('}');
            return sb.toString();
        }

    }

}
