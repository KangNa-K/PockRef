# PockRef 启动页"导出运行日志"按钮 — 设计规范

> 版本: 1.0 | 日期: 2026-06-16
> 适用平台: Android (Galaxy Tab S10+ landscape 340dpi)
> 技术栈: Kotlin + Jetpack Compose + Material 3 (与现有 PockRef 一致)
> 当前目标版本: 1.3.0 (bump versionCode 8→9)

---

## 0. 背景与动机

当前 PockRef 缺少一个面向开发 / 技术支持快速导出运行日志的能力。开发者本人在排查用户报告的崩溃、异常或 UI 异常时,只能让用户提供 `adb logcat` 抓取产物或连接到设备逐项翻看 `android.util.Log` 行,效率低且依赖用户协助。

本次目标:在起始页(无场景打开时显示的 `RecentFileGrid`)右下角新增一个"导出运行日志"按钮,点一下即可将设备 / 应用 / 近期错误 / 近期崩溃 / 应用本进程 logcat 等信息汇总成单 `.txt` 文件,通过 SAF (Storage Access Framework) 让用户选择保存位置。便于开发者 / 技术支持快速定位问题。

**明确不在本规范范围内**:
- 网络请求 / 响应日志(PockRef 是纯本地 App,无网络层,无数据源)
- 跨进程后台监听(S Pen / QuickMenu 后台模式不在范围内)
- 启动页之外的入口(顶部工具栏、设置 sheet、canvas 模式均**不**新增此按钮)
- 日志上传 / 自动上报(无后端,不做)
- `.zip` 压缩包(用户决策:单 `.txt`)

---

## 1. 触发场景与入口

| 属性 | 值 |
|---|---|
| 触发图标 | `Icons.Rounded.Description` (Material Symbols `description`) — 备选 `Article` |
| 入口位置 | 起始页(`state.images.isEmpty()` 分支)的 `Box` 容器 `BottomEnd` 对齐位置 |
| 触发条件 | 起始页可见(`isStartPage == true`)—— 一旦加载场景,按钮消失 |
| 触发 Action | `PockRefAction.RequestLogExport` |
| 可见性 | **总是可见**,不区分 build type(用户决策) |
| 重复点击保护 | `logExportInProgress == true` 时 FAB disabled |
| 提示文案 | 永久小 label `Text("导出运行日志")` 8dp 间距在图标下方(S Pen hover + 视觉双通道) |

按钮**不**放在:
- 顶部工具栏(任何时候都可见会让 canvas 模式过载,违背"启动页"语义)
- 设置 sheet 内(多一层跳转,违反"一键导出"诉求)
- Android 系统 Splash Screen(Android 12+ 系统 splash 窗口仅 ~200ms,无足够交互时间,且非应用可控层)

---

## 2. 界面层级树

```
PockRefApp (Box root)
├── state.images.isEmpty() == true  →  StartPageScaffold
│   ├── TopCenter: 标题 + 副标题
│   ├── Body:  RecentFileGrid (BoxWithConstraints)
│   └── Overlay: Box (BottomEnd, padding=EdgeInset=16dp)
│       └── LogExportFab                                ← NEW
│           ├── Surface(ChromeSurfaceAlpha=0.92f,
│           │           ChromeCornerRadius=12.dp,
│           │           FabElevation=6.dp)
│           ├── IconButton(size=44dp, onClick=...)
│           │   ├── Idle:      Icon(Description, 24dp)
│           │   └── Loading:   CircularProgressIndicator(16dp, strokeWidth=2dp)
│           └── Text("导出运行日志", style=labelSmall)
└── state.images.isEmpty() == false →  CanvasScaffold
    └── (无 LogExportFab)
```

---

## 3. 组件清单

### 3.1 LogExportFab(新文件:`ui/logs/LogExportFab.kt`)

