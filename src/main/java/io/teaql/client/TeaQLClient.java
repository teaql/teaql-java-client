package io.teaql.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * TeaQL Java Client — fluent builder, Java 17+.
 *
 * <pre>{@code
 * TeaQLClient client = TeaQLClient.builder()
 *     .endpoint("https://us-east.api.teaql.io")
 *     .licenseFile(Path.of("/path/to/license"))
 *     .timeout(Duration.ofSeconds(30))
 *     .build();
 *
 * // Execute a query
 * List<Map<String, Object>> rows = client.query("User", "list", Map.of("merchantId", "M001"));
 * }</pre>
 */
public final class TeaQLClient implements AutoCloseable {

    private final TeaQLConfig config;
    private final HttpClient httpClient;
    private final Gson gson;

    private TeaQLClient(TeaQLConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(config.timeout())
            .build();
        this.gson = new GsonBuilder().create();
    }

    // ─── Factory ───────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    // ─── Public API ─────────────────────────────────────────────

    /**
     * Execute a named query against a TeaQL entity.
     *
     * @param entity  entity name, e.g. {@code "User"}
     * @param query   query name defined in the KSML model
     * @param params  query parameters
     */
    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> query(String entity, String query, Map<String, Object> params) {
        var body = Map.of(
            "entity", entity,
            "query", query,
            "params", params == null ? Map.of() : params
        );
        var resp = post("/query", body);
        return gson.fromJson(resp, new TypeToken<List<Map<String, Object>>>(){}.getType());
    }

    /**
     * Execute a mutation (insert / update / delete) on a TeaQL entity.
     *
     * @param entity  entity name
     * @param mutation mutation name defined in the KSML model
     * @param params  mutation parameters
     * @return the primary key / affected row count (server-dependent)
     */
    @SuppressWarnings("unchecked")
    public Object mutate(String entity, String mutation, Map<String, Object> params) {
        var body = Map.of(
            "entity", entity,
            "mutation", mutation,
            "params", params == null ? Map.of() : params
        );
        var resp = post("/mutate", body);
        return gson.fromJson(resp, Object.class);
    }

    /**
     * Ping the TeaQL endpoint; returns {@code true} if reachable.
     */
    public boolean ping() {
        try {
            var request = HttpRequest.newBuilder()
                .uri(URI.create(config.endpoint() + "/health"))
                .timeout(config.timeout())
                .GET()
                .build();
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        } catch (Exception e) {
            return false;
        }
    }

    // ─── Internal ──────────────────────────────────────────────

    private String post(String path, Object body) {
        var json = gson.toJson(body);
        var request = HttpRequest.newBuilder()
            .uri(URI.create(config.endpoint() + path))
            .timeout(config.timeout())
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("X-TeaQL-License", config.licenseKey())
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

        try {
            var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new TeaQLException("HTTP " + response.statusCode() + ": " + response.body());
            }
            return response.body();
        } catch (IOException | InterruptedException e) {
            throw new TeaQLException("Request failed: " + path, e);
        }
    }

    // ─── AutoCloseable ─────────────────────────────────────────

    @Override
    public void close() {
        // HttpClient is immutable and shared; nothing to close.
        // Kept for future resource management (connection pools, etc.).
    }

    // ─── Builder ────────────────────────────────────────────────

    public static final class Builder {

        private String  endpoint;
        private Path    licenseFile;
        private String  licenseKeyOverride;
        private Duration timeout = Duration.ofSeconds(30);

        private Builder() {}

        /**
         * TeaQL API endpoint, e.g. {@code "https://us-east.api.teaql.io"}.
         */
        public Builder endpoint(String endpoint) {
            this.endpoint = endpoint;
            return this;
        }

        /**
         * Path to the TeaQL license file.
         * The file content is read and sent as the {@code X-TeaQL-License} header.
         */
        public Builder licenseFile(Path licenseFile) {
            this.licenseFile = licenseFile;
            return this;
        }

        /**
         * Read license from the default location:
         * <ol>
         *   <li>Environment variable {@code TEAQL_LICENSE} (path to license file)</li>
         *   <li>Fallback: {@code ~/.teaql/license}</li>
         * </ol>
         */
        public Builder licenseFile() {
            String envPath = System.getenv("TEAQL_LICENSE");
            if (envPath != null && !envPath.isBlank()) {
                this.licenseFile = Path.of(envPath);
            } else {
                this.licenseFile = Path.of(System.getProperty("user.home"), ".teaql", "license");
            }
            return this;
        }

        /**
         * Set the license key directly as a raw string
         * (for environments where a file is not available).
         */
        public Builder licenseKey(String licenseKey) {
            this.licenseKeyOverride = licenseKey;
            return this;
        }

        /**
         * HTTP connect & read timeout (default 30 s).
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public TeaQLClient build() {
            if (endpoint == null || endpoint.isBlank()) {
                throw new IllegalArgumentException("endpoint is required");
            }
            String licenseKey;
            if (licenseKeyOverride != null) {
                licenseKey = licenseKeyOverride;
            } else if (licenseFile != null) {
                try {
                    licenseKey = Files.readString(licenseFile).trim();
                } catch (IOException e) {
                    throw new IllegalArgumentException("Cannot read license file: " + licenseFile, e);
                }
            } else {
                licenseKey = "";
            }
            var config = new TeaQLConfig(endpoint, licenseKey, timeout);
            return new TeaQLClient(config);
        }
    }
}
