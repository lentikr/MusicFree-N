# MusicFree Native Android Demo (Kotlin + Compose M3E + QuickJS)

这是一个基于 **Kotlin + Jetpack Compose (Material 3 Expressive 1.5.0) + QuickJS (`quickjs-kt`) + OkHttp + Media3** 构建的 Android 原生原型工程，用于验证在原生 Android 应用中**在线导入并运行 MusicFree JS 插件**的完整技术链路。

## 核心特性

1. **Material 3 Expressive (M3E) 原生界面**
   - 采用 `androidx.compose.material3:material3:1.5.0` 的 `MaterialExpressiveTheme`、`MotionScheme.expressive()`、`LoadingIndicator`、Dynamic Color 与 Edge-to-Edge 布局（参考 [Compose Material 3 1.5.0 发布说明](https://m3.material.io/blog/compose-material-3-is-stable)）。
2. **MusicFree 插件在线导入**
   - 自动识别单个 `.js` 插件地址或标准 `plugins.json` 订阅清单（`{ "plugins": [{ "name", "url", "version" }] }`，参考 [MusicFreePlugins/plugins.json](https://github.com/maotoumao/MusicFreePlugins/blob/master/plugins.json)）。
   - 已导入插件自动持久化至 `filesDir/mf_plugins/`，重启应用自动恢复。
3. **QuickJS 沙箱与白名单 `require()` 注入**
   - 基于 [`dokar3/quickjs-kt`](https://github.com/dokar3/quickjs-kt)（v1.0.15）与 Kotlin 协程异步桥接。
   - 内置 MusicFree 常见白名单模块兼容层：
     - `axios` → 桥接至 Kotlin **OkHttp**（支持自定义 Headers / Referer / User-Agent / CookieJar / params / POST JSON & Form）
     - `cheerio` → 桥接至 Kotlin **Jsoup**（支持常见 CSS 选择器、`.text()`、`.attr()`、`.each()`、`.find()`）
     - `crypto-js` → 桥接至 Android 原生 `MessageDigest` / `Mac` / `Cipher`（MD5、SHA1、SHA256、HmacSHA1/256、AES-CBC/ECB）
     - `qs`、`he`、`dayjs`、`big-integer`、`@react-native-cookies/cookies`、`Buffer`、`atob`/`btoa`、`env.getUserVariables()`
4. **MusicFree 协议接口联调与 Media3 试听**
   - 支持直接验证 `search(query, page, type)`、`getMediaSource(musicItem, quality)`、`getLyric(musicItem)`、`getTopLists()`。
   - 内置 `assets/demo_itunes_plugin.js`（符合 MusicFree CommonJS 规范的公开 iTunes 搜索插件），点击「载入内置验证源」即可零配置验证全链路并在 Media3 ExoPlayer 中实际出声试听。

## 快速运行

1. 解压后使用 **Android Studio (Ladybug / Meerkat 或更新版本)** 打开 `MusicFreeNativeDemo` 目录。
2. 等待 Gradle Sync 完成（需可访问 Google Maven 与 Maven Central）。
3. 连接真机或模拟器运行 `app` 模块：
   - **零配置验证**：在「插件」页点击 **载入内置验证源** → 点击 **去验证接口** → 点击 **调用 search()** → 点击任意曲目的 **测 getMediaSource** 并点击 **用 Media3 试听播放**。
   - **第三方插件验证**：在「插件」页粘贴任意 MusicFree `.js` 插件链接或 `plugins.json` 订阅链接，点击 **在线导入**，即可在「接口联调」页和「日志」页观察完整请求与返回。

## 已知局限与进阶扩展建议

- 当前 Demo 中的 `crypto-js`、`cheerio`、`dayjs` 为轻量级原生/JS Shim，已覆盖大部分常规音源插件；若个别重度混淆或复杂插件调用了冷门 API（如 `cheerio` 完整 DOM 修改或 `crypto-js` RC4/TripleDES），建议在前端用 `esbuild` 将官方 npm 包预打包为独立 `.js` 放入 `app/src/main/assets/vendor/` 并在 `MusicFreeSandboxBootstrap.kt` 中直接加载。
- 生产环境建议为每个插件分配独立 `QuickJs` 实例池，并补充 `userVariables` 配置表单界面。

## 使用 GitHub Actions 云端构建 APK

1. 将解压后的 `MusicFreeNativeDemo` 目录内文件推送到你的 GitHub 仓库（`main` 或 `master` 分支）。
2. 打开仓库的 **Actions** 页面，`.github/workflows/android-build.yml` 会自动触发（也可点击 **Run workflow** 手动触发）。
3. 工作流内置了 `gradle/actions/setup-gradle@v4`（Gradle 8.11.1），无需提前在本地生成 `gradle-wrapper.jar` 即可直接构建。
4. 构建完成后，在任务详情页底部的 **Artifacts** 中下载 `MusicFreeNativeDemo-debug-apk` 解压安装即可。
