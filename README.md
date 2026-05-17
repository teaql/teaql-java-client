# TeaQL Java Client

Java 17+ client for the TeaQL API, with a fluent builder-style entry point.

## Usage

```java
import io.teaql.client.TeaQLClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class Example {
    public static void main(String[] args) {
        var client = TeaQLClient.builder()
            .endpoint("https://us-east.api.teaql.io")
            .licenseFile(Path.of("/path/to/license"))
            .timeout(Duration.ofSeconds(30))
            .build();

        // Run a named query
        List<Map<String, Object>> users = client.query(
            "User",
            "list",
            Map.of("merchantId", "M001")
        );

        // Run a mutation
        Object result = client.mutate(
            "User",
            "create",
            Map.of("name", "Alice", "email", "alice@example.com")
        );

        // Health check
        boolean ok = client.ping();
    }
}
```

## Maven

```xml
<dependency>
  <groupId>io.teaql</groupId>
  <artifactId>teaql-java-client</artifactId>
  <version>0.1.0</version>
</dependency>
```

## Requirements

- Java 17 or later

## License

Apache-2.0
