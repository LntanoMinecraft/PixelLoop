# PixelLoop 界面彻底重构、新版 Google 风格图标与「关于」页升级指南（给 AI Studio / Gemini）

> 使用方式：将下方「重构 Prompt 正文」复制发送给 AI Studio 中的 Gemini（可顺手附上之前的 4 张页面截图与图标截图），它会在**保留所有视频/录屏转 GIF 核心功能与 CI 构建配置不变**的前提下，一次性完成排版重构、弹性悬浮导航栏、Google Sans Flex 圆角字体、新版 Google 风格图标以及全新的「设置与关于」页。

---

## 重构 Prompt 正文

你是一位资深 Android Jetpack Compose 与 Material Design 3 Expressive 设计工程师。请基于现有的 **PixelLoop** 工程，**完全保留所有底层视频剪辑、录屏转 GIF、任务队列处理、作品库读写逻辑以及现有签名/GitHub Actions 工作流不变**，仅对全站 **UI 排版、底部悬浮导航栏、字体系统、应用图标以及新增的「设置/关于」页** 进行彻底重构，全面对齐 Google Pixel / Material Design 3 Expressive 旗舰质感。

### 一、底部悬浮胶囊导航栏（彻底重构）

1. **交互升级：未选中仅图标，选中弹性展开「图标 + 名称」**：
   * 底部采用宽幅悬浮圆角胶囊容器（`CircleShape`），悬浮于系统手势条上方（`navigationBarsPadding()` + `12dp`），使用 `surfaceContainerHigh` 色调表面、柔和抬升阴影与极淡边界（`outlineVariant.copy(alpha = 0.22f)`）；
   * 导航栏设 **5 个标签页**：`创作`、`剪辑`、`队列`、`作品库`、`设置`；
   * **核心动效**：未选中项**只显示图标**；点击切换至对应标签页时，该项通过 `AnimatedVisibility`（`expandHorizontally` + `fadeIn`）**横向弹性展开为「图标 + 名称」的胶囊指示器**（`secondaryContainer`），其余项平滑收缩回纯图标；
   * 整条胶囊的尺寸变化（`animateContentSize`）、指示器展开与图标微缩放全部采用富有弹性的 M3 Expressive 弹簧物理参数（`spring(dampingRatio = 0.62f, stiffness = 380f)`）。
2. **修复底部内容遮挡**：
   * 所有可滚动页面（尤其是「剪辑」页底部的帧率/分辨率参数区）底部必须统一预留至少 `104dp + navigationBarsPadding()` 的留白，彻底解决悬浮导航栏挡住选项按钮的问题。

### 二、字体系统与 M3 Expressive 视觉规范

1. **Google Sans Flex 圆角表现力字体**：
   * 在 `Type.kt` 中为英文、品牌名（`PixelLoop`）及数字参数配置 **Google Sans Flex**（优先加载系统内置 `google-sans-flex` 字体族，或通过免额外权限的字体回退），并在 `FontVariation.Settings` 中显式开启其特有的**圆角可变轴（`FontVariation.Setting("ROND", 100f)`）**；
   * 中文字符自然回退至系统原生清晰黑体；所有时间码（`00:00.0`）、分辨率（`640×480`）、帧率（`15 FPS`）与倍速统一启用等宽数字（`fontFeatureSettings = "tnum"`），防止拖动滑块时文字抖动。
2. **彻底去除生硬细描边，改用色调层级卡片**：
   * 移除目前所有卡片上显得呆板生硬的 `1dp` 灰色外框描边；
   * 统一改用 M3 Expressive 大圆角分组容器（外圆角 `24dp–28dp`，内边距 `16dp–20dp`），通过 `surfaceContainerLow` / `surfaceContainer` / `surfaceContainerHigh` 的色调明暗层次区分前景与背景；
   * 拉开字体字重层级：页面大标题用 `headlineMedium` + `FontWeight.Bold`（微紧字距 `-0.5sp`），分组标题用 `labelLarge` + `FontWeight.SemiBold` 着 `primary` 主色，正文与辅助提示克制清晰。

### 三、逐页精准排版重构（解决现有 4 页痛点）

1. **创作页（主入口）**：
   * **清理顶部栏**：将原先堆在首页右上角的「动态色」「跟随系统」两个零散胶囊移入新的「设置」页，首页顶部仅保留简洁醒目的 `PixelLoop` 标题；
   * **重构两大功能主卡**（「视频转 GIF」与「屏幕录制转 GIF」）：拉大卡片内部呼吸感与主次按钮对比度（主操作用高饱和 `primary` / `primaryContainer` 大圆角实心按钮，次操作用 `secondaryContainer` 色调按钮），底部可补充一个极简紧凑的「最近作品快捷入口」或合理平衡页面留白，避免下半屏显得空洞。