| 属性 | 值 | Token / 说明 |
|---|---|---|
| 容器尺寸 | 44dp × 44dp | 复用 `Dimens.BottomPanelChromeSize` |
| 圆角 | 12.dp | 复用 `Dimens.ChromeCornerRadius` |
| 表面色 | `surface` alpha `ChromeSurfaceAlpha` (0.92f) | 复用 `Dimens.ChromeSurfaceAlpha` |
| 表面描边 | `outlineVariant` 1.dp | 复用 `Dimens.ChromeBorderWidth` |
| 阴影 | 6.dp | 复用 `Dimens.FabElevation` |
| 内边距 | 整体 `padding(EdgeInset)` (16dp) | 复用 `Dimens.EdgeInset` |
| 图标尺寸 | 24.dp | M3 标准 |
| 图标颜色 | `onSurface` | M3 |
| Loading 指示器 | 16.dp, strokeWidth 2.dp, color = `primary` | M3 `CircularProgressIndicator` |
| Label 字体 | `MaterialTheme.typography.labelSmall` | M3 |
| Label 颜色 | `onSurfaceVariant` | M3 |
| Label 与图标间距 | 8.dp | — |

**状态机**(本地 `remember { mutableStateOf<LogExportState>(Idle) }`):

```
Idle ──(onClick)──▶ Loading ──(VM build 完成 + SAF 已 launch)──▶ Idle
                       │
                       └────(VM build 失败)──▶ Error("...") ──(1.5s 后)──▶ Idle
```

**注意**:Loading 在 `launcher.launch(name)` 调用瞬间即转为 Idle —— SAF 接管后 FAB 立刻恢复可点击状态;真正写文件由 SAF callback 完成,失败时 Toast。

### 3.2 LogExportState(sealed interface,新文件:`ui/logs/LogExportState.kt`)

```kotlin
sealed interface LogExportState {
    data object Idle : LogExportState
    data object Loading : LogExportState
    data class Error(val message: String) : LogExportState
}
```

不进入 `PockRefUiState`(纯本地 UI 状态,放 Composable 的 `remember` 内)。

### 3.3 CrashHandler(新文件:`data/CrashHandler.kt`)

| 项 | 值 |
|---|---|
| 安装位置 | `MainActivity.onCreate` 第 1 步,`super.onCreate` 之后,`enableEdgeToEdge` 之前 |
| 写入路径 | `context.filesDir / "last_crash.txt"` (App 私有内部存储,无需权限) |
| 写入时机 | `Thread.setDefaultUncaughtExceptionHandler` 的回调内,同步写(`FileOutputStream` flush + close) |
| 写入内容 | UTF-8 纯文本,见 §5 文件格式 |
| 链式调用 | 写完调用前一个 default handler(系统仍弹崩溃对话框,Android vitals 仍能上报) |
| 读取 API | `fun readLastCrash(context: Context): String?` —— null 表示文件不存在或为空 |
| 清理 API | `fun clearLastCrash(context: Context)` —— 成功导出后**不自动调用**,留给用户手动调试 |

### 3.4 LogExporter(新文件:`data/LogExporter.kt`)

```kotlin
object LogExporter {
    /**
     * Pure function. Returns assembled text. No IO, no SAF.
     * @param context  Used only for resources and PackageManager (read-only).
     * @param appPrefs Snapshot of user preferences.
     * @param recentErrors Last N (≤50) UiError entries from the VM's ring buffer.
     * @param lastCrash   Contents of last_crash.txt, or null if absent.
     */
    suspend fun build(
        context: Context,
        appPrefs: AppPreferences,
        recentErrors: List<UiError>,
        lastCrash: String?,
    ): String
}
```

所有 section 拼接均在该函数内完成。`logcat -d` 调用是唯一 IO 操作,放在函数内 `withContext(Dispatchers.IO)` 子作用域执行,自身 try/catch 后退化为 "dump failed" 字符串,**不抛**。

### 3.5 recentErrors Ring Buffer(在 `PockRefViewModel` 内)

