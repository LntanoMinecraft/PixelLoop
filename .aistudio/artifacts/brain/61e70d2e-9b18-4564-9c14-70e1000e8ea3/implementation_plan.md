# PixelLoop (灵动帧) — 录屏捕获与 MP4 转 GIF 创意工坊

**PixelLoop (灵动帧)** 是一款专为移动创作者、开发者与社交分享达人打造的 Android 录屏与视频转 GIF 工具。它采用谷歌最新的 **Material Design 3 Expressive** 设计语言，支持系统级动态取色与高帧率物理弹簧动效；深度集成 **Android 16 (API 36) 实时通知（Live Updates）** 展示录屏与编码进度，并通过 **零模糊（Zero-Blur）与硬件加速图层** 确保全流程满帧丝滑体验。

---

## 用户评审与关键决策确认

> [!IMPORTANT]
> 以下核心产品、性能与系统特性决策已根据您的最新反馈全面落实：

- **确认决策 1 — Android 16 实时更新通知（Live Updates）交互**：录屏过程与 GIF 编码进度全面采用 **Android 16 (API 36) 实时通知新特性**（`ProgressStyle` + `setRequestPromotedOngoing(true)` + `setShortCriticalText` + `POST_PROMOTED_NOTIFICATIONS` 权限），在系统状态栏胶囊（Status Bar Chip）、锁屏与通知抽屉顶部实时展示录制秒数（如 `REC 00:15`）、分段进度条及“结束并编辑 GIF”一键操作按钮。
- **确认决策 2 — 极致流畅度优化与“零模糊”准则**：**严禁引入任何 `Modifier.blur` 或 `RenderEffect` 高斯模糊效果**。完全依靠 Material 3 Expressive 的多级语义化不透明表面色阶（`surfaceContainerLowest` 至 `surfaceContainerHighest`）塑造层次感；所有物理弹簧动画均下沉至 GPU `graphicsLayer` 绘制阶段执行，配合 `derivedStateOf` 状态隔离与缩略图内存复用，杜绝掉帧与卡顿。
- **确认决策 3 — 视觉美学（Material 3 Expressive + 动态取色）**：适配 Android 12+ 壁纸动态取色（`dynamicDarkColorScheme` / `dynamicLightColorScheme`）与高表现力备选色板，运用胶囊/超椭圆动态形变（Shape Morphing）与鲜明排版层级。
- **确认决策 4 — 裁剪编辑与本地作品库管理**：支持 **时间轴首尾截取**、**画面区域裁剪（自由比例 + 1:1 / 16:9 / 9:16 / 4:3）**、**帧率（5–24 FPS）**、**分辨率（240p–720p/原画）** 与 **倍速（0.5x–2.0x）** 调节；内置基于 Room 的本地作品库，展示体积与参数并支持一键分享至社交平台。

---

## 1. 产品概览与核心价值

- **核心功能**：
  1. **双源输入与 Android 16 实时录屏**：一键开启 3 秒弹性倒计时录屏，转入后台后通过 **Android 16 Live Updates 实时通知** 在状态栏胶囊与锁屏顶部实时显示录制时长、阶段进度与停止按钮；同时支持零权限 Android Photo Picker 导入本地 MP4 视频（并内置炫彩演示视频一键生成器）。
  2. **精密非线性编辑台（GIF Studio）**：在同一工作台内完成画面区域框选裁剪、首尾时间段精准截取、倍速控制、帧率与分辨率参数调优。
  3. **纯端侧高速 GIF 编码引擎**：搭载自适应色彩量化（256色调色板）与 LZW 编码器，在端侧离线完成视频帧提取、空间裁剪、缩放与 GIF 合成，并通过实时通知与界面同步反馈进度。
  4. **本地作品库与一键社交分享**：集中管理所有生成的 GIF 动图，查看详细参数，支持一键调起系统分享面板发送给好友或社交平台。
- **核心价值**：将“录屏 → 视频剪辑 → 转 GIF → 分享”四步流程浓缩为端侧无损、无水印、原生契合 Android 16 系统体验的高帧率工具。

---

## 2. 用户体验与视觉设计 (M3 Expressive & 高帧率零模糊设计)

### 核心用户流程

