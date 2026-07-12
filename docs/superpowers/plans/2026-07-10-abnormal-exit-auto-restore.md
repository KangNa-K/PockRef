# 异常退出自动恢复 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在无外部文件打开请求的冷启动中，检测上次异常结束的工作会话并自动恢复最近一个有效内部备份。

**Architecture:** 新增独立的 `RecoverySessionManager`，只负责同步持久化异常会话标记和纯恢复决策；`AutoSaveManager` 负责按时间提供并解析备份候选；`PockRefViewModel` 负责把恢复场景装载到现有 UI state；`MainActivity` 在解析启动 Intent 后决定外部文件优先还是执行一次恢复。恢复后的内容不绑定正式 URI，并保持未保存状态。

**Tech Stack:** Kotlin、Android SharedPreferences、Jetpack ViewModel/StateFlow、现有 `PurSceneCodec`、JUnit4、Gradle。

---

## 文件结构

- Create: `app/src/main/java/com/pockref/app/data/RecoverySessionManager.kt` — 会话标记持久化与恢复资格判断。
- Create: `app/src/test/java/com/pockref/app/data/RecoverySessionManagerTest.kt` — 会话标记和启动决策纯单元测试。
- Modify: `app/src/main/java/com/pockref/app/data/AutoSaveManager.kt` — 返回首个可解析的最新备份，并允许清除恢复资格对应的备份。
- Create: `app/src/test/java/com/pockref/app/data/AutoSaveRecoveryTest.kt` — 有效、损坏和回退候选测试。
- Modify: `app/src/main/java/com/pockref/app/ui/PureRefViewModelScene.kt` — 复用现有场景装载路径恢复内部备份。
- Modify: `app/src/main/java/com/pockref/app/ui/PureRefViewModel.kt` — 暴露一次性恢复入口、恢复提示和正常结束处理。
- Modify: `app/src/main/java/com/pockref/app/MainActivity.kt` — 冷启动 Intent 优先级与会话标记启动。
- Modify: `app/src/main/java/com/pockref/app/ui/PureRefActions.kt` — 若现有正常关闭/丢弃 action 需要增加明确会话结束 action，仅增加一个直接 sealed variant。
- Modify: `app/src/test/java/com/pockref/app/ui/PureRefActionsTest.kt` — 同步 action 数量并覆盖新 action（如实际需要）。

### Task 1: 会话标记和启动决策

**Files:**
- Create: `app/src/main/java/com/pockref/app/data/RecoverySessionManager.kt`
- Create: `app/src/test/java/com/pockref/app/data/RecoverySessionManagerTest.kt`

- [ ] **Step 1: 编写失败的纯决策测试**

测试以下表格：上次标记存在且无外部文件时恢复；有外部文件时不恢复；无标记时不恢复；本进程已经检查过时不重复恢复。

```kotlin
@Test
fun abnormalPreviousSession_withoutExternalOpen_shouldRestore() {
    assertTrue(shouldRestoreBackup(previousSessionActive = true, hasExternalOpenRequest = false, alreadyChecked = false))
}

@Test
fun externalOpenRequest_hasPriority() {
    assertFalse(shouldRestoreBackup(previousSessionActive = true, hasExternalOpenRequest = true, alreadyChecked = false))
}

@Test
fun activityRecreation_doesNotRestoreTwice() {
    assertFalse(shouldRestoreBackup(previousSessionActive = true, hasExternalOpenRequest = false, alreadyChecked = true))
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.pockref.app.data.RecoverySessionManagerTest'`

Expected: FAIL，提示 `shouldRestoreBackup` 或 `RecoverySessionManager` 尚不存在。

- [ ] **Step 3: 实现最小会话管理器**

实现：

```kotlin
internal fun shouldRestoreBackup(
    previousSessionActive: Boolean,
    hasExternalOpenRequest: Boolean,
    alreadyChecked: Boolean,
): Boolean = previousSessionActive && !hasExternalOpenRequest && !alreadyChecked
```

`RecoverySessionManager` 使用独立 SharedPreferences：

```kotlin
class RecoverySessionManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("pockref_recovery_session", Context.MODE_PRIVATE)

    fun beginSession(): Boolean {
        val previousSessionActive = prefs.getBoolean(KEY_SESSION_ACTIVE, false)
        prefs.edit().putBoolean(KEY_SESSION_ACTIVE, true).commit()
        return previousSessionActive
    }

    fun endSession() {
        prefs.edit().putBoolean(KEY_SESSION_ACTIVE, false).commit()
    }
}
```

