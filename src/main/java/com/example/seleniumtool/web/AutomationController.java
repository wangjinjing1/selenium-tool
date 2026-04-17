package com.example.seleniumtool.web;

import com.example.seleniumtool.service.BrowserAutomationService;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/automation")
public class AutomationController {

    private final BrowserAutomationService browserAutomationService;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public AutomationController(BrowserAutomationService browserAutomationService) {
        this.browserAutomationService = browserAutomationService;
    }

    /**
     * 提供手动触发入口，避免 HTTP 请求线程被长时间阻塞。
     */
    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> runNow() {
        executorService.submit(browserAutomationService::executeOnce);
        return ResponseEntity.accepted().body(Map.of("accepted", true));
    }

    @PreDestroy
    public void shutdown() {
        executorService.shutdown();
    }
}
