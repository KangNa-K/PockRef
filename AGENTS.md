# PockRef (Android Compose — Clip Studio Paint 参考图板)

## 当前分支状态 (2026-06-04)

- 工作分支:`feature/merge-context-menu-into-floating-toolbar`
- 平台:Android only (Galaxy Tab S10+ 平板 + S Pen,340dpi landscape)
- 架构:Kotlin + Jetpack Compose + Material 3,ViewModel + StateFlow + 共享 `PockRefUiState`

## 最近 commit (新 → 旧)

```
599d995 Collapsed minimap icon: 44dp FAB-style + Map icon
eb705ed Smart chrome: minimap shrink + responsive pill
8c3244b Single-select expanded: restore full 4+8 / 4+10 action set
0b7ba30 Two changes: S Pen press-and-hold + restore single-select collapse
adc7d9d Multi-select batch export: surface Export button in collapsed panel
ce5f3d5 Merge commit 8d9d74e (selection refactor) onto current branch
bf5fb26 feat(spen): S Pen side button → selection mode binding (L2)
a100580 Gesture toggles: 8 per-gesture on/off switches in settings panel
a701a31 Settings panel: '背景颜色' label + HSV color wheel
78bee94 Top toolbar settings refactor: gear button + ModalBottomSheet settings panel
```

## ⚠️ 8d9d74e merge 丢掉的特性 (重要!)

`ce5f3d5` 把 8d9d74e 的 selection refactor 合进来,代价是当前分支的下列特性**没了**:

- **Settings panel** (ModalBottomSheet,5 个 section: edge snap / rotation snap / theme / background color / gesture toggles)
- **HSV color wheel**(原本在 settings panel 里)
- **Lock 特性**(SelectionFloatingToolbar 的 4 按钮 locked 模式 + 锁定图片的红色双层框)
- **8 个 gesture toggles**(单指拖动 / 双指缩放 / 双指旋转 / 双指竖直滑 / 双指点 / 三指点 / 三指双击 / 双击空白)
- **顶栏 gear 入口**(settings 入口)

8d9d74e 自己的旧 toolbar 还把 边缘吸附 / 旋转吸附 / palette 摆回顶栏内联,没 settings。

**恢复计划**:在 8d9d74e 基线上逐个 re-apply 上述特性,每个 1-2 commit 独立可 review。预计 4-6 commit。

**应急回滚**:`git tag -l "backup*"` 有 `backup-pre-merge-c327081-8d9d74e` 在 a100580,merge 前的状态。

## 8d9d74e 已知 bug 修复

`AlignMode` enum 在 8d9d74e 文件里**缺** ALIGN_TOP / BOTTOM / LEFT / RIGHT 4 个 case,但 `SelectionFloatingToolbar` 已经在 dispatch 这 4 个 enum 值。修复在 `ce5f3d5` commit 里(enum 补齐 + `PockRefViewModelBatch.batchAlign` 加 4 个 when 分支)。

## SelectionFloatingToolbar 模式布局 (8d9d74e 之后的标准)

- 0 选中 → 隐藏
- 1 选中 → 5 折叠 (镜像/裁切/灰度/删除/展开) / 11 展开(镜像/裁切/灰度/锁定/左旋/右旋/复原/复制/导出/删除/收起)
- ≥2 选中 → 5 折叠 (水平反转/灰度/删除/导出/展开) / 12 展开(12 按钮,镜像/灰度/锁定/复原/复制/4 对齐/导出/删除/收起)
- 全部用 `Dimens.EdgeInset / ChromeCornerRadius / ChromeSurfaceAlpha / ToolbarElevation` token 统一 chrome 风格
- `expanded` 状态 hoist 到函数顶层,`remember(selectedCount)` 让 1↔2 切换时自动重置

## 底部 chrome 元素 (FAB 风格统一)

3 个底部元素共享 `Dimens.*` token:

