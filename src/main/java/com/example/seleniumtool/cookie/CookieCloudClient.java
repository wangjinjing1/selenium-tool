package com.example.seleniumtool.cookie;

import com.example.seleniumtool.config.AutomationProperties;
import com.example.seleniumtool.service.AutomationAlertState;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

@Component
public class CookieCloudClient {

    private static final Logger log = LoggerFactory.getLogger(CookieCloudClient.class);

    private final AutomationProperties properties;
    private final AutomationAlertState automationAlertState;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public CookieCloudClient(
        AutomationProperties properties,
        AutomationAlertState automationAlertState,
        RestTemplateBuilder builder,
        ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.automationAlertState = automationAlertState;
        this.restTemplate = builder.build();
        this.objectMapper = objectMapper;
    }

    public List<CookieCloudCookie> getCookiesForDomain(String targetUrl, String configuredDomain) {
        if (!properties.getCookieCloud().isEnabled()) {
            log.info("CookieCloud 已禁用，跳过目标 {} 的 Cookie 解析", targetUrl);
            return List.of();
        }

        JsonNode body = getCookiePayload();
        if (body == null || body.isNull()) {
            log.warn("CookieCloud 返回空数据，目标 {}", targetUrl);
            return List.of();
        }

        JsonNode cookieData = body.path("cookie_data");
        if (cookieData.isMissingNode() || cookieData.isNull() || !cookieData.isObject()) {
            log.warn("CookieCloud 返回结果中缺少 cookie_data，目标 {}", targetUrl);
            return List.of();
        }

        String domain = resolveDomain(targetUrl, configuredDomain);
        Set<String> allowedDomains = buildAllowedDomains(domain);
        log.info("开始为目标 [{}] 解析 Cookie，配置域名 [{}]，允许匹配域名 {}", targetUrl, configuredDomain, allowedDomains);

        List<CookieCloudCookie> matches = collectCookiesFromCookieData(cookieData, allowedDomains);
        log.info("域名 [{}] 共解析到 {} 个 Cookie", domain, matches.size());
        if (!matches.isEmpty()) {
            log.info("域名 [{}] 的 Cookie Header: {}", domain, buildCookieHeader(matches));
        }
        return matches;
    }

    public String buildCookieHeader(List<CookieCloudCookie> cookies) {
        StringJoiner joiner = new StringJoiner("; ");
        for (CookieCloudCookie cookie : cookies) {
            joiner.add(cookie.getName() + "=" + cookie.getValue());
        }
        return joiner.toString();
    }

    private synchronized JsonNode getCookiePayload() {
        Path cacheFile = resolveCacheFile();
        JsonNode cachedPayload = readCache(cacheFile);
        if (cachedPayload != null && isCacheFresh(cacheFile)) {
            log.info("使用本地 CookieCloud 缓存 {}", cacheFile);
            return cachedPayload;
        }

        JsonNode remotePayload = fetchRemotePayload();
        if (remotePayload != null && !remotePayload.isNull()) {
            writeCache(cacheFile, remotePayload);
            return remotePayload;
        }

        if (cachedPayload != null) {
            log.warn("远端获取失败，回退到旧的 CookieCloud 缓存 {}", cacheFile);
            return cachedPayload;
        }

        return null;
    }

