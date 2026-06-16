# PockRef v1.3.0

- ● 新增 启动页"导出运行日志" — 在起始页(无场景打开时)右下角新增悬浮按钮,点击即可导出当前设备的运行日志(应用版本 / 设备信息 / 用户偏好 / 最近 50 条 UI 错误 / 最近一次崩溃堆栈 / 本进程 logcat 后 500 行),通过 SAF 让用户选择保存位置,文件名 `PockRef_日志导出_<时间戳>.txt`。便于开发者与技术支持快速定位用户报告的异常
- ● 新增 崩溃持久化 — `CrashHandler` 在 `MainActivity.onCreate` 启动时安装,捕获未处理异常并同步写入 `<filesDir>/last_crash.txt`(`FileDescriptor.sync()` 强制落盘,避免进程死亡时丢字节),下次启动导出日志时一并带上。原来的崩溃走系统默认对话框的体验保留,Android vitals 仍能上报
- ▲ 调整 启动页导出日志按钮去除阴影 + 去除文字说明 — 按钮现在是一个纯图标 FAB(48dp 圆角,1dp outline),不再有 labelSmall 文字"导出运行日志",也不再有 `Dimens.FabElevation` 阴影。accessibility 的 `contentDescription` 仍保留
- ● 新增 启动页是否显示导出日志的开关 — `设置 → 外观` 增加一个 `启动页显示导出日志` Switch,默认开,关闭后启动页 FAB 立即消失。**注意:1.3.0 中启动页 FAB 是导出日志的唯一入口,关掉后该功能将完全无法触达 — 后续版本考虑加 long-press 启动页菜单项作为冗余入口**
- ▲ 修复 加载 disk-backed 大图(原尺寸 9000+px)到画布时闪退 — `BitmapRegionDecoder.decodeRegion` 在 SM-X810 / 部分 Adreno GPU 上**忽略 `inSampleSize`**,直接返回原图 1:1 bitmap(347MB ARGB_8888),触发 `Canvas: trying to draw too large` 异常。`SceneImageDecoder.decodeViaRegionDecoder` 改成 decodeRegion 后**强制** `createScaledBitmap` 到 `targetSide`,跟 fallback 路径一致。disk-backed .pur 大图(9317×9333 这种)现在正常显示为 4096 long-edge(~16MB),不再闪退

## 🛠 内部改进

- 新增 `data/CrashHandler.kt` 与 `data/LogExporter.kt` 两个纯函数 utility,均带 JVM 单测(`fd.sync()` 在 macOS dev host 上会抛 SyncFailedException,生产代码已用 `runCatching` 兜底)
- `PockRefViewModel` 新增 `recentErrors` 50 条 ring buffer + `logExportInProgress: StateFlow<Boolean>`(沿用 `lastError` 模式,不进 `PockRefUiState`)+ `startLogExport(appLabel, onReady, onError)` 30s watchdog
- `versionName` / `versionCode` 改读 PackageManager(`buildFeatures.buildConfig` 默认 false,生成的 BuildConfig 类不可用)
