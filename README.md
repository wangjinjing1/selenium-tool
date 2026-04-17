# selenium-tool

基于 Java、Spring Boot、Selenium 的定时浏览器任务工具，支持从 CookieCloud 拉取 Cookie，并通过 webhook 发送启动结果和异常告警。

## 功能概览

- 支持 Spring Boot 定时任务，按 `cron` 自动执行
- 支持 Selenium + Chrome/Chromium 自动打开页面、注入 Cookie、停留后退出
- 支持启动后立即执行一次任务
- 支持 CookieCloud 拉取 Cookie，并带本地缓存回退
- 支持通过 webhook 发送启动成功、启动失败、浏览器关闭等通知
- 支持 Docker 运行，默认直接使用 Jar 内置配置
- 提供手动触发接口 `POST /api/automation/run`

## 目录说明

- `src/main/resources/application.yml`
  项目默认配置文件，IDEA、本地运行、Docker 默认都使用这份配置
- `Dockerfile`
  构建运行镜像，基于 `selenium/standalone-chrome`
- `docker-compose.yml`
  本地快速启动容器
- `cookiecloud-cache.json`
  CookieCloud 本地缓存文件

## 配置示例

默认配置文件位置：

```yaml
src/main/resources/application.yml
```

示例：

```yaml
spring:
  application:
    name: selenium-tool
  main:
    banner-mode: "off"

logging:
  level:
    root: INFO
    com.example.seleniumtool: INFO

# 自动化任务主配置
automation:
  # 启动应用后是否立刻执行一次任务
  run-on-startup: true
  schedule:
    # Spring 六段式 cron，例如每天 9 点执行一次
    cron: "0 28 14 * * *"
    # 定时任务使用的时区
    zone: "Asia/Shanghai"
  browser:
    # true 表示无界面运行，服务器和 Docker 环境建议开启
    headless: false
    # Linux 容器环境通常需要开启
    no-sandbox: true
    # Docker 中共享内存较小时建议开启
    disable-dev-shm-usage: true
    # 每个页面打开后的停留时间，单位秒
    page-stay-seconds: 10
    # 页面加载超时时间
    page-load-timeout: 60s
    driver-path:
    # 额外的浏览器启动参数
    arguments:
      - "--window-size=1400,900"
      - "--disable-gpu"
  cookie-cloud:
    # 是否启用 CookieCloud 拉取 Cookie
    enabled: true
    # CookieCloud 接口地址
    url: "https://CookieCloud地址/cookiecloud/get/"
    # CookieCloud 访问 key
    key: "CookieCloud 访问 key"
    # CookieCloud 访问密码
    password: "CookieCloud 访问密码"
    # 要访问的目标网址列表，按顺序逐个执行
    refresh-interval: 1h
    cache-file: "cookiecloud-cache.json"
  startup-notification:
    enabled: true
    webhook-url: "webhook地址"
    message: "selenium-tool启动成功"
  targets:
    # 任务名称，仅用于日志识别
    - name: "qqq"
      # 实际访问地址
      url: "https://qqq.top/index.php"
      # Cookie 过滤域名；不填则从 url 自动提取
      cookie-domain: "qqq.top"
      warmup-path: "/favicon.ico"

```

## 配置说明

### `automation.run-on-startup`

- `true` 表示应用启动后立即执行一次自动化任务
- `false` 表示只等定时任务或手动触发

### `automation.schedule`

- `cron` 使用 Spring 六段式表达式
- `zone` 指定时区，例如 `Asia/Shanghai`

示例：

```yaml
cron: "0 0 9 * * *"
```

表示每天上午 9 点执行一次。

### `automation.browser`

- `headless`
  是否无界面运行。Docker 场景通常可设为 `false` 以便通过 noVNC 观察页面
- `no-sandbox`
  Linux / Docker 环境建议开启
- `disable-dev-shm-usage`
  Docker 共享内存较小时建议开启
- `page-stay-seconds`
  页面打开后停留秒数
- `page-load-timeout`
  页面状态检查与页面加载超时时间
- `binary-path`
  浏览器可执行文件路径。本地路径特殊时需要显式配置
- `driver-path`
  如需自定义驱动路径可填写
