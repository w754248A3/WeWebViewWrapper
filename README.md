# WeWebViewWrapper

这是一个完全面向云端构建的 Android 项目（利用 GitHub Actions），集成了 WebView 包装器功能和私有存储服务。

## 核心功能

### 1. WebView 包装器 (核心)
本应用作为一个 WebView 容器，支持以下高级特性：
- **离线优先**: 通过拦截请求，优先加载 `assets` 中的本地网页资源，无需网络连接。
- **全屏体验**: 支持网页视频全屏播放，自动隐藏系统 UI 并旋转屏幕。
- **Service Worker 支持**: 拦截 Service Worker 请求，确保 PWA 应用的离线能力。
- **文件选择集成**: 将网页的文件选择请求 (`<input type="file">`) 桥接到 Android 系统文件选择器。
- **错误日志**: 内置运行时错误日志捕获与显示工具栏，方便调试。

### 2. DocumentsProvider 存储服务
本应用保留了一个 `DocumentsProvider`，允许其他 Android 应用（通过系统文件选择器）安全地访问本应用的私有存储空间。
- **Authority**: `com.wewebviewwrapper.provider`
- **支持操作**: 读取、写入、创建、删除、重命名文件和文件夹。
- **存储路径**: 应用私有目录 (`/data/user/0/com.wewebviewwrapper/files`)。

## 技术特性
- **纯 Java 编写**: 保持代码简洁易读。
- **Android 8.0+ 支持**: 适配 Android 14 (API 34) 规范，兼容至 Android 8.0 (API 26)。
- **云端构建**: 本地无需安装 Android 开发环境，完全依赖 GitHub Actions 进行构建、签署和发布。

## 如何构建与获取
1. **GitHub Actions**: 每次推送到 `main` 分支都会触发自动构建。
2. **自动发布**: 
   - 推送到 `main` 分支会自动更新 `latest` Release 页面。
   - 推送版本标签（如 `v1.3`）会创建一个正式的 Release。
3. **下载**: 请前往项目的 [Releases](../../releases) 页面下载签署后的 APK。

## 使用说明
1. 安装 APK 后，打开应用即进入本地网页。
2. 底部工具栏提供后退、前进、主页和日志查看功能（下滑网页可自动隐藏工具栏）。
3. 在其他应用中选择文件时，可在“浏览”中找到 “Private Storage”，即本应用提供的私有空间。

---
*Created with ❤️ via Cloud-native Android Development Flow.*