```
┌────────────────────────────────────────────────────────────────────────────┐
│                     1. 首页创意中枢 (Expressive Home)                      │
│  ┌──────────────────────────┐              ┌────────────────────────────┐  │
│  │   🔴 录屏捕获 GIF        │              │   🎬 导入 MP4 视频转 GIF   │  │
│  │ 弹性倒计时 → A16实时通知 │              │  系统相册选择器 / 演示视频 │  │
│  └────────────┬─────────────┘              └──────────────┬─────────────┘  │
└───────────────┼───────────────────────────────────────────┼────────────────┘
                │                                           │
                ▼ (状态栏胶囊 "REC 00:12" + ProgressStyle)  │
┌───────────────────────────────────────────┐               │
│   Android 16 Live Update 实时录屏通知     │               │
│  • 状态栏胶囊实时跳字 + 多段式进度指示条  │               │
│  • 快捷操作：[停止并编辑]  [取消录制]     │               │
└───────────────┬───────────────────────────┘               │
                └─────────────────────┬─────────────────────┘
                                      ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                   2. 灵动剪辑工作台 (M3 Expressive Studio)                 │
│  • 画布预览区：纯色高对比容器 + 硬件加速九宫格裁剪框 (自由/1:1/16:9/9:16)  │
│  • 胶片时间轴：缓存缩略图轨道 + 胶囊形双滑块截取 + 动态时长气泡            │
│  • 参数控制台：形变胶囊选项卡 (FPS / 分辨率 / 倍速)                        │
│  • 实时体积预估徽章：毫秒级响应计算预计帧数与预估 GIF 体积                 │
└─────────────────────────────────────┬──────────────────────────────────────┘
                                      ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                      3. 编码导出与作品详情 (Export)                        │
│  • 界面几何脉冲进度 + Android 16 实时转码进度通知 → 生成动态 GIF           │
│  • 立即预览动态效果 + 一键分享至社交平台 / 保存至系统相册                  │
│  • 自动归档至【我的作品库】，支持查看参数详情与随时重分享                  │
└────────────────────────────────────────────────────────────────────────────┘
```

### 视觉识别与流畅度准则

- **美学定位**：**Material Design 3 Expressive（高对比纯净色阶，零模糊渲染）**
  - **摒弃模糊效果**：不使用任何高斯模糊（Blur）或毛玻璃遮罩，避免 GPU 离屏缓冲区切换带来的帧率下降。所有悬浮面板、状态徽章与遮罩均采用纯粹的不透明色阶（`surfaceContainerLow`、`surfaceContainerHigh`、`primaryContainer`、`tertiaryContainer`）与锐利的几何边框，视觉干净利落且渲染开销极低。
- **动态取色与 Expressive 色板（Dynamic Color）**：
  - 默认启用 Android 12+ 系统壁纸动态取色（`dynamicDarkColorScheme` / `dynamicLightColorScheme`），并提供一键切换“动态壁纸取色 / 经典灵动色板”与深浅色模式的顶栏开关。
  - 利用 `primaryContainer` 与 `tertiaryContainer` 构建充满活力的双核入口卡片与时间轴高亮区间。
- **硬件加速物理弹簧动效（GPU-Accelerated Spring Motion）**：
  - 所有缩放、平移、旋转与透明度过渡统一通过 `Modifier.graphicsLayer` 在渲染绘制阶段（Draw Phase）执行，不触发外层布局测量（Re-layout）。
  - 交互态胶囊（比例选择、FPS 档位、倍速按钮）采用轻量级 `spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)` 实现圆角与宽度的瞬时弹性反馈。

---

## 3. 核心产品决策与技术权衡

- **决策 1：深度集成 Android 16 (API 36) 实时更新通知（Live Updates）**
  - *所选方案*：针对项目 `compileSdk = 36` / `targetSdk = 36` 环境，在 `AndroidManifest.xml` 中声明 `android.permission.POST_PROMOTED_NOTIFICATIONS`。录屏前台服务（`ScreenRecordService`）构建通知时严格遵循 Android 16 规范：
    1. 启用 `.setOngoing(true)` 与 `.setRequestPromotedOngoing(true)` 请求系统提升为实时通知。
    2. 设置 `.setShortCriticalText("00:12")` 在系统状态栏胶囊（Status Bar Chip）中实时展示录制读数。
    3. 使用 Android 16 `Notification.ProgressStyle`（或 `NotificationCompat.ProgressStyle`）配置多段式录制时长里程碑（Segments & Points）与实时进度位置，不设置自定义 `RemoteViews` 或 `setColorized(true)`，确保 100% 满足系统提升条件。
    4. 提供直达剪辑台的“停止并转 GIF”与“取消”通知栏操作按钮，并在应用内同步展示 Android 16 实时通知状态预览条。
  - *原因*：完美契合 Android 16 原生系统级实时活动体验，用户在切换到其他应用进行录屏操作时，只需瞥一眼状态栏胶囊即可掌握录制时长，并可一键结束录制直接进入 GIF 裁剪台。

- **决策 2：全链路流畅度优化（60/120Hz 满帧保障）**
  - *所选方案*：
    1. **零模糊与少透明层叠（Overdraw 控制）**：裁剪画布暗角遮罩使用单次 `Canvas` 路径裁剪绘制，避免多层组件叠加。
    2. **重组范围严格隔离**：录制计时器、时间轴拖拽游标位置均采用函数式状态读取（Lambda Modifiers / `drawWithCache`），确保拖动滑块时仅重绘画布标尺而非整个页面。
    3. **异步缩略图缓存与解码降采样**：提取时间轴胶片预览图时使用 `getScaledFrameAtTime` 直接解码为小尺寸位图并存入内存缓存，杜绝主线程 I/O 或大图解码卡顿。

