# PockRef for Android

面向 Android 平板的参考图板，使用 Kotlin、Jetpack Compose 和 Material 3 构建，适合绘画时整理、查看参考图片，并支持悬浮参考窗口和 S Pen 操作。

本仓库包含 Android 应用源码。iOS 版本不在本仓库中。

## 功能

- 自由画布：平移、缩放、旋转，管理多张参考图片。
- 图片操作：裁剪、镜像、灰度、颜色调整、取色和导出。
- 多选、分组、对齐、自动排版和图层管理。
- 画布标注、文字及练习模式。
- 场景保存、最近文件、自动备份与异常恢复。
- `.pur` 文件读写，以及 PockRef 场景扩展数据。
- 悬浮参考窗口和 S Pen 输入支持；具体行为取决于设备及系统授权。

文件互操作说明：PockRef 支持多种 `.pur` 格式和自身扩展数据，但不同应用、文件版本对扩展属性的支持可能不同。重要文件请保留原始副本。

## 下载

已签名的 Android 安装包发布在独立的更新仓库：

[下载 PockRef APK](https://github.com/KangNa-K/PockRef-updates/releases/latest)

应用最低支持 Android 7.0（API 24）。平板和手写笔是主要使用场景。

## 从源码构建

需要 JDK 17、Android SDK，以及网络连接以下载 Gradle 和 Maven 依赖。当前项目使用 Android SDK 37；Gradle、Android Gradle Plugin 和 Kotlin 的具体版本以仓库内构建配置为准。

```bash
git clone https://github.com/KangNa-K/PockRef.git
cd PockRef
```

使用 Android Studio 打开项目并配置 Android SDK，或在项目根目录创建本机的 `local.properties`：

```properties
sdk.dir=/absolute/path/to/Android/sdk
```

构建调试安装包：

```bash
./gradlew :app:assembleDebug
```

输出位置：`app/build/outputs/apk/debug/app-debug.apk`。

调试构建不需要正式签名密钥。正式版本签名从本机 `~/.android-keys/pockref/keystore.properties` 或项目根目录的 `keystore.properties` 读取；这些文件和签名密钥不包含在仓库中。自行分发时请使用自己的签名。

## 测试

```bash
# 本地单元测试与静态检查
./gradlew :app:testDebugUnitTest :app:lintDebug

# 仪器测试：需要已连接的 Android 设备或模拟器
./gradlew :app:connectedDebugAndroidTest
```

`benchmark/` 包含设备端 Macrobenchmark。性能结果与设备、系统和运行条件有关。

## 项目结构

```text
app/src/main/java/com/pockref/app/
  data/       场景文件、图片解码、缓存与备份
  ui/         Compose 界面、画布和 ViewModel
  spen/       手写笔按键与输入处理
  update/     应用更新检查和安装流程
app/src/test/           本地单元测试
app/src/androidTest/    设备端测试
benchmark/             性能测试
docs/                  设计记录与发布说明
```

## 反馈与贡献

欢迎通过 [Issues](https://github.com/KangNa-K/PockRef/issues) 报告问题，或提交 Pull Request。报告问题时请附上应用版本、设备型号、Android 版本和复现步骤。提交日志或测试文件前，请移除私人图片、路径和其他个人信息。

APK 更新流程见 [docs/update-release.md](docs/update-release.md)，版本变更见 [GitHub Releases](https://github.com/KangNa-K/PockRef-updates/releases)。

## 许可证

PockRef 源码采用 [GNU General Public License v3.0](LICENSE)（SPDX：`GPL-3.0-only`）。

完整条款见 [LICENSE](LICENSE)。第三方依赖遵循各自的许可证。