| 元素 | 位置 | 尺寸 | Elevation | 用途 |
|---|---|---|---|---|
| `CanvasMinimap` | BottomStart | ~200dp 动态 | 1dp + 1dp border | 全局缩略图 |
| `CollapsedMinimapIcon` | BottomStart | **44dp** | **6dp FabElevation** | 选中时 minimap 折叠态(图标 = `Icons.Rounded.Map`) |
| `SelectionStatusBar` (pill) | BottomCenter | 120-280dp | 3dp ToolbarElevation | "已选 N 张" + 复制/删除(wide)或 1 个"更多"菜单(narrow <480dp) |
| 一键排版 FAB | BottomEnd | 44dp | 6dp FabElevation | 自动排版(图标 = `Icons.Rounded.AutoFixHigh`) |

minimap 折叠态的 44dp + FabElevation 跟 FAB 完全一致(token 一致),用户读成"两个 corner 浮动"。

## SelectionStatusBar (小白条) 缩放交互

小白条长按可唤出缩放 Slider，拖拽直接控制画布缩放，交互流程如下：

| # | 手势 | 触发条件 | 行为 |
|---|------|---------|------|
| 1 | 长按 | 按住 ≥ 500ms | Slider 从小白条上方浮现 |
| 2 | 松手(长按后) | 手指抬起 | Slider 保持显示 3s 后自动淡出消失 |
| 3 | 长按 + 向上拖 | 长按触发后继续上拖 | 画布实时放大，Slider thumb 同步上移 |
| 4 | 长按 + 向下拖 | 长按触发后继续下拖 | 画布实时缩小，Slider thumb 同步下移 |

**实现要点**：

- **长按检测**：`pointerInput` + `detectTapGestures(onLongPress)` 或自定义 `awaitPointerEventScope`，阈值 500ms
- **Slider 位置**：紧贴小白条上方，水平方向居中对齐，宽度与小白条等宽或略宽
- **拖拽映射**：垂直拖拽偏移量 → `scale` 值，向上 = 放大，向下 = 缩小；Slider thumb 位置与当前 scale 同步
- **自动消失**：松手后启动 3s `LaunchedEffect` / `delay`，到时 `animateFloatAsState` 淡出 Slider
- **中断重置**：Slider 消失动画期间若再次长按，取消当前 timer，重新显示 Slider
- **缩放范围**：与双指缩放共享同一 `minScale..maxScale` 区间，Slider 刻度对齐

## S Pen 按住 = 开框选,松开 = 关

- `SpenButtonDetector.ButtonEvent.BoxStart` (DOWN 边) → `MainActivity.dispatchSpenEvent` 直接 dispatch `SetSelectionMode(BOX) + BeginBoxSelect`
- `SpenButtonDetector.ButtonEvent.BoxEnd` (UP 边) → dispatch `SetSelectionMode(SINGLE)` 退出框选
- detector 单元测试 11 个 case 全过 (`./gradlew :app:testDebugUnitTest --tests "com.pockref.app.spen.SpenButtonDetectorTest"`)
- **V_NOW:检测路径简化为 Activity 级别的 dispatch hooks**,不再用悬浮窗:
  - `MainActivity.dispatchTouchEvent` 读 `event.buttonState` → `detector.onDown/onUp`(笔尖触屏路径)
  - `MainActivity.dispatchGenericMotionEvent` 读 `event.buttonState` → 同上(笔悬浮路径,这是 spen 项目的参考实现)
  - **不需要 SYSTEM_ALERT_WINDOW / a11y / shizuku 权限**,直接走 framework 的标准 input dispatch 路径,Samsung fork 在这条路上不剥 button bit
  - SM-X810 实测可用(spen 项目的 `MainActivity.detectStylusButtonState` 是同一份思路)
- 旧实现(已删除):`SpenButtonOverlay` + `SpenButtonCoordinator` + `SpenButtonBus` + `SpenButtonAuthorization` 共 4 个文件 / 879 行 → 现在 1 个 detector + 1 个 dispatch hook,约 320 行。删除原因:overlay 由 MainActivity 自启(不是 a11y service),Samsung fork 在私有 overlay 路径上剥 button bit,所以真机不 fire。`AndroidManifest.xml` 的 `SYSTEM_ALERT_WINDOW` 权限也同步删除
- 跨 App 后台监听(S Pen 在别的 App 时 QuickMenu 后台也能识别)目前**不支持**。spen 项目用 `SpenInputMonitor`(Shizuku /dev/input 读 kernel event)解决,需要用户授权 Shizuku。当前 QuickMenu 只在自身 Activity 内监听,够用