- **决策 3：纯 Kotlin 自适应 LZW GIF 编码引擎与本地 Room 作品库**
  - *所选方案*：在 `Dispatchers.Default` 后台协程池中完成帧裁剪、目标分辨率缩放、256 色调色板量化与 LZW 编码；导出作品写入本地存储并记录至 Room 数据库，通过 `FileProvider` 实现零权限一键社交分享。

---

## 4. 技术架构与数据策略

### 系统架构与组件关系图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│        UI Layer (Jetpack Compose + M3 Expressive + Zero-Blur Canvas)        │
│  ┌──────────────────────┐  ┌──────────────────────┐  ┌───────────────────┐  │
│  │   Expressive Home    │  │   GIF Studio Editor  │  │  Gallery & Share  │  │
│  │  • 动态取色/主题切换 │  │  • 硬件加速裁剪画布  │  │  • 动图流式卡片   │  │
│  │  • 弹性录屏倒计时    │  │  • 缓存帧缩略图时间轴│  │  • 弹簧展开详情   │  │
│  │  • A16实时通知状态卡 │  │  • 形变胶囊参数面板  │  │  • 社交平台分享   │  │
│  └──────────┬───────────┘  └──────────┬───────────┘  └─────────┬─────────┘  │
└─────────────┼─────────────────────────┼────────────────────────┼───────────┘
              │                         │                        │
              ▼                         ▼                        ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│            State & ViewModel Layer (MVVM + derivedStateOf 隔离)             │
│  • StudioViewModel: 管理动态取色偏好、当前视频源元数据、裁剪归一化坐标、    │
│    时间截取区间 (startMs..endMs)、FPS/分辨率/倍速参数、实时体积估算、       │
│    编码进度流 (EncodingState) 以及与 Android 16 实时录屏通知的双向联动      │
└─────────────┬─────────────────────────┬────────────────────────┬───────────┘
              │                         │                        │
              ▼                         ▼                        ▼
┌─────────────────────────┐ ┌───────────────────────┐ ┌───────────────────────┐
│  ScreenRecordService    │ │   GifExportEngine     │ │   GifRepository       │
│  • MediaProjection      │ │  • 降采样关键帧缓存   │ │  • Room GifItemDao    │
│  • A16 Live Update 通知 │ │  • 区域裁剪与尺寸缩放 │ │  • 本地文件存储管理   │
│  • ProgressStyle 进度条 │ │  • 256色量化 + LZW    │ │  • MediaStore 导出    │
│  • ShortCriticalText胶囊│ │  • Dispatchers.Default│ │  • FileProvider 分享  │
└─────────────────────────┘ └───────────────────────┘ └───────────────────────┘
```

### 核心数据模型与状态流转

1. **Android 16 实时通知状态 (`LiveRecordNotificationState`)**：
   - `isRecording`: 是否正在录屏捕获中
   - `elapsedSeconds`: 已录制秒数（同步更新至通知 `setShortCriticalText("00:XX")`）
   - `maxRecommendedSeconds`: 推荐 GIF 黄金时长上限（如 30 秒，映射为 `ProgressStyle` 的 `Segment` 分段：0-10s 轻量段、10-20s 标准段、20-30s 超长段）
   - `isPromotedEligible`: 当前通知是否满足 Android 16 `canPostPromotedNotifications()` 提升状态

2. **编辑配置状态 (`GifEditConfig`)**：
   - `sourceUri`: 当前处理的 MP4 视频 URI（来自录屏输出、相册选择或内置演示源）
   - `videoDurationMs`, `videoWidth`, `videoHeight`: 原视频元数据
   - `trimStartMs`, `trimEndMs`: 时间轴首尾截取毫秒区间
   - `cropRect`: 归一化画面裁剪区域（`left, top, right, bottom` 范围 `0f..1f`）及当前比例模式（自由、1:1、16:9、9:16、4:3）
   - `targetFps`: 目标帧率（5 / 8 / 10 / 12 / 15 / 20 / 24 FPS）
   - `targetResolution`: 目标分辨率档位（240p / 360p / 480p / 720p / 原始）
   - `playbackSpeed`: 播放倍速（0.5x / 1.0x / 1.5x / 2.0x）
   - `estimatedSizeBytes` & `estimatedFrameCount`: 派生计算的预估信息

3. **作品库实体 (`GifCreationEntity`)**：
   - `id`, `title`, `filePath`, `thumbnailPath`, `width`, `height`, `fps`, `durationMs`, `fileSizeBytes`, `sourceType`, `createdAt`
