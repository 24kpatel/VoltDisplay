package com.voltraceway.display;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

final class DisplayUrl {
    private DisplayUrl() {
    }

    static String normalize(String rawValue) {
        if (rawValue == null) {
            throw new IllegalArgumentException("Enter a display URL.");
        }

        String value = rawValue.trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Enter a display URL.");
        }

        if (!value.contains("://")) {
            value = "https://" + value;
        }

        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null || host.trim().isEmpty()) {
                throw new IllegalArgumentException("Enter a complete website address.");
            }

            String normalizedScheme = scheme.toLowerCase(Locale.CANADA);
            if (!"https".equals(normalizedScheme) && !"http".equals(normalizedScheme)) {
                throw new IllegalArgumentException("Only HTTP and HTTPS addresses are supported.");
            }

            return value;
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("The website address is not valid.");
        }
    }
}