```kotlin
private val recentErrors = ArrayDeque<UiError>(50)

// in existing reportError(message, throwable):
recentErrors.addLast(UiError(message, throwable))
while (recentErrors.size > 50) recentErrors.removeFirst()
```

**不进 `PockRefUiState`**(避免触发现有 100+ 字段的 data class 频繁重组合)。`LogExporter.build` 通过 `viewModel.snapshotRecentErrors()` 一次性快照读取。

---

## 4. 状态 / Action 契约

### 4.1 PockRefViewModel 增量状态(独立 StateFlow,**不**进 PockRefUiState)

```kotlin
// ui/PureRefViewModel.kt (existing class, alongside lastError pattern at line ~228)
private val _logExportInProgress = MutableStateFlow(false)
val logExportInProgress: StateFlow<Boolean> = _logExportInProgress.asStateFlow()
```

> 复用现有 `lastError: StateFlow<UiError?>` 的模式(line 228),不进 `PockRefUiState` data class(已 100+ 字段,避免重组开销)。

### 4.2 PockRefAction 增量(4 个,全部使用)

```kotlin
// ui/PureRefActions.kt: PockRefAction sealed interface
data object RequestLogExport : PockRefAction        // FAB 点击 → VM 开始构建文本
data object LogExportStarted : PockRefAction        // VM 已进入 IO 协程
data object LogExportFinished : PockRefAction       // VM 已完成文本构建
data class  LogExportFailed(val reason: String) : PockRefAction  // 构建失败
```

> 实际 SAF 调用由 Composable 直接 `launcher.launch(suggestedName)`,**不经 Action**。文本内容通过 VM callback 闭包回传 Composable,避免把可能 ~100KB 字符串塞进 `StateFlow`。

### 4.3 PockRefViewModel 增量 API

```kotlin
val logExportInProgress: StateFlow<Boolean>  // backed by MutableStateFlow

fun snapshotRecentErrors(): List<UiError> = recentErrors.toList()

fun startLogExport(
    appLabel: String,
    onReady: (suggestedName: String, content: String) -> Unit,
    onError: (String) -> Unit,
) {
    if (logExportInProgress.value) return  // 双保险
    viewModelScope.launch {
        _logExportInProgress.value = true
        onAction(LogExportStarted)
        try {
            val text = LogExporter.build(
                context       = application,
                appPrefs       = appPreferences,
                recentErrors   = recentErrors.toList(),
                lastCrash      = CrashHandler.readLastCrash(application),
            )
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val name = "${appLabel}_日志导出_$ts.txt"
            onReady(name, text)
            onAction(LogExportFinished)
        } catch (t: Throwable) {
            Log.e(TAG, "Log export failed", t)
            onError(t.message ?: t::class.java.simpleName)
            onAction(LogExportFailed(t.message ?: "未知错误"))
        } finally {
            _logExportInProgress.value = false
        }
    }
}
```

---

## 5. 导出文件格式

