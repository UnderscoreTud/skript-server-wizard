package me.tud;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class GitHubAPI {

    private static final String GITHUB_ENDPOINT = "https://api.github.com";
    private static final String GITHUB_SEARCH_ENDPOINT = GITHUB_ENDPOINT + "/search/repositories?q=%s+language:%s";
    private static final String GITHUB_RELEASES_ENDPOINT = GITHUB_ENDPOINT + "/repos/%s/%s/releases";
    private static final String GITHUB_RELEASES_LATEST_ENDPOINT = GITHUB_RELEASES_ENDPOINT + "/latest";
    private static final String GITHUB_ASSETS_ENDPOINT = GITHUB_ENDPOINT + "/repos/%s/%s/releases/%s/assets";

    public static final Repository SKRIPT_REPO = new Repository(new User("SkriptLang"), "Skript");

    private static final int OK = 200;

    private GitHubAPI() {
        throw new UnsupportedOperationException();
    }

    public static List<Repository> searchRepositories(String name) throws IOException, URISyntaxException, InterruptedException {
        name = name.toLowerCase(Locale.ENGLISH);
        URI uri = new URI(GITHUB_SEARCH_ENDPOINT.formatted(name, "java"));
        HttpResponse<JsonElement> response = HttpUtils.sendRequest(
            HttpRequest.newBuilder(uri)
                .GET()
                .setHeader("Accept", "application/vnd.github+json")
                .build()
        );
        if (response.statusCode() != OK)
            throw new IOException("Failed to search repositories: " + response.body());

        JsonObject body = response.body().getAsJsonObject();
        int size = body.get("total_count").getAsInt();
        List<Repository> repositories = new ArrayList<>(size);
        for (JsonElement element : body.getAsJsonArray("items")) {
            Repository repository = Repository.from(element.getAsJsonObject());
            if (StringDistance.editDistance(name, repository.name().toLowerCase(Locale.ENGLISH)) < 3)
                repositories.add(repository);
        }
        return repositories;
    }

    public record User(String name) {

        public static User from(JsonObject object) {
            return new User(object.get("login").getAsString());
        }

    }

    public static class Repository {

        private final User owner;
        private final String name;
        private transient List<Release> releases;

        public Repository(User owner, String name) {
            this.owner = owner;
            this.name = name;
        }

        public String getFullName() {
            return owner.name() + "/" + name;
        }

        public String url() {
            return "https://www.github.com/" + owner.name() + "/" + name;
        }

        public static Repository from(JsonObject object) {
            return new Repository(
                User.from(object.getAsJsonObject("owner")),
                object.get("name").getAsString()
            );
        }

        public List<Release> getReleases() throws IOException, InterruptedException {
            if (releases != null)
                return releases;
            HttpResponse<JsonElement> response = HttpUtils.sendRequest(
                HttpRequest.newBuilder(URI.create(GITHUB_RELEASES_ENDPOINT.formatted(owner.name(), name)))
                    .GET()
                    .setHeader("Accept", "application/vnd.github+json")
                    .build()
            );
            if (response.statusCode() != OK)
                return this.releases = Collections.emptyList();

            List<Release> releases = new ArrayList<>();
            for (JsonElement element : response.body().getAsJsonArray())
                releases.add(Release.from(this, element.getAsJsonObject()));
            return this.releases = releases;
        }

        public Release getLatestRelease() throws IOException, InterruptedException {
            if (releases != null) {
                for (Release release : releases) {
                    if (!release.draft() && !release.prerelease())
                        return release;
                }
                return null;
            }
            HttpResponse<JsonElement> response = HttpUtils.sendRequest(
                HttpRequest.newBuilder(URI.create(GITHUB_RELEASES_LATEST_ENDPOINT.formatted(owner.name(), name)))
                    .GET()
                    .setHeader("Accept", "application/vnd.github+json")
                    .build()
            );
            if (response.statusCode() != OK)
                return null;
            return Release.from(this, response.body().getAsJsonObject());
        }

        public Release getRelease(String name) throws IOException, InterruptedException {
            for (Release release : getReleases()) {
                if (release.tagName().equals(name))
                    return release;
            }
            return null;
        }

        public User owner() {
            return owner;
        }

        public String name() {
            return name;
        }

    }

    public static class Release {

        private final Repository repository;
        private final int releaseId;
        private final String name, tagName, url;
        private final boolean draft, prerelease;
        private transient List<Asset> assets;

        public Release(
            Repository repository,
            int releaseId,
            String name,
            String tagName,
            String url,
            boolean draft,
            boolean prerelease
        ) {
            this.repository = repository;
            this.releaseId = releaseId;
            this.name = name;
            this.tagName = tagName;
            this.url = url;
            this.draft = draft;
            this.prerelease = prerelease;
        }

        public List<Asset> getJarAssets() throws IOException, InterruptedException {
            if (assets != null)
                return assets;

            HttpResponse<JsonElement> response = HttpUtils.sendRequest(
                HttpRequest.newBuilder(URI.create(GITHUB_ASSETS_ENDPOINT.formatted(
                        repository.owner().name(),
                        repository.name(),
                        releaseId
                    )))
                    .GET()
                    .setHeader("Accept", "application/vnd.github+json")
                    .build()
            );
            if (response.statusCode() != OK)
                throw new IOException("Failed to get release: " + response.body());

            List<Asset> assets = new ArrayList<>();
            for (JsonElement element : response.body().getAsJsonArray()) {
                JsonObject object = element.getAsJsonObject();
                if (object.get("content_type").getAsString().equals("application/java-archive")) {
                    assets.add(new Asset(
                        this,
                        object.get("name").getAsString(),
                        object.get("content_type").getAsString(),
                        new URL(object.get("browser_download_url").getAsString())
                    ));
                }
            }
            return this.assets = assets;
        }

        public Repository repository() {
            return repository;
        }

        public int releaseId() {
            return releaseId;
        }

        public String name() {
            return name;
        }

        public String tagName() {
            return tagName;
        }

        public String url() {
            return url;
        }

        public boolean draft() {
            return draft;
        }

        public boolean prerelease() {
            return prerelease;
        }

        public static Release from(Repository repository, JsonObject object) {
            return new Release(
                repository,
                object.get("id").getAsInt(),
                object.get("name").getAsString(),
                object.get("tag_name").getAsString(),
                object.get("html_url").getAsString(),
                object.get("draft").getAsBoolean(),
                object.get("prerelease").getAsBoolean()
            );
        }

        public record Asset(Release release, String name, String type, URL downloadURL) {}

    }

}