    private JsonNode fetchRemotePayload() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> requestBody = Map.of("password", properties.getCookieCloud().getPassword());
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                buildEndpointUrl(),
                HttpMethod.POST,
                new HttpEntity<>(requestBody, headers),
                String.class
            );
            log.info("CookieCloud 请求成功，状态码 {}", response.getStatusCode());
            return parsePayload(response.getBody());
        } catch (Exception ex) {
            automationAlertState.addStartupWarning(buildCookieCloudWarning(ex));
            log.warn("CookieCloud 调不通，将继续使用缓存或空 Cookie", ex);
            return null;
        }
    }

    private String buildEndpointUrl() {
        String url = properties.getCookieCloud().getUrl();
        String key = properties.getCookieCloud().getKey();
        if (!StringUtils.hasText(url)) {
            throw new IllegalStateException("CookieCloud 地址不能为空");
        }
        if (!StringUtils.hasText(key)) {
            return url;
        }
        if (url.contains("{key}")) {
            return url.replace("{key}", key);
        }
        if (url.endsWith("/")) {
            return url + key;
        }
        if (url.endsWith(key)) {
            return url;
        }
        return url + "/" + key;
    }

    private JsonNode parsePayload(String body) {
        if (!StringUtils.hasText(body)) {
            return null;
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception ex) {
            String preview = body.length() > 200 ? body.substring(0, 200) + "..." : body;
            log.warn("CookieCloud 返回内容不是合法 JSON: {}", preview);
            return null;
        }
    }

    private Path resolveCacheFile() {
        String configured = properties.getCookieCloud().getCacheFile();
        Path path = Paths.get(configured);
        return path.isAbsolute() ? path : path.toAbsolutePath().normalize();
    }

    private boolean isCacheFresh(Path cacheFile) {
        try {
            if (!Files.exists(cacheFile)) {
                return false;
            }
            Duration refreshInterval = properties.getCookieCloud().getRefreshInterval();
            if (refreshInterval == null || refreshInterval.isZero() || refreshInterval.isNegative()) {
                return false;
            }
            Instant lastModified = Files.getLastModifiedTime(cacheFile).toInstant();
            return lastModified.plus(refreshInterval).isAfter(Instant.now());
        } catch (Exception ex) {
            log.warn("检查 CookieCloud 缓存是否新鲜时失败 {}", cacheFile, ex);
            return false;
        }
    }

    private JsonNode readCache(Path cacheFile) {
        try {
            if (!Files.exists(cacheFile)) {
                return null;
            }
            return objectMapper.readTree(Files.readString(cacheFile));
        } catch (Exception ex) {
            log.warn("读取 CookieCloud 缓存失败 {}", cacheFile, ex);
            return null;
        }
    }

    private void writeCache(Path cacheFile, JsonNode payload) {
        try {
            Path parent = cacheFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(cacheFile, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload));
            log.info("CookieCloud 缓存已更新 {}", cacheFile);
        } catch (Exception ex) {
            log.warn("写入 CookieCloud 缓存失败 {}", cacheFile, ex);
        }
    }

    private List<CookieCloudCookie> collectCookiesFromCookieData(JsonNode cookieData, Set<String> allowedDomains) {
        List<CookieCloudCookie> output = new ArrayList<>();
        cookieData.fields().forEachRemaining(entry -> {
            String cookieDataDomain = normalizeDomain(entry.getKey());
            if (!domainMatches(cookieDataDomain, allowedDomains)) {
                return;
            }

            JsonNode cookiesNode = entry.getValue();
            if (!cookiesNode.isArray()) {
                return;
            }

            for (JsonNode node : cookiesNode) {
                CookieCloudCookie cookie = toCookie(node, cookieDataDomain);
                if (cookie != null) {
                    output.add(cookie);
                }
            }
        });
        return output;
    }

    private CookieCloudCookie toCookie(JsonNode node, String fallbackDomain) {
        String domain = firstText(node, "domain", "host", "site");
        if (!StringUtils.hasText(domain)) {
            domain = fallbackDomain;
        }
        String name = firstText(node, "name");
        String value = firstText(node, "value");
        if (!StringUtils.hasText(domain) || !StringUtils.hasText(name) || value == null) {
            return null;
        }

        String path = StringUtils.hasText(firstText(node, "path")) ? firstText(node, "path") : "/";
        boolean secure = firstBoolean(node, "secure", "isSecure");
        boolean httpOnly = firstBoolean(node, "httpOnly", "http_only");
        return new CookieCloudCookie(domain, name, value, path, secure, httpOnly);
    }

    private String firstText(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode child = node.get(name);
            if (child != null && !child.isNull()) {
                return child.asText();
            }
        }
        return null;
    }

    private boolean firstBoolean(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode child = node.get(name);
            if (child != null && !child.isNull()) {
                return child.asBoolean(false);
            }
        }
        return false;
    }

    private String resolveDomain(String targetUrl, String configuredDomain) {
        if (StringUtils.hasText(configuredDomain)) {
            return normalizeDomain(configuredDomain);
        }
        return normalizeDomain(URI.create(targetUrl).getHost());
    }

    private Set<String> buildAllowedDomains(String domain) {
        Set<String> domains = new HashSet<>();
        String normalized = normalizeDomain(domain);
        if (!StringUtils.hasText(normalized)) {
            return domains;
        }

        domains.add(normalized);
        String[] segments = normalized.split("\\.");
        for (int i = 1; i < segments.length - 1; i++) {
            domains.add(String.join(".", java.util.Arrays.copyOfRange(segments, i, segments.length)));
        }
        return domains;
    }

    private boolean domainMatches(String cookieDomain, Set<String> allowedDomains) {
        String normalized = normalizeDomain(cookieDomain);
        if (!StringUtils.hasText(normalized)) {
            return false;
        }
        if (allowedDomains.contains(normalized)) {
            return true;
        }
        return allowedDomains.stream().anyMatch(allowed -> normalized.endsWith("." + allowed));
    }

    private String normalizeDomain(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT).trim();
        while (normalized.startsWith(".")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    private String buildCookieCloudWarning(Exception ex) {
        String message = ex.getMessage();
        if (!StringUtils.hasText(message)) {
            message = ex.getClass().getSimpleName();
        }
        return "CookieCloud 调不通: " + message.replace('\r', ' ').replace('\n', ' ');
    }
}
