package com.example.seleniumtool.browser;

import com.example.seleniumtool.config.AutomationProperties;
import java.time.Duration;
import org.openqa.selenium.PageLoadStrategy;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeDriverService;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class WebDriverFactory {

    private final AutomationProperties properties;

    public WebDriverFactory(AutomationProperties properties) {
        this.properties = properties;
    }

    /**
     * 按配置创建 ChromeDriver，兼容本地调试和 Linux 容器运行。
     */
    public RemoteWebDriver createChromeDriver() {
        ChromeOptions options = new ChromeOptions();
        options.setPageLoadStrategy(PageLoadStrategy.NORMAL);

        if (properties.getBrowser().isHeadless()) {
            options.addArguments("--headless=new");
        }
        if (properties.getBrowser().isNoSandbox()) {
            options.addArguments("--no-sandbox");
        }
        if (properties.getBrowser().isDisableDevShmUsage()) {
            options.addArguments("--disable-dev-shm-usage");
        }
        if (StringUtils.hasText(properties.getBrowser().getBinaryPath())) {
            options.setBinary(properties.getBrowser().getBinaryPath());
        }
        options.addArguments(properties.getBrowser().getArguments());

        ChromeDriver driver;
        if (StringUtils.hasText(properties.getBrowser().getDriverPath())) {
            ChromeDriverService service = new ChromeDriverService.Builder()
                .usingDriverExecutable(new java.io.File(properties.getBrowser().getDriverPath()))
                .build();
            driver = new ChromeDriver(service, options);
        } else {
            driver = new ChromeDriver(options);
        }
        Duration pageLoadTimeout = properties.getBrowser().getPageLoadTimeout();
        driver.manage().timeouts().pageLoadTimeout(pageLoadTimeout);
        return driver;
    }
}
