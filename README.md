# PixelLoop（灵动帧）

> 面向 Android 的本地屏幕录制与视频转 GIF 工具

[](https://github.com/LntanoMinecraft/PixelLoop)
[](https://github.com/LntanoMinecraft/PixelLoop/blob/main/LICENSE)
[](https://github.com/LntanoMinecraft/PixelLoop/releases)

## 项目简介

**PixelLoop（灵动帧）** 是一款完全在本地运行的 Android 屏幕录制与视频转 GIF 工具。支持直接录屏或导入本地视频，通过内置剪辑器自由裁剪画面、截取片段与调节帧率倍速，并离线编码导出为 GIF 动图。

## 核心功能

| 模块 | 功能说明 |
| :--- | :--- |
| **屏幕录制** | 基于系统 `MediaProjection` 捕获屏幕，支持录制前倒计时、实时状态展示与快捷设置磁贴一键启录 |
| **视频剪辑** | 导入本地 MP4 视频，支持自由比例 / `1:1` / `16:9` / `9:16` / `4:3` 裁剪、时间轴精剪、倍速调节与自定义参数 |
| **本地 GIF 编码** | 纯离线本地编码（无需上传云端），支持最高 60 FPS、多档输出分辨率预设、实时导出进度与后台处理 |
| **队列与作品库** | 支持多视频批量队列处理、本地作品库分类管理（GIF 作品 / 录屏原片）、最近记录与一键保存分享 |
| **视觉与交互** | 基于 Jetpack Compose 与 Material Design 3 Expressive 构建，配备弹性展开悬浮胶囊导航栏、Google Sans Flex 圆角字体、动态取色（Material You）、7 色色盘与深浅色切换 |

## 技术栈

* **语言与异步**：Kotlin、Kotlin Coroutines
* **界面与设计**：Jetpack Compose、Material Design 3 Expressive
* **媒体与编码**：MediaProjection、MediaCodec、MediaExtractor
* **数据与构建**：Room、Gradle

## 系统要求与安装

* **系统要求**：Android 7.0（API 24）及以上（部分系统特性与动态取色需较新 Android 版本）。
* **获取安装包**：前往 [GitHub Releases](https://github.com/LntanoMinecraft/PixelLoop/releases) 下载最新签名 APK 安装即可。

## 项目信息

* **项目名称**：PixelLoop（灵动帧）
* **应用包名**：`com.aistudio.pixelloop.mrxkqv`
* **项目主页**：[github.com/LntanoMinecraft/PixelLoop](https://github.com/LntanoMinecraft/PixelLoop)
* **维护者**：[LntanoMinecraft](https://github.com/LntanoMinecraft) · [酷安主页](https://www.coolapk.com/u/34054336)

## 开源许可证

Copyright (C) 2026 LntanoMinecraft

本项目采用 [GNU General Public License v3.0 (GPL-3.0)](https://github.com/LntanoMinecraft/PixelLoop/blob/main/LICENSE) 协议开源。你可以在遵守 GPL-3.0 条款的前提下自由使用、修改与分发，分发修改版本时需同步开放对应源代码。

*本项目由 Google AI Studio 中的 Gemini 4 Argon 与 Gemini 4 Neon 协作开发构建。*
