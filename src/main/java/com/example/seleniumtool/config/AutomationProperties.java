package com.example.seleniumtool.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "automation")
public class AutomationProperties {

    /**
     * 定时执行配置。
     */
    @Valid
    private final Schedule schedule = new Schedule();

    /**
     * 浏览器启动与页面停留配置。
     */
    @Valid
    private final Browser browser = new Browser();

    /**
     * CookieCloud 连接配置。
     */
    @Valid
    private final CookieCloud cookieCloud = new CookieCloud();

    @Valid
    private final StartupNotification startupNotification = new StartupNotification();

    /**
     * 是否在应用启动后立即执行一次任务。
     */
    private boolean runOnStartup = true;

    /**
     * 需要按顺序访问的目标站点列表。
     */
    @Valid
    @NotEmpty
    private List<Target> targets = new ArrayList<>();

    public Schedule getSchedule() {
        return schedule;
    }

    public Browser getBrowser() {
        return browser;
    }

    public CookieCloud getCookieCloud() {
        return cookieCloud;
    }

    public StartupNotification getStartupNotification() {
        return startupNotification;
    }

    public boolean isRunOnStartup() {
        return runOnStartup;
    }

    public void setRunOnStartup(boolean runOnStartup) {
        this.runOnStartup = runOnStartup;
    }

    public List<Target> getTargets() {
        return targets;
    }

    public void setTargets(List<Target> targets) {
        this.targets = targets;
    }

    public static class Schedule {
        @NotBlank
        private String cron = "0 0 9 * * *";

        @NotBlank
        private String zone = "Asia/Shanghai";

        public String getCron() {
            return cron;
        }

        public void setCron(String cron) {
            this.cron = cron;
        }

        public String getZone() {
            return zone;
        }

        public void setZone(String zone) {
            this.zone = zone;
        }
    }

    public static class Browser {
        private boolean headless = true;
        private boolean noSandbox = true;
        private boolean disableDevShmUsage = true;
        @Min(1)
        private long pageStaySeconds = 60;
        private Duration pageLoadTimeout = Duration.ofSeconds(60);
        /**
         * 浏览器可执行文件路径；本地通常可不填，Docker 中建议显式指定。
         */
        private String binaryPath;
        private String driverPath;
        private List<String> arguments = new ArrayList<>();

        public boolean isHeadless() {
            return headless;
        }

        public void setHeadless(boolean headless) {
            this.headless = headless;
        }

        public boolean isNoSandbox() {
            return noSandbox;
        }

        public void setNoSandbox(boolean noSandbox) {
            this.noSandbox = noSandbox;
        }

        public boolean isDisableDevShmUsage() {
            return disableDevShmUsage;
        }

        public void setDisableDevShmUsage(boolean disableDevShmUsage) {
            this.disableDevShmUsage = disableDevShmUsage;
        }

        public long getPageStaySeconds() {
            return pageStaySeconds;
        }

        public void setPageStaySeconds(long pageStaySeconds) {
            this.pageStaySeconds = pageStaySeconds;
        }

        public Duration getPageLoadTimeout() {
            return pageLoadTimeout;
        }

        public void setPageLoadTimeout(Duration pageLoadTimeout) {
            this.pageLoadTimeout = pageLoadTimeout;
        }

        public String getBinaryPath() {
            return binaryPath;
        }

        public void setBinaryPath(String binaryPath) {
            this.binaryPath = binaryPath;
        }

        public String getDriverPath() {
            return driverPath;
        }

        public void setDriverPath(String driverPath) {
            this.driverPath = driverPath;
        }

        public List<String> getArguments() {
            return arguments;
        }

        public void setArguments(List<String> arguments) {
            this.arguments = arguments;
        }
    }

    public static class CookieCloud {
        @NotBlank
        private String url = "https://CookieCloud地址/cookiecloud/get/";
        /**
         * CookieCloud 的访问 key。
         */
        private String key = "CookieCloud 的访问 key";
        /**
         * CookieCloud 的访问密码。
         */
        private String password = "CookieCloud 的访问密码。";
        private boolean enabled = true;
        private Duration refreshInterval = Duration.ofHours(1);
        private String cacheFile = "cookiecloud-cache.json";

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getRefreshInterval() {
            return refreshInterval;
        }

        public void setRefreshInterval(Duration refreshInterval) {
            this.refreshInterval = refreshInterval;
        }

        public String getCacheFile() {
            return cacheFile;
        }

        public void setCacheFile(String cacheFile) {
            this.cacheFile = cacheFile;
        }
    }

    public static class Target {
        @NotBlank
        private String name;

        @NotBlank
        private String url;

        /**
         * 指定从 CookieCloud 过滤 Cookie 时使用的域名；为空时从 URL 自动提取。
         */
        private String cookieDomain;
        private String warmupPath = "/favicon.ico";

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getCookieDomain() {
            return cookieDomain;
        }

        public void setCookieDomain(String cookieDomain) {
            this.cookieDomain = cookieDomain;
        }

        public String getWarmupPath() {
            return warmupPath;
        }

        public void setWarmupPath(String warmupPath) {
            this.warmupPath = warmupPath;
        }
    }

    public static class StartupNotification {
        private boolean enabled = true;
        @NotBlank
        private String webhookUrl = "webhook地址";
        @NotBlank
        private String message = "selenium-tool启动成功";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getWebhookUrl() {
            return webhookUrl;
        }

        public void setWebhookUrl(String webhookUrl) {
            this.webhookUrl = webhookUrl;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}