2. **剪辑页**：
   * 优化「画面裁剪与预览」「时间截取」「参数调节」三组卡片的垂直节奏与层次；
   * 为帧率、分辨率等快捷预设芯片行增加顺滑的 `contentPadding` 与舒适间距，**并确保滚动到底部时所有参数完全露出在悬浮导航栏上方**。
3. **任务队列页**：
   * **修复芯片右边缘被硬裁断的问题**：在「队列输出预设」的横向滚动芯片行（帧率 / 分辨率 / 倍速）中，使横向列表贯穿卡片内边距（Edge-to-Edge 滚动配合左右 `contentPadding = 16dp`）或改用自适应 `FlowRow` 换行排布，彻底消除 `45...`、`480p 清晰均...` 被卡片边缘切掉半截的割裂感；
   * 顶部「空闲 / 处理中」状态徽章与「添加视频」按钮对齐基线，视觉更利落。
4. **作品库页**：
   * **修复顶部拥挤**：将 `GIF 作品 (0)` 与 `录屏原片 (0)` 从标题右侧移至标题下方，做成独立的 M3 Expressive **分段按钮（SegmentedButton / 胶囊切换条）**；
   * 优化空状态视觉居中与间距，去掉生硬外框，使整体更轻盈通透。

### 四、新增「设置与关于」页（核心必做项）

新增第 5 个标签页 **「设置」**，采用 M3 Expressive 分组圆角卡片排布，包含两大板块：

1. **外观与偏好**：
   * **动态取色（Material You）** 开关；
   * **深浅色模式**：分段选择器（`跟随系统 / 浅色 / 深色`）；
   * **应用主题色盘**：7 色圆形色盘选择器（带选中态圆环与弹簧勾号动效）；
   * （如工程支持双语）**应用语言**切换。
2. **关于 PixelLoop（硬性要求，重点打磨）**：
   * **品牌头卡**：醒目圆角展示 **全新 PixelLoop 应用图标** + 应用名称 `PixelLoop` + 动态版本号（格式如 `v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})`），下方配一句极简克制的描述（如「本地视频与录屏转 GIF 工具」，禁止堆砌 AI 宣传词）；
   * **开源与开发者链接列表（均支持点击通过 `Intent.ACTION_VIEW` 跳转）**：
     * **项目主页（GitHub）**：副标题显示 `github.com/LntanoMinecraft/PixelLoop`，点击跳转 `https://github.com/LntanoMinecraft/PixelLoop`；
     * **开发者酷安**：副标题显示 `酷安主页 · UID 34054336`，点击跳转 `https://www.coolapk.com/u/34054336`（优先尝试系统默认处理以唤起酷安 App，无酷安时自动回退浏览器打开）。

### 五、应用图标重构（对齐新版 Google 系统图标风格）

当前深蓝黑底 + 白色空心方框的图标在深色界面容易隐身且显得单薄，请按 **新版 Google 系统图标（纯白底 + 柔和空气感渐变 + 实心叠层，无生硬描边）** 彻底重绘：

1. **纯白底板与严格安全区留白**：
   * 将 `ic_launcher_background.xml` 改为纯净白色（`#FFFFFF`）；
   * 前景核心图形严格收敛在 `108dp × 108dp` 画布正中心的 **`46dp × 46dp`（外接圆直径 ≤ 52dp）** 范围内，四周留出与新版 Google 文档、信息、通讯录一致的宽裕纯白留白。
2. **「柔光循环播放体」实心渐变构图（紧扣 PixelLoop 概念，无生硬描边）**：
   * 彻底去掉白色细描边方框，改用饱满的**大圆角实心柔光渐变主体**（采用清透青蓝 → 谷歌蓝 → 柔和紫罗兰渐变，如 `#38BDF8` → `#4285F4` → `#8B5CF6`，带有新版 Google 图标特有的轻盈微光泽）；
   * 造型上可用**两段首尾自然交错衔接的圆润循环弧/微透叠层循环框（寓意 Loop 动图循环）+ 居中极简纯白圆角播放三角**，线条利落、无脏阴影、无多余小圆点装饰。
3. **完整适配主题图标与全分辨率回退**：
   * 同步重写清晰的负空间单色剪影 **`ic_launcher_monochrome.xml`**，并在 `mipmap-anydpi-v26/ic_launcher.xml` 及 `ic_launcher_round.xml` 中显式声明 `<monochrome>` 节点，完整支持系统**动态取色主题图标（Themed Icons）**与各类桌面形状自适应裁切，同时重新生成全部 5 套分辨率的方形与圆形 PNG 回退图标。

### 六、输出要求

请直接完成以上全部 UI、导航与图标重构代码修改，保持文案克制、零 AI 味，并跑通 Gradle 编译与单元测试后输出简明变更清单。