- [ ] **Step 4: 运行测试并确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.pockref.app.data.RecoverySessionManagerTest'`

Expected: PASS。

### Task 2: 选择最新有效备份

**Files:**
- Modify: `app/src/main/java/com/pockref/app/data/AutoSaveManager.kt`
- Create: `app/src/test/java/com/pockref/app/data/AutoSaveRecoveryTest.kt`

- [ ] **Step 1: 编写损坏回退测试**

用临时目录创建两个候选：最新文件写入垃圾字节，次新文件由 `PurSceneCodec.write` 生成。验证恢复选择器返回次新场景；全部损坏时返回 null。

```kotlin
@Test
fun newestCorruptBackup_fallsBackToOlderValidBackup() {
    val result = loadLatestValidBackup(listOf(newestCorrupt, olderValid)) { file -> codec.readFromFile(file) }
    assertEquals("older-valid", result?.scene?.name)
}

@Test
fun allCorruptBackups_returnsNull() {
    assertNull(loadLatestValidBackup(listOf(firstCorrupt, secondCorrupt)) { file -> codec.readFromFile(file) })
}
```

- [ ] **Step 2: 运行测试并确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.pockref.app.data.AutoSaveRecoveryTest'`

Expected: FAIL，恢复选择函数尚不存在。

- [ ] **Step 3: 实现纯候选选择器和生产入口**

在 `AutoSaveManager.kt` 增加：

```kotlin
internal data class RestoredBackup(val entry: AutoSaveManager.BackupEntry, val scene: PockRefScene)

internal fun loadLatestValidBackup(
    candidates: List<AutoSaveManager.BackupEntry>,
    reader: (File) -> PockRefScene,
): RestoredBackup? = candidates.firstNotNullOfOrNull { entry ->
    runCatching { RestoredBackup(entry, reader(entry.file)) }.getOrNull()
}
```

生产方法调用 `listBackups()` 和 `codec.readFromFile`。保留损坏文件用于诊断，不在恢复扫描中删除。

- [ ] **Step 4: 运行恢复候选测试**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.pockref.app.data.AutoSaveRecoveryTest'`

Expected: PASS。

### Task 3: 将备份恢复到 ViewModel

**Files:**
- Modify: `app/src/main/java/com/pockref/app/ui/PureRefViewModelScene.kt`
- Modify: `app/src/main/java/com/pockref/app/ui/PureRefViewModel.kt`
- Test: `app/src/test/java/com/pockref/app/ui/SceneContentTest.kt`

- [ ] **Step 1: 提取并测试恢复后的保存语义**

增加纯 helper，确保恢复内容不绑定 URI并被视为未保存：

```kotlin
internal data class RecoverySaveState(
    val currentSceneUri: Uri?,
    val isImportedPurFile: Boolean,
    val lastSaveSnapshotHash: String,
)

internal fun recoveredBackupSaveState(): RecoverySaveState = RecoverySaveState(
    currentSceneUri = null,
    isImportedPurFile = false,
    lastSaveSnapshotHash = "",
)
```

测试断言三个字段与上述值一致。

- [ ] **Step 2: 运行测试并确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.pockref.app.ui.SceneContentTest'`

Expected: FAIL，新 helper 尚不存在。

- [ ] **Step 3: 复用现有场景装载代码实现恢复**

在 `PockRefViewModelScene` 中新增 `restoreAutoSave(scene)`，调用现有加载场景时使用的 UI state 映射，且：

```kotlin
mainVm.currentSceneUri = null
mainVm.isImportedPurFile = false
mainVm.lastSaveSnapshotHash = ""
mainVm.undoStack.clear()
mainVm.redoStack.clear()
mainVm.markupHistory.clear()
```

不得加入最近文件列表，不得设置外部文件权限。

- [ ] **Step 4: 暴露一次性 ViewModel 恢复入口**

`PockRefViewModel.restorePreviousSessionIfAvailable()` 在 `viewModelScope` 的 IO dispatcher 读取 `AutoSaveManager.loadLatestValidBackup()`，成功后切回现有 state 更新路径并通过现有 UI 消息通道发出 `已恢复上次异常退出前的内容`。用进程内布尔值保证只执行一次。

- [ ] **Step 5: 运行 ViewModel 邻近测试**

Run: `./gradlew :app:testDebugUnitTest --tests 'com.pockref.app.ui.SceneContentTest' --tests 'com.pockref.app.data.AutoSaveRecoveryTest'`

Expected: PASS。

### Task 4: 接入冷启动优先级

