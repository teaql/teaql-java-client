package io.teaql.client;

import java.time.Duration;

/**
 * Immutable config record for {@link TeaQLClient}.
 *
 * @param endpoint   base URL, e.g. {@code "https://us-east.api.teaql.io"}
 * @param licenseKey raw license key string (sent as {@code X-TeaQL-License} header)
 * @param timeout    HTTP connect & read timeout
 */
public record TeaQLConfig(
    String endpoint,
    String licenseKey,
    Duration timeout
) {}
