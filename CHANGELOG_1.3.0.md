# PockRef v1.3.0

- ● 新增 启动页"导出运行日志" — 在起始页(无场景打开时)右下角新增悬浮按钮,点击即可导出当前设备的运行日志(应用版本 / 设备信息 / 用户偏好 / 最近 50 条 UI 错误 / 最近一次崩溃堆栈 / 本进程 logcat 后 500 行),通过 SAF 让用户选择保存位置,文件名 `PockRef_日志导出_<时间戳>.txt`。便于开发者与技术支持快速定位用户报告的异常
- ● 新增 崩溃持久化 — `CrashHandler` 在 `MainActivity.onCreate` 启动时安装,捕获未处理异常并同步写入 `<filesDir>/last_crash.txt`(`FileDescriptor.sync()` 强制落盘,避免进程死亡时丢字节),下次启动导出日志时一并带上。原来的崩溃走系统默认对话框的体验保留,Android vitals 仍能上报
- ▲ 调整 启动页导出日志按钮去除阴影 + 去除文字说明 — 按钮现在是一个纯图标 FAB(48dp 圆角,1dp outline),不再有 labelSmall 文字"导出运行日志",也不再有 `Dimens.FabElevation` 阴影。accessibility 的 `contentDescription` 仍保留
- ▲ 调整 启动页导出日志按钮默认隐藏 + 7-tap 解锁 — 按钮**默认不显示**(每次打开 app 都从隐藏开始,这是 Compose `remember` 的语义)。要显示需要**点启动页 "PockRef" 标题 7 次**(连击 7 下后按钮出现,本次 session 持续显示;下次 app 重启又回到隐藏)。前 6 次 tap 是静默的(无 snackbar / 无 toast),第 7 次直接以 FAB 出现作为 affordance 反馈。同时**去掉**了 `设置 → 外观` 里 "启动页显示导出日志" 开关(不需要了,7-tap 本身就是显式 unlock)。对应的 PockRefUiState 字段 + AppPreferences 字段 + Action 全部清理掉
- ▲ 修复 加载 disk-backed 大图(原尺寸 9000+px)到画布时闪退 — `BitmapRegionDecoder.decodeRegion` 在 SM-X810 / 部分 Adreno GPU 上**忽略 `inSampleSize`**,直接返回原图 1:1 bitmap(347MB ARGB_8888),触发 `Canvas: trying to draw too large` 异常。`SceneImageDecoder.decodeViaRegionDecoder` 改成 decodeRegion 后**强制** `createScaledBitmap` 到 `targetSide`,跟 fallback 路径一致。disk-backed .pur 大图(9317×9333 这种)现在正常显示为 4096 long-edge(~16MB),不再闪退
- ▲ 修复 (round 2, 同 crash 路径) 真正主因不是 region decoder(`getOrCreateRegionDecoder` 在本项目是 stub 返 null,生产路径永远走 `decodeFromOffsetFallback` → `decodeSampledBitmap` → `BitmapFactory.decodeByteArray`),而是 **`BitmapFactory` 在 SM-X810 上同样忽略 `inSampleSize`**,返回 1:1 bitmap。`decodeSampledBitmap` decode 后加强制 `createScaledBitmap` 到 `reqWidth/Height`,跟 e4b2b31 同款 force-scale 保护。`OOM retry` 路径**不**能 catch 这个 case(OOM 只在真分配失败时 fire,1:1 decode 12GB 设备塞得下不会 OOM)

## 🛠 内部改进

- 新增 `data/CrashHandler.kt` 与 `data/LogExporter.kt` 两个纯函数 utility,均带 JVM 单测(`fd.sync()` 在 macOS dev host 上会抛 SyncFailedException,生产代码已用 `runCatching` 兜底)
- `PockRefViewModel` 新增 `recentErrors` 50 条 ring buffer + `logExportInProgress: StateFlow<Boolean>`(沿用 `lastError` 模式,不进 `PockRefUiState`)+ `startLogExport(appLabel, onReady, onError)` 30s watchdog
- `versionName` / `versionCode` 改读 PackageManager(`buildFeatures.buildConfig` 默认 false,生成的 BuildConfig 类不可用)