**Files:**
- Modify: `app/src/main/java/com/pockref/app/MainActivity.kt`
- Modify: `app/src/main/java/com/pockref/app/ui/PureRefViewModel.kt`

- [ ] **Step 1: 确认启动 Intent 判定函数可测试**

提取纯函数：

```kotlin
internal fun Intent.hasExternalSceneOpenRequest(): Boolean =
    action == Intent.ACTION_VIEW && data != null
```

若项目已有更精确的 `.pur` Intent 判定，则复用该条件，不放宽到普通图片导入 Intent。

- [ ] **Step 2: 在 `MainActivity.onCreate` 接入会话开始**

顺序固定：

```kotlin
val previousSessionActive = recoverySessionManager.beginSession()
val hasExternalOpenRequest = intent.hasExternalSceneOpenRequest()
handleOpenIntent(intent)
if (shouldRestoreBackup(previousSessionActive, hasExternalOpenRequest, savedInstanceState != null)) {
    viewModel.restorePreviousSessionIfAvailable()
}
```

外部打开永远先处理；Activity 重建不恢复。

- [ ] **Step 3: 编译启动接入**

Run: `./gradlew :app:compileDebugKotlin`

Expected: PASS。

### Task 5: 正常结束和明确丢弃

**Files:**
- Modify: `app/src/main/java/com/pockref/app/ui/PureRefViewModelScene.kt`
- Modify: `app/src/main/java/com/pockref/app/ui/PureRefViewModel.kt`
- Modify only if required: `app/src/main/java/com/pockref/app/ui/PureRefActions.kt`
- Modify only if required: `app/src/test/java/com/pockref/app/ui/PureRefActionsTest.kt`

- [ ] **Step 1: 找到应用内明确结束路径**

将现有“关闭场景后保存”“关闭场景后丢弃”“新建空场景并确认丢弃”路径统一调用：

```kotlin
recoverySessionManager.endSession()
autoSaveManager.clearRecoveryBackups()
recoverySessionManager.beginSession()
```

这样结束的是当前工作会话，但应用继续运行时立即开始新的干净会话。不要在 `onPause`、`onStop`、Activity 普通销毁或配置变化中调用。

- [ ] **Step 2: 增加备份清理最小实现**

`clearRecoveryBackups()` 只删除 `auto_save/*.auto_backup.pur` 和遗留 `.tmp`；不修改外部文件，不清理无关缓存。

- [ ] **Step 3: 若新增 action，同步 sealed action 测试**

读取实际 `PockRefAction` 直接子类数量后更新 `expectedVariantCount`，并运行：

Run: `./gradlew :app:testDebugUnitTest --tests 'com.pockref.app.ui.PockRefActionsTest'`

Expected: PASS。若无需新增 action，不修改这两个文件。

### Task 6: 全面验证

**Files:**
- Verify all modified files

- [ ] **Step 1: 运行恢复专项测试**

Run:

```bash
./gradlew :app:testDebugUnitTest \
  --tests 'com.pockref.app.data.RecoverySessionManagerTest' \
  --tests 'com.pockref.app.data.AutoSaveRecoveryTest' \
  --tests 'com.pockref.app.data.AutoSaveContentHashTest' \
  --tests 'com.pockref.app.data.PurSceneCodecCustomFormatTest' \
  --tests 'com.pockref.app.ui.SceneContentTest'
```

Expected: PASS，0 failures。

- [ ] **Step 2: 运行全部单元测试**

Run: `./gradlew :app:testDebugUnitTest`

Expected: PASS。若仍有与本功能无关的既有失败，记录准确测试名和失败断言，不修改无关代码。

- [ ] **Step 3: 构建 Debug APK**

Run: `./gradlew :app:assembleDebug`

Expected: BUILD SUCCESSFUL，生成 `app/build/outputs/apk/debug/app-debug.apk`。

- [ ] **Step 4: 检查差异范围**

Run: `git diff --check && git status --short`

Expected: 无空白错误；保留用户预先存在的 `app/build.gradle.kts` 版本号修改，不覆盖、不提交。

- [ ] **Step 5: 真机手动验证清单**

1. 设置自动保存 1 分钟，编辑画布并等待保存提示。
2. 使用 Android Studio `Stop App` 或制造测试崩溃，重新从桌面启动，验证直接恢复并出现 Snackbar。
3. 恢复后关闭应用内场景并选择丢弃，再次强杀和启动，验证旧场景不恢复。
4. 从文件管理器点击 `.pur`，验证该文件优先打开。
5. 恢复后执行“另存为”，验证保存成功且最近文件列表正常。