```
=== PockRef Run Log ===
Exported at:    2026-06-16T13:42:11+08:00
App version:    1.3.0 (versionCode 9)
App package:    com.pockref.app
SDK target:     34
SDK min:        24

=== DEVICE INFO ===
Manufacturer:   samsung
Model:          SM-X810
Brand:          samsung
Product:        gts10p
Hardware:       exynos2400
Android rel:    14
SDK int:        34
Security patch: 2026-05-01
Locale:         zh_CN
Screen density: 340 dpi
Uptime:         2h 14m 03s

=== PREFERENCES ===
Theme mode:              SYSTEM
Auto-save:               enabled (interval 30s)
S Pen mode:              BOX_SELECT_ON_HOLD
Gesture toggles:
  singleDrag          = ON
  twoFingerZoom       = ON
  twoFingerRotate     = ON
  twoFingerVertical   = ON
  twoFingerTap        = ON
  threeFingerTap      = ON
  threeFingerDoubleTap= ON
  doubleTapEmpty      = ON
Recent scenes count:     7

=== UI ERRORS (last 50, newest first) ===
[2026-06-16T13:39:08Z] ImageOptimizer: decode timeout
  java.util.concurrent.TimeoutException
    at com.pockref.app.data.ImageOptimizer.decode(ImageOptimizer.kt:312)
    ...
[2026-06-16T13:38:55Z] AutoSave: backup write failed
  java.io.IOException: No space left on device
    at ...

=== LAST CRASH ===
Time:        2026-06-15T22:10:33+08:00
Thread:      main
Exception:   java.lang.RuntimeException
Message:     Camera intent failed
Stacktrace:
  at com.pockref.app.MainActivity.onActivityResult(MainActivity.kt:572)
  at android.app.Activity.dispatchActivityResult(Activity.java:1345)
  ...

=== LOGCAT (PID 12345, last 500 lines, threadtime) ===
06-16 13:39:08.123 12345 12345 D AutoSave: tick
06-16 13:39:08.124 12345 12345 I PockRefSave: wrote 524288 bytes
06-16 13:39:08.567 12345 12345 W ImageOptimizer: decode timeout
...
```

**编码**:UTF-8 无 BOM
**行尾**:`\n` (LF),跨平台兼容
**最大体积估算**:~200 KB(假设 logcat 500 行平均 80 字节/行 + device/prefs/errors sections 共 ~10 KB)

---

## 6. 数据流(FAB 点击 → 文件落盘)

```
用户点击 LogExportFab
  │
  ▼
Composable:
  state.value = Loading
  viewModel.startLogExport(appLabel) { name, content ->
      state.value = Idle                              // Loading 关掉,SAF 接管
      capturedContent = content                       // 闭包持有
      launcher.launch(name)                           // SAF CreateDocument("text/plain")
  }
  │
  ▼  (Dispatchers.IO inside VM)
LogExporter.build() {
    HEADER     ← SimpleDateFormat + BuildConfig.VERSION_NAME/CODE
    DEVICE     ← android.os.Build.* + Locale + uptime
    PREFS      ← appPreferences 当前所有字段
    UI_ERRORS  ← recentErrors.toList() (snapshot at call time)
    LAST_CRASH ← CrashHandler.readLastCrash(context)
    LOGCAT     ← Runtime.getRuntime().exec(["logcat","-d","-v","threadtime",
                                            "--pid",Process.myPid().toString(),
                                            "-t","500"])
                 → BufferedReader.readText()
  }
  │
  ▼
SAF callback:
  uri = result (or null on cancel)
  if (uri == null) { state unchanged, no Toast }
  else {
      runCatching {
          contentResolver.openOutputStream(uri, "wt")?.use {
              it.write(content.toByteArray(Charsets.UTF_8))
          } ?: error("openOutputStream returned null")
      }.fold(
          onSuccess = { Toast.makeText(context, "日志已导出: $name", LONG).show() },
          onFailure = { Toast.makeText(context, "日志写入失败: ${it.message}", LONG).show() }
      )
  }
```

**并发控制**:
- `logExportInProgress` 由 VM 维护,加载期间 FAB 不可点击
- VM 协程超时 watchdog:30s 内未完成自动 reset 到 Idle 并 Toast "导出超时"
- SAF 阶段不阻塞 FAB:用户从 SAF 回来之前 FAB 已恢复 Idle(用户体验连贯)

---

## 7. 错误处理

| 失败点 | 检测 | 用户反馈 |
|---|---|---|
| 用户取消 SAF(uri=null) | callback | 静默无操作 |
| `logcat -d` IOException | `LogExporter.build` 内 catch,该 section 标 "(logcat dump failed: <msg>)" | 不影响其他 section,导出继续 |
| `last_crash.txt` 读取失败 | `CrashHandler.readLastCrash` 内 catch + return null | section 标 "No recent crash" |
| `LogExporter.build` 整体异常 | VM catch → onError callback | Toast `日志导出失败: ${msg}` |
| `openOutputStream(uri)` 返回 null | null check | Toast "无法打开目标文件" |
| 写入 IOException | catch | Toast "日志写入失败: ${io.message}` |
| 重复点击 | `logExportInProgress` 阻止 | FAB disabled + 视觉灰 |
| 导出超时(>30s) | VM watchdog | Toast "导出超时" + reset state |

