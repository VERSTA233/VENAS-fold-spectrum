# VENAS Fold Spectrum

<img src="example/logo.png" width="128" alt="VENAS Fold Spectrum Logo" />

本软件主要为 Android 系统折叠屏手机设计，实现后台音频的可视化展示（直板机也可以使用）。启动软件后半折手机置于桌面上，会在音乐播放时展示当前音乐的封面、信息、实时频谱、播放进度和实时波形（无音乐播放时则显示极简时钟）。

<img src="example/visual.jpg" width="600" alt="VENAS Fold Spectrum 可视化界面展示" />

---

## 使用说明

- 首次使用时，检测到折叠屏手机特征则默认使用折叠模式，半折手机自动进入可视化界面；未检测到则开启“非折叠模式”，旋转横放手机或单击右上角旋转按钮进入可视化界面。

- 软件通过检测转轴角度实现半折检测，半折检测的角度范围为 **10°～150°**。

- 在可视化界面可通过单击封面进行暂停/播放；在封面上左右滑动可切换歌曲；拖动播放进度条可以调整播放进度；单击空白区域会在右上角显示音量调节和设置图标。

## 设置说明

在初始页可展开设置；在可视化界面单击任意位置会在界面右上角显示设置图标，单击后进入设置。

- 旋转 180°：可视化界面下会禁用系统自动旋转，如果你的使用场景下显示的内容是倒过来的，可以通过开关该选项反转显示方向。

- 非折叠模式：为直板机提供（折叠屏手机也可以打开，开启后不需要半折屏幕即可显示可视化），旋转横放手机或单击右上角旋转按钮进入可视化界面，竖起手机返回初始页。

- 屏幕常亮：开启后可视化显示时屏幕常亮，禁用系统自动息屏。

- 界面亮度：关闭时跟随系统，开启后可指定亮度，禁用系统自动亮度调整。

- 防烧屏模式：开启时闲置一分钟后通过棋盘遮罩关闭一部分像素，并每五分钟切换一次（屏幕亮度会变低）。点击屏幕时恢复到正常显示状态并重置计时。该模式旨在避免 OLED 屏幕的烧屏情况，思路来源于“Linzin 桌面時鐘”。

## 项目构建

推荐使用 Android Studio 完成环境配置、调试和 APK 打包。项目自带 Gradle Wrapper，无需单独安装 Gradle；首次同步需要联网下载依赖。

### 首次打开项目

1. 安装 Android Studio，从 GitHub 克隆项目，打开包含 `settings.gradle.kts` 的项目根目录。
2. 在 **Tools → SDK Manager** 中安装 **Android SDK Platform 36**，缺少的 SDK Build-Tools 按构建提示安装。
3. 在 **Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK** 中选择 **JDK 17 或 21**；没有合适版本时可使用 **Download JDK** 下载。
4. 等待 Gradle 同步完成。Android Studio 通常会自动生成指向本机 SDK 的 `local.properties`；若提示找不到 SDK，请确认 SDK Manager 中的安装路径与该文件的 `sdk.dir` 一致。该文件无需提交到 Git。

### 开发调试

打开 **View → Tool Windows → Build Variants**，将 `app` 的构建类型设为 `debug`。连接已开启 USB 调试并授权的手机，或选择模拟器，点击 **Run** 运行；需要断点调试时点击 **Debug**。

### 生成测试安装包

1. 在 **Build Variants** 中将 `app` 切换为 `preview`。
2. 选择 **Build → Generate App Bundles or APKs → Generate APKs**（旧版可能显示为 **Build Bundle(s) / APK(s) → Build APK(s)**）。
3. 完成后通过构建通知中的 **Locate** 查看安装包，默认输出为 `app/build/outputs/apk/preview/app-preview.apk`。

`preview` 已开启代码和资源缩减，使用本机调试签名，适合分发测试。它使用独立包名 `com.venas.foldspectrum.preview`，桌面名称为“VENAS 测试版”，可与正式版同时安装；设置和权限独立，首次使用需要单独授权。需要断点调试时切回 `debug`。

### 生成正式 release 安装包

1. 发布更新前，在 `app/build.gradle.kts` 中递增 `versionCode`，并修改展示给用户的 `versionName`。
2. 选择 **Build → Generate Signed App Bundle or APK → APK**，模块选择 `app`。
3. 选择发行密钥文件（`.jks` 或 `.keystore`），填写 **Key store password**、**Key alias** 和 **Key password**。首次发行时可通过 **Create new** 创建密钥。
4. 下一步选择 `release`，确认输出目录后点击 **Create / Finish**。代码和资源缩减已在项目中配置，无需额外开启。
5. 完成后通过 **Locate** 找到签名 APK。输出位置以向导中的目录为准，常见路径为 `app/release/app-release.apk`。

请备份发行密钥、alias 和密码，后续更新继续使用同一密钥。更换电脑时需要单独迁移，GitHub 克隆不会恢复这些内容；密钥及密码不要提交到仓库。建议同时归档该版本的 APK 和 `app/build/outputs/mapping/release/mapping.txt`，便于还原混淆后的崩溃日志。

## 权限与隐私

应用首次启动时需要开启两项权限：

- 通知权限：读取当前播放器的封面、曲名、艺人、播放状态和进度，并进行播放／暂停、切歌及进度控制。软件不会主动发送通知。

- 音频权限（系统可能显示为“麦克风”或“录音”）：用于获取系统正在播放音频的实时波形和频谱。应用不会打开麦克风或保存音频。

网络权限访问不需要授权，仅用于读取播放器在媒体元数据中提供的HTTPS封面地址，当访问失败时显示默认封面。软件不会上传数据。

## 兼容性说明

- 软件的折叠模式支持主流的具有转轴传感器的大折叠手机，包括荣耀、OPPO、VIVO、三星、小米、Google Pixel。

- 针对阔直板和阔折叠机型的屏幕比例优化了布局。实际横屏窗口宽高比**1.20-1.60** 自动使用阔屏布局。

- 直板机可以通过非折叠模式进行使用。

- 系统版本要求：Android 12 及以上。

## 软件参考

折叠屏厂商状态映射和防烧屏优化参考了UP主Linzin阿哲开发的 [Linzin 桌面時鐘](https://github.com/linzin86/LinzinDeskClock)。
