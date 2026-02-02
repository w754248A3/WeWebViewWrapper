# WeWebViewWrapper

这是一个完全面向云端构建的 Android 项目（利用 GitHub Actions），集成了 WebView 包装器功能和私有存储服务。

## 核心功能

### 1. WebView 包装器 (核心)
本应用作为一个 WebView 容器，支持以下高级特性：
- **离线优先**: 通过拦截请求，优先加载 `assets` 中的本地网页资源，无需网络连接。
- **全屏体验**: 支持网页视频全屏播放，自动隐藏系统 UI 并旋转屏幕。
- **Service Worker 支持**: 拦截 Service Worker 请求，确保 PWA 应用的离线能力。
- **文件选择与目录授权**: 
  - 支持标准文件选取 (`<input type="file">`)。
  - **标准 Web API 支持**: 目前原生支持 `window.showOpenFilePicker`。
  - **注意**: `window.showSaveFilePicker` 和 `window.showDirectoryPicker` 在当前 Android WebView 版本下可能无法正常触发拦截（预计需要 Android 16 / WebView 142+），建议暂通过 `<input type="file" accept=".directory">` 触发目录选择。
  - **目录授权**: 支持通过 `<input type="file" accept=".directory">` 触发文件夹选择，Java 层会自动锁定并持久化该目录的访问权限。
  - **持久化**: 授权后的目录权限在应用重启后依然有效，Java 层会静默保存授权记录。
- **错误日志**: 内置运行时错误日志捕获与显示工具栏，方便调试。

### 2. DocumentsProvider 存储服务
本应用提供了一个 `DocumentsProvider`，允许其他应用安全访问本应用的私有存储。
- **Authority**: `com.wewebviewwrapper.provider`
- **支持操作**: 读取、写入、创建、删除、重命名。
- **存储路径**: 应用私有目录 (`/data/user/0/com.wewebviewwrapper/files`)。

## 开发者指南 (Web 端)

### 目录授权集成
要在您的网页中请求用户授权访问某个文件夹，只需添加以下 HTML 元素：
```html
<input type="file" accept=".directory" onchange="onDirPicked(this.files)">
```
当用户选择文件夹并确认授权后，Java 层会锁定该目录的**持久访问权限**，并返回该目录下的文件列表。

### 私有存储访问
推荐使用标准的 **Origin Private File System (OPFS)** API 进行网页私有数据的读写，无需额外权限。

## 如何构建

项目支持云端自动化构建和本地环境构建。

### 1. 本地构建要求
- **JDK**: 17 (推荐使用 Temurin)
- **Android SDK**: API 34 (Build Tools 34.0.0)
- **Gradle**: 8.2 (项目已内置 Gradle Wrapper)
- **Web 资源**: 构建前需手动将网页前端资源解压至 `app/src/main/assets/` 目录中。

### 2. 本地构建步骤
```bash
# 生成/同步 Gradle Wrapper
./gradlew wrapper --gradle-version 8.2

# 执行 Release 构建
./gradlew assembleRelease
```

### 3. GitHub Actions 自动化构建
每次推送代码到 `main` 或 `master` 分支，GitHub Actions 会自动执行以下流程：
1. **自动下载资源**: 从仓库的 `AssetStorage` Release 中下载最新的 `dist.zip` 并部署到 `assets`。
2. **签署与发布**: 
   - 使用 GitHub Secrets (`SIGNING_KEY`, `ALIAS`, `KEY_STORE_PASSWORD`, `KEY_PASSWORD`) 自动签署 APK。
   - 签署后的 APK 会自动发布在项目的 [Releases](../../releases) 页面。

## 使用说明
1. 安装 APK 后，打开应用即进入本地网页。
2. 底部工具栏提供后退、前进、主页和日志查看功能（下滑网页可自动隐藏工具栏）。
3. 在其他应用中选择文件时，可在“浏览”中找到 “Private Storage”，即本应用提供的私有空间。

---
*Created with ❤️ via Cloud-native Android Development Flow.*