**权限方面**(本规范的关键澄清):
- `logcat -d` 不需权限(API 24+)
- SAF CreateDocument 不需运行时权限
- `context.filesDir` 不需权限
- 因此**无需新增 `<uses-permission>`** —— `AndroidManifest.xml` 不变

---

## 8. 资源 / 字符串

`app/src/main/res/values/strings.xml` 新增:

```xml
<string name="log_export_button_label">导出运行日志</string>
<string name="log_export_toast_success">日志已导出: %1$s</string>
<string name="log_export_toast_write_failed">日志写入失败: %1$s</string>
<string name="log_export_toast_build_failed">日志导出失败: %1$s</string>
<string name="log_export_toast_timeout">日志导出超时</string>
<string name="log_export_saf_suggested_filename">%1$s_日志导出_%2$s</string>
```

`values-zh-rCN/strings.xml` 同步(本规范中所有中文字符串已为最终中文,无需额外翻译)。

---

## 9. 测试策略

### 9.1 单元测试

| 测试文件 | 关键 case |
|---|---|
| `data/LogExporterTest.kt` | ① 全 section 渲染;② 空 errors 写 "No recent errors";③ null lastCrash 写 "No recent crash";④ logcat 抛 IOException 时该 section 标 "dump failed";⑤ header 含 app versionName/SDK |
| `data/CrashHandlerTest.kt` | ① 触发异常后 `readLastCrash` 非 null;② 内容含 exception class name + ≥1 行 stack frame;③ 第二次崩溃覆盖第一次;④ `clearLastCrash` 后 `readLastCrash` 返回 null |
| `ui/PureRefViewModelTest.kt`(现有文件追加) | ① 注入 51 条 UiError 后 `snapshotRecentErrors().size == 50`;② FIFO 顺序保持 |

### 9.2 手动验证 checklist(写入 commit message)

1. Debug build 启动,确认起始页右下角有 FAB
2. 点击 FAB → SAF 弹出 → 选 Downloads → 确认 `.txt` 落盘
3. 打开 `.txt` 确认 6 个 section 完整、UTF-8 正常、logcat 有本 PID 行
4. Toast 显示 "日志已导出: PockRef_日志导出_*.txt"
5. 故意在 MainActivity.onCreate 抛 `RuntimeException`(临时一行)→ 应用崩 → 重启 → 点击 FAB → 导出 → `=== LAST CRASH ===` 段落出现完整堆栈
6. Release build (assembleRelease) 同样能看到 FAB(用户决策:不分 build type)
7. 在导出途中(loading)再次点击,确认 FAB 不可点
8. 在 SAF 弹窗时按返回键,确认无 Toast(静默取消)
9. 拒绝 SAF 选择的存储位置 / 选只读目录 → 写入失败 Toast

---

## 10. 文件 / 修改清单

### 10.1 新增文件(5)

```
app/src/main/java/com/pockref/app/
├── data/
│   ├── CrashHandler.kt                    ← NEW (~60 行)
│   └── LogExporter.kt                     ← NEW (~140 行)
└── ui/
    └── logs/
        ├── LogExportFab.kt                ← NEW (~120 行)
        └── LogExportState.kt              ← NEW  (~10 行)

app/src/test/java/com/pockref/app/
├── data/
│   ├── CrashHandlerTest.kt                ← NEW (~50 行)
│   └── LogExporterTest.kt                 ← NEW (~80 行)
```

### 10.2 修改文件(4)