- `arguments`
  额外浏览器启动参数

### `automation.cookie-cloud`

- `enabled`
  是否启用 CookieCloud
- `url`
  CookieCloud 接口地址
- `key`
  CookieCloud 访问 key
- `password`
  CookieCloud 访问密码
- `refresh-interval`
  本地缓存刷新周期
- `cache-file`
  本地缓存文件路径

CookieCloud 当前行为：

- 优先请求远端 CookieCloud
- 远端成功时刷新本地缓存
- 远端失败时优先回退到本地缓存
- 如果 CookieCloud 调不通，不会导致应用启动失败
- CookieCloud 调不通会记录日志，并在启动成功 webhook 中附带告警信息

### `automation.startup-notification`

- `enabled`
  是否启用 webhook 通知
- `webhook-url`
  webhook 地址
- `message`
  启动成功时的主消息内容

当前通知策略：

- 应用启动成功后会发送 webhook
- 如果 CookieCloud 调不通，会在 webhook 中追加告警
- 如果目标站点状态检查失败，也会在 webhook 中追加失败摘要
- 如果 webhook 发送失败，应用启动会直接失败，Docker 日志中可看到失败原因
- 应用启动阶段抛异常时，会尝试发送启动失败通知

### `automation.targets`

每个 target 表示一个需要访问的目标页面。

- `name`
  任务名称，用于日志识别
- `url`
  实际访问页面
- `cookie-domain`
  Cookie 过滤域名；为空时从 `url` 自动提取
- `warmup-path`
  注入 Cookie 前先访问的预热路径，默认 `/favicon.ico`

## 本地运行

要求：

- JDK 17
- Maven 3.9+
- 本机已安装 Chrome 或 Chromium

启动：

```bash
mvn spring-boot:run
```

或者直接运行：

```text
com.example.seleniumtool.SeleniumToolApplication
```

## Docker 运行

### 构建镜像

```bash
docker build -t selenium-tool .
```

### 启动容器

```bash
docker run -d \
  --name selenium-tool \
  -e TZ=Asia/Shanghai \
  -p 7900:7900 \
  selenium-tool
```

或者：

```bash
docker compose up -d --build
```

说明：

- 镜像基于 `selenium/standalone-chrome`
- 容器启动时会先拉起基础镜像自带的 Selenium / noVNC 服务，再启动 Java 应用
- Docker 默认直接使用 Jar 内的 `application.yml`

### 覆盖默认配置

如果需要用宿主机配置覆盖 Jar 内默认配置，可以挂载一个自定义目录：

```bash
docker run -d \
  --name selenium-tool \
  -e TZ=Asia/Shanghai \
  -e SPRING_CONFIG_ADDITIONAL_LOCATION=optional:file:/app/config/ \
  -v $(pwd)/config:/app/config:ro \
  -p 7900:7900 \
  selenium-tool
```

例如把宿主机的 `config/application.yml` 挂进容器。

## noVNC / VNC

基础镜像 `selenium/standalone-chrome` 默认提供 VNC / noVNC 能力。

常见访问方式：

- noVNC：`http://localhost:7900`

如需更多 VNC 参数，按 `selenium/standalone-chrome` 官方环境变量配置即可。

## 手动触发

接口：

```bash
curl -X POST http://localhost:8080/api/automation/run
```

## 启动与失败行为

启动流程大致如下：

1. Spring Boot 启动
2. 如 `run-on-startup=true`，执行一次自动化任务
3. 汇总 CookieCloud 告警和目标检查失败信息
4. 发送启动成功 webhook
5. 如果 webhook 发送失败，应用启动失败

失败处理规则：

- `CookieCloud` 不通：记日志，不阻断启动，webhook 提示用户
- `webhook` 不通：记日志并抛错，应用启动失败
- 浏览器在运行中被手动关闭：发送告警 webhook，并在日志中记录

## 开发建议

- 本地调试优先先关掉定时任务，只保留手动触发
- Docker 环境建议保留 `no-sandbox` 和 `disable-dev-shm-usage`
- 如果目标站点依赖登录态，优先确认 CookieCloud 返回结构和域名过滤结果
