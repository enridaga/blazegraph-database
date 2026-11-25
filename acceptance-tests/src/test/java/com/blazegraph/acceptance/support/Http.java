package com.blazegraph.acceptance.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Minimal HTTP helper based on {@link java.net.http.HttpClient}, so the tests do
 * not depend on the HTTP stack (Jetty) shipped with Blazegraph. Redirects are
 * never followed automatically.
 */
public final class Http {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final Duration TIMEOUT = Duration.ofSeconds(120);

    private Http() {
    }

    /** Ordered query/form parameters. A {@code null} value encodes a bare flag (e.g. {@code ?ESTCARD}). */
    public static Map<String, String> params(String... kv) {
        if (kv.length % 2 != 0) {
            throw new IllegalArgumentException("key/value pairs expected");
        }
        Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    public static String encode(Map<String, String> params) {
        return params.entrySet().stream()
                .map(e -> enc(e.getKey()) + (e.getValue() == null ? "" : "=" + enc(e.getValue())))
                .collect(Collectors.joining("&"));
    }

    public static String withQuery(String url, Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + encode(params);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    public static HttpResponse<String> get(String url, String... headers) {
        return send(builder(url, headers).GET());
    }

    public static HttpResponse<String> get(String url, Map<String, String> params, String... headers) {
        return get(withQuery(url, params), headers);
    }

    public static HttpResponse<String> postForm(String url, Map<String, String> form, String... headers) {
        return send(builder(url, headers)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(encode(form))));
    }

    public static HttpResponse<String> post(String url, String contentType, String body, String... headers) {
        return send(builder(url, headers)
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)));
    }

    public static HttpResponse<String> post(String url, String contentType, byte[] body, String... headers) {
        return send(builder(url, headers)
                .header("Content-Type", contentType)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body)));
    }

    public static HttpResponse<String> postEmpty(String url, String... headers) {
        return send(builder(url, headers).POST(HttpRequest.BodyPublishers.noBody()));
    }

    public static HttpResponse<String> put(String url, String contentType, String body, String... headers) {
        return send(builder(url, headers)
                .header("Content-Type", contentType)
                .PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)));
    }

    public static HttpResponse<String> delete(String url, String... headers) {
        return send(builder(url, headers).DELETE());
    }

    /** A multipart/form-data body; each part is {name, contentType, content}. */
    public static HttpResponse<String> postMultipart(String url, String[]... parts) {
        String boundary = "----acceptance" + UUID.randomUUID().toString().replace("-", "");
        StringBuilder sb = new StringBuilder();
        for (String[] p : parts) {
            sb.append("--").append(boundary).append("\r\n")
              .append("Content-Disposition: form-data; name=\"").append(p[0])
              .append("\"; filename=\"").append(p[0]).append(".dat\"\r\n")
              .append("Content-Type: ").append(p[1]).append("\r\n\r\n")
              .append(p[2]).append("\r\n");
        }
        sb.append("--").append(boundary).append("--\r\n");
        return post(url, "multipart/form-data; boundary=" + boundary, sb.toString());
    }

    private static HttpRequest.Builder builder(String url, String... headers) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT);
        if (headers.length % 2 != 0) {
            throw new IllegalArgumentException("header name/value pairs expected");
        }
        for (int i = 0; i < headers.length; i += 2) {
            b.header(headers[i], headers[i + 1]);
        }
        return b;
    }

    private static HttpResponse<String> send(HttpRequest.Builder b) {
        try {
            return CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    /** The media type of the response, without parameters, lower case. */
    public static String mediaType(HttpResponse<?> r) {
        Optional<String> ct = r.headers().firstValue("Content-Type");
        return ct.map(s -> s.split(";")[0].trim().toLowerCase()).orElse("");
    }

    public static String header(HttpResponse<?> r, String name) {
        return r.headers().firstValue(name).orElse(null);
    }
}