```
app/src/main/java/com/pockref/app/
├── MainActivity.kt              ← +CrashHandler.install(application),约 +5 行
├── ui/PureRefActions.kt         ← +4 个 Action 变体,约 +8 行
├── ui/PureRefApp.kt             ← +StartPageScaffold 内的 LogExportFab Box,约 +30 行
└── ui/PureRefViewModel.kt       ← +recentErrors + _logExportInProgress StateFlow + startLogExport,约 +50 行

app/src/main/res/values/
├── strings.xml                  ← +5 个字符串
└── values-zh-rCN/strings.xml    ← +5 个字符串(同上)

app/build.gradle.kts             ← versionCode 8→9, versionName "1.2.4"→"1.3.0"
CHANGELOG_1.3.0.md              ← 新文件,~10 行
```

> 注:`PockRefUiState` data class **不**加 `logExportInProgress` 字段(走独立 StateFlow,沿用 `lastError` 模式),所以 `model/Models.kt` 不在修改清单中。

### 10.3 不变的文件

- `AndroidManifest.xml`(无新权限)
- `app/proguard-rules.pro`(无新 keep 规则需要,默认会保留 data class 字段)
- 任何 settings/practice 路径(`ui/settings/*` 与本规范无关)

---

## 11. 风险与缓解

| 风险 | 影响 | 缓解 |
|---|---|---|
| `recentErrors` 在快速刷新时丢失高频错误 | 低(只影响"近期"窗口,30 分钟前的也会被挤出) | 容量 50 已足够调试;若不够,后续 bump 到 200 |
| `logcat -d` 在某些 OEM ROM 上需要 root 或拒绝 | 中 | catch IOException 后该 section 降级为 "(logcat dump failed)" |
| 启动早期崩溃(`super.onCreate` 之前)不被 CrashHandler 捕获 | 低 | CrashHandler 在 `super.onCreate` 之后第 1 步安装,覆盖 `enableEdgeToEdge` / `setContent` / Compose 初始化阶段 |
| 单 `.txt` 体积过大 | 低(预估 < 200KB) | 已限 logcat `-t 500`;若后续需要,可改为按 section 拆 5 个文件 + `.zip` |
| release build 用户看到一个"导出日志"按钮觉得奇怪 | 低(用户决策:总是可见) | 无 |
| CrashHandler 写文件被 OS 在进程死亡前打断 | 低 | `FileOutputStream` + `flush()` + `close()`,再用 `fsync`(`fileDescriptor.sync()`)保证落盘 |

---

## 12. 不在本规范范围内(显式排除)

1. **网络层日志**:PockRef 无网络代码,无数据源
2. **跨进程后台监听**:AGENTS.md §"S Pen 按住"已说明"跨 App 后台监听目前不支持"
3. **上传 / 自动上报**:无后端
4. **启动页以外的入口**:明确不放在顶栏、设置、canvas 模式
5. **`.zip` 压缩包**:用户决策单 `.txt`
6. **日志可视化界面**:用户决策路径 A(直接导出,不预览)
7. **Developer mode 7-tap 解锁**:用户决策总是可见

---

## 13. 实施顺序(概要)

1. 加 strings(5 个 zh/en)
2. 写 `CrashHandler` + 单测
3. 写 `LogExporter` + 单测
4. 改 `PockRefViewModel`(recentErrors + startLogExport + onAction 4 个分支)
5. 改 `PureRefActions.kt`(4 个 action)
6. 改 `Models.kt`(+ logExportInProgress)
7. 写 `LogExportFab` + `LogExportState` + 在 `PureRefApp.kt` 起始页分支挂上 Box overlay
8. 改 `MainActivity.onCreate`(装 CrashHandler)
9. 跑 `./gradlew :app:testDebugUnitTest` 全绿
10. manual checklist(§9.2)
11. bump versionCode / versionName + CHANGELOG_1.3.0.md
12. `git commit -m "feat(logs): export run log from start page"`

---

## 14. 版本与变更记录

- 1.0 (2026-06-16) — 初始版本
