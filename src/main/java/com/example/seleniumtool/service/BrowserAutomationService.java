package com.example.seleniumtool.service;

import com.example.seleniumtool.browser.WebDriverFactory;
import com.example.seleniumtool.config.AutomationProperties;
import com.example.seleniumtool.cookie.CookieCloudClient;
import com.example.seleniumtool.cookie.CookieCloudCookie;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.InvalidCookieDomainException;
import org.openqa.selenium.NoSuchSessionException;
import org.openqa.selenium.WebDriverException;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class BrowserAutomationService {

    private static final Logger log = LoggerFactory.getLogger(BrowserAutomationService.class);
    private static final String IDLE_URL = "https://www.baidu.com";

    private final AutomationProperties properties;
    private final WebDriverFactory webDriverFactory;
    private final CookieCloudClient cookieCloudClient;
    private final AutomationAlertState automationAlertState;
    private final WebhookNotificationService webhookNotificationService;
    private final HttpClient httpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    private RemoteWebDriver sharedDriver;

    public BrowserAutomationService(
        AutomationProperties properties,
        WebDriverFactory webDriverFactory,
        CookieCloudClient cookieCloudClient,
        AutomationAlertState automationAlertState,
        WebhookNotificationService webhookNotificationService
    ) {
        this.properties = properties;
        this.webDriverFactory = webDriverFactory;
        this.cookieCloudClient = cookieCloudClient;
        this.automationAlertState = automationAlertState;
        this.webhookNotificationService = webhookNotificationService;
    }

    public synchronized void executeOnce() {
        log.info("开始执行浏览器自动化任务，共 {} 个目标", properties.getTargets().size());
        RemoteWebDriver driver = getOrCreateDriver();
        boolean interrupted = false;
        for (AutomationProperties.Target target : properties.getTargets()) {
            if (!visitTarget(driver, target)) {
                interrupted = true;
                break;
            }
        }
        if (!interrupted && isDriverAlive(driver)) {
            keepBrowserOpen(driver);
        }
        log.info("浏览器自动化任务执行结束");
    }

    private boolean visitTarget(RemoteWebDriver driver, AutomationProperties.Target target) {
        log.info("开始打开目标 [{}] {}", target.getName(), target.getUrl());
        try {
            String warmupUrl = buildWarmupUrl(target);
            log.info("先访问预热地址 [{}] {}", target.getName(), warmupUrl);
            driver.get(warmupUrl);

            List<CookieCloudCookie> cookies = cookieCloudClient.getCookiesForDomain(
                target.getUrl(),
                target.getCookieDomain()
            );
            if (cookies.isEmpty()) {
                log.warn("目标 [{}] 未解析到任何 CookieCloud Cookie，配置域名 [{}]", target.getName(), target.getCookieDomain());
            } else {
                log.info("目标 [{}] 共解析到 {} 个 CookieCloud Cookie", target.getName(), cookies.size());
            }

            checkTargetStatus(target, cookies);

            int injectedCount = 0;
            for (CookieCloudCookie source : cookies) {
                try {
                    driver.manage().addCookie(
                        new Cookie.Builder(source.getName(), source.getValue())
                            .domain(source.getDomain())
                            .path(source.getPath())
                            .isSecure(source.isSecure())
                            .isHttpOnly(source.isHttpOnly())
                            .build()
                    );
                    injectedCount++;
                } catch (InvalidCookieDomainException ex) {
                    log.warn(
                        "跳过 Cookie [{}]，因为域名 [{}] 与当前站点不匹配",
                        source.getName(),
                        source.getDomain()
                    );
                }
            }
            log.info("目标 [{}] 成功注入 {} 个 Cookie", target.getName(), injectedCount);

            driver.navigate().to(target.getUrl());
            sleep(Duration.ofSeconds(properties.getBrowser().getPageStaySeconds()));
            log.info("目标 [{}] 执行完成", target.getName());
            return true;
        } catch (Exception ex) {
            if (isBrowserClosedException(ex)) {
                handleBrowserClosed(target, ex);
                return false;
            }
            log.error("目标 [{}] 执行失败 {}", target.getName(), target.getUrl(), ex);
            return true;
        }
    }

    private RemoteWebDriver getOrCreateDriver() {
        if (isDriverAlive(sharedDriver)) {
            return sharedDriver;
        }
        closeDriverQuietly(sharedDriver);
        sharedDriver = webDriverFactory.createChromeDriver();
        log.info("已创建新的浏览器会话");
        return sharedDriver;
    }

    private boolean isDriverAlive(RemoteWebDriver driver) {
        if (driver == null) {
            return false;
        }
        try {
            driver.getWindowHandles();
            return true;
        } catch (WebDriverException ex) {
            log.info("现有浏览器会话已失效，将自动创建新会话");
            return false;
        }
    }

    private void keepBrowserOpen(RemoteWebDriver driver) {
        try {
            driver.navigate().to(IDLE_URL);
            log.info("浏览器保持打开，当前停留在 {}", IDLE_URL);
        } catch (Exception ex) {
            if (isBrowserClosedException(ex)) {
                sharedDriver = null;
                log.info("浏览器在跳转到待机页面前已被关闭");
                return;
            }
            log.warn("浏览器跳转到待机页面失败 {}", IDLE_URL, ex);
        }
    }

    private String buildWarmupUrl(AutomationProperties.Target target) {
        URI uri = URI.create(target.getUrl());
        String warmupPath = target.getWarmupPath();
        if (!StringUtils.hasText(warmupPath)) {
            warmupPath = "/favicon.ico";
        }
        if (!warmupPath.startsWith("/")) {
            warmupPath = "/" + warmupPath;
        }
        return uri.getScheme() + "://" + uri.getHost() + warmupPath;
    }

    private void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("浏览器自动化任务被中断", ex);
        }
    }

    private void checkTargetStatus(AutomationProperties.Target target, List<CookieCloudCookie> cookies) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(target.getUrl()))
                .timeout(properties.getBrowser().getPageLoadTimeout())
                .GET();
            String cookieHeader = cookieCloudClient.buildCookieHeader(cookies);
            if (StringUtils.hasText(cookieHeader)) {
                requestBuilder.header("Cookie", cookieHeader);
            }

            HttpResponse<Void> response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.discarding());
            int statusCode = response.statusCode();
            log.info("目标 [{}] 状态检查返回 {}", target.getName(), statusCode);
            if (statusCode != 200) {
                automationAlertState.addTargetFailure(
                    "目标: " + target.getName() + "\nURL: " + target.getUrl() + "\n状态码: " + statusCode
                );
            }
        } catch (Exception ex) {
            automationAlertState.addTargetFailure(
                "目标: " + target.getName() + "\nURL: " + target.getUrl() + "\n异常: " + ex.getMessage()
            );
            log.warn("目标 [{}] 状态检查失败 {}", target.getName(), target.getUrl(), ex);
        }
    }

    private boolean isBrowserClosedException(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof NoSuchSessionException) {
                return true;
            }
            if (current instanceof WebDriverException && !(current instanceof InvalidCookieDomainException)) {
                String message = current.getMessage();
                if (StringUtils.hasText(message)) {
                    String normalized = message.toLowerCase();
                    if (normalized.contains("session deleted as the browser has closed the connection")
                        || normalized.contains("invalid session id")
                        || normalized.contains("not connected to devtools")) {
                        return true;
                    }
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private void handleBrowserClosed(AutomationProperties.Target target, Exception ex) {
        sharedDriver = null;
        webhookNotificationService.send(
            "浏览器已关闭",
            "执行过程中浏览器被手动关闭\n目标: " + target.getName() + "\nURL: " + target.getUrl()
        );
        log.warn("目标 [{}] 执行过程中浏览器被关闭 {}", target.getName(), target.getUrl(), ex);
    }

    @PreDestroy
    public synchronized void shutdown() {
        closeDriverQuietly(sharedDriver);
        sharedDriver = null;
    }

    private void closeDriverQuietly(RemoteWebDriver driver) {
        if (driver == null) {
            return;
        }
        try {
            driver.quit();
        } catch (Exception ex) {
            log.debug("关闭浏览器会话时发生异常", ex);
        }
    }
}
