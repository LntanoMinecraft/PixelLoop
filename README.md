PixelLoop（灵动帧）

PixelLoop（灵动帧） 是一款面向 Android 平台的录屏与视频转 GIF 创作工具。它将屏幕录制、视频剪辑、画面裁剪、GIF 编码和作品分享整合在一个简洁而富有表现力的工作流中，让动态内容的捕获与创作变得更加轻松。

无论是记录应用操作、制作教程动图、捕获游戏精彩瞬间，还是将视频片段转换为 GIF，PixelLoop 都希望提供一套直观、高效且贴近 Android 原生体验的解决方案。

---

✨ 核心功能

📱 屏幕录制

- 使用 Android 系统级屏幕捕获能力录制屏幕内容。
- 支持录制倒计时与录制状态显示。
- 通过前台服务保持录制过程。
- 支持 Android 16 实时通知体验，方便在其他应用中查看录制状态。
- 支持通过快捷设置磁贴快速启动录制。

🎬 视频导入与编辑

- 支持从本地选择 MP4 视频。
- 提供视频预览与时间轴缩略图。
- 支持精确调整视频片段的起始与结束位置。
- 支持交互式画面区域裁剪。
- 提供多种画面比例选择，包括自由比例、1:1、16:9、9:16 和 4:3。
- 支持自定义 GIF 输出帧率、分辨率和播放速度。

🎨 GIF 制作与导出

- 将录制内容或导入的视频转换为 GIF 动图。
- 使用本地 GIF 编码引擎完成帧提取、裁剪、缩放与编码。
- 采用 256 色调色板与 LZW 编码。
- 支持后台处理与进度反馈。
- 生成完成后可预览、保存和分享 GIF。

🗂️ 本地作品管理

- 内置作品库，集中管理生成的 GIF。
- 保存作品缩略图与相关参数信息。
- 支持查看作品信息、预览和再次分享。
- 支持将 GIF 保存到系统图库。

⚡ 批量处理

- 支持添加多个视频到处理队列。
- 后台队列管理与处理进度显示。
- 方便连续制作多个 GIF，无需逐个重复操作。

🎨 原生视觉体验

- 基于 Material Design 3 Expressive 设计理念。
- 支持 Android 12 及以上设备的动态壁纸取色。
- 提供深色模式、浅色模式及主题切换。
- 采用 Compose 动画与硬件加速图形效果。
- 适配手机、平板及折叠屏等不同尺寸设备。

---

🛠️ 技术栈

技术| 用途
Kotlin| 主要开发语言
Jetpack Compose| 声明式 UI 构建
Material 3| 界面设计与组件
Android MediaProjection| 系统屏幕捕获
Android MediaCodec / MediaExtractor| 视频处理相关能力
Room| 本地作品数据管理
Kotlin Coroutines| 异步任务与后台处理
Android Foreground Service| 录屏与编码任务管理
Gradle Kotlin DSL| 项目构建配置
GitHub Actions| 自动化构建与发布

---

📦 下载与安装

前往 GitHub Releases 页面获取最新版本：

"下载 PixelLoop" (https://github.com/LntanoMinecraft/PixelLoop/releases)

下载对应版本的 APK 文件，在 Android 设备上安装即可。

首次使用屏幕录制功能时，需要按照 Android 系统提示授权屏幕捕获。

---

📋 系统要求

- 操作系统：Android 7.0（API 24）及以上。
- 推荐使用较新的 Android 版本，以获得更完整的系统体验。
- 屏幕录制功能需要系统屏幕捕获授权。
- Android 16 实时通知等功能需要对应系统版本支持。

---

🏗️ 自动化构建

本项目使用 GitHub Actions 自动构建 Android APK。

当前工作流支持：

- Debug 构建：普通分支更新、Pull Request 或手动触发时执行。
- Release 构建：推送符合 "v*" 格式的版本标签时执行。
- 自动签名：通过 GitHub Actions Secrets 安全读取签名密钥。
- 自动发布：Release 构建成功后，将 APK 上传至对应 GitHub Release。

版本发布示例：

v6.0

构建工作流：

".github/workflows/android-build.yml"

---

📂 项目信息

项目| 信息
项目名称| PixelLoop
中文名称| 灵动帧
Android 包名| "com.aistudio.pixelloop.mrxkqv"
当前版本| 6.0
最低 Android 版本| Android 7.0（API 24）
开发语言| Kotlin
默认分支| "main"

---

💡 项目愿景

PixelLoop 希望让录屏与 GIF 创作不再需要在多个工具之间反复切换。

从捕获屏幕动态，到精确编辑视频片段，再到生成、管理和分享 GIF，整个过程都可以在 Android 设备上完成。

Capture. Create. Loop.

让每一个值得记录的瞬间，都能灵动呈现。

---

🔗 项目链接

- GitHub 仓库："LntanoMinecraft/PixelLoop" (https://github.com/LntanoMinecraft/PixelLoop)
- Releases："版本下载与更新" (https://github.com/LntanoMinecraft/PixelLoop/releases)
- Actions："自动化构建记录" (https://github.com/LntanoMinecraft/PixelLoop/actions)

---

## 开源许可证

Copyright (C) 2026 LntanoMinecraft

PixelLoop 采用 **GNU 通用公共许可证第三版（GNU GPL-3.0）** 开源。

你可以在遵守 GPL-3.0 许可证条款的前提下，自由使用、复制、修改和分发本项目。

如果你分发修改后的版本，也必须遵循 GPL-3.0 的相关要求，包括向接收者提供相应的源代码。

完整许可证内容请参阅 [LICENSE](LICENSE) 文件。

## AI 开发说明

本项目由 Google AI Studio 中的 **Gemini 4 Argon** 完成构建。
