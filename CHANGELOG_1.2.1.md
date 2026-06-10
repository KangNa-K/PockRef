# PockRef v1.2.1 更新说明

## 更新日期

2026-06-08

***

## 框选模式支持窗交和窗选两种选择方式

## 修复导出画布图片显示不全的问题

##导出画布新增"无尺寸上限"选项

###改动

- 「导出画布」(右下角面板)弹出一个新的对话框,提供2 个画质选项:
1. **标准 (最长8192px)** — 默认行为,画布按8192 等比缩,内存峰值 ~256MB。
2. **原始画质 (无尺寸上限)** —画布按实际像素拼,可生成20000×15000+ 大图,小内存设备可能 OOM。
- 每个选项下方都附带说明文字,描述内存预期和 OOM风险,避免用户误选"无上限"导致崩溃。
- `PockRefUiState.exportCanvasMaxDim`字段: `null` = 无上限, `Int` =像素上限(默认8192)。
- `PockRefAction.SetExportCanvasMaxDim(value)` action:选 cap 并 dismiss dialog; `CancelExportCanvas` action: dismiss 不改 cap。

###行为

- 标准:跟之前的8192 cap 完全一致,所有现有 export 测试不变。
- 无上限:画布按实际 bbox 输出,每张图按 intrinsic像素解码(去掉了之前的4096 per-image cap)。

###注意事项

- "无上限"模式在大画布下会占用大量内存(20000×15000 ARGB_8888 ≈1.2GB),设备 heap不足时会 OOM。
-选了"无上限"导出后,`PockRefUiState.exportCanvasMaxDim` 会一直保持 `null`,直到下次"导出画布"重新选选项(下次点开 dialog 默认显示"标准"按钮为 highlighted,但实际"无上限"仍生效,这是已知 follow-up)。

### 测试

- `ExportCanvasMaxDimTest` (7 个 case): 默认8192 /4096 / null 三种 maxDim 的 scale 和 maxImageDim行为,以及 unlimited模式下 per-image 解码到 intrinsic。
- `PureRefActionsTest` 的 `expectedVariantCount` 从127升到129(新增 SetExportCanvasMaxDim + CancelExportCanvas)。
- 全套274 tests 全过。

##修复"原始画质(无尺寸上限)导出图挤左上角"bug

###根因

`PureRefViewModelScene.exportCanvas` 的 Pass1 bbox 计算混用了两个坐标系:
- 图尺寸 `(w, h)` = `image.intrinsicWidth * image.scale`(intrinsic像素)
- 图中心 `(cx, cy)` = `image.center.x / .y`(displaySize坐标,用户拖拽布局用的坐标系)

`image.center` 是用户在 displaySize坐标系下拖拽的位置(`.pur`矩阵编码时就是这么存的),但 Pass1 用 intrinsic像素算 extent → bbox范围比实际布局大 `1/displayScale` 倍,但锚点还在 displaySize坐标 → 每张图的 dstRect 向 (0,0)偏移,所有图全部坍缩到左上角。

###修复

Pass1改成 **统一在 intrinsic坐标系** 做:
- `(w, h) = intrinsic * image.scale`(不变)
- `(cx, cy) = image.center / displayScaleX` ,其中 `displayScaleX = displaySize.width / intrinsicWidth`
- 对 isotropic `.pur`(desktop PockRef 默认) `displayScaleX = displayScaleY`,转换自然一致
- anisotropic `.pur`罕见,可接受 per-axis转换后 (w, h) 是 isotropic 的小 trade-off

###测试

- `ExportCanvasPass1CenterTest.pass1_bboxUsesIntrinsicCoordsAndConvertsCenter`:验证转换数学正确,buggy 版本 bbox left edge 是负数(坍缩到 (0,0)),修复后是正数。
- `ExportCanvasPass1CenterTest.pass1_twoImagesAtDifferentCenters_ProduceSeparateBboxExtents`: 两个 image 在 displaySize坐标下相隔3000 px,修复后 intrinsic bbox 不重叠(gap=6000),buggy 版本会坍缩到一起。
- 全套276 tests 全过(原274 + 新增2)。

***

## 1.2.1 polish — chrome unify + import popup + lock-mode parity

post-release commit batch (5 commits),versionCode 4 → 5:

### 1. 顶栏 chrome unify (#1)

5 个常驻 icon (chevron / import / undo / redo / close) 大小完全一致:
- 全部 40dp 触摸盒 + 20dp icon (原本 chevron/import 是 18dp)
- 全部 `onSurfaceVariant` tint
- chevron active 时背景染 `primaryContainer` (跟 Lock 按钮激活态同色),开/关 recent files popup 时切换
- 顶栏 + 右下角 panel 全部 `tonalElevation = 0.dp`,不再带 8% primary overlay,纯 onSurface

### 2. 顶栏 import 按钮 → 3 选项 popup (#2)

tap 后弹 `DropdownMenu`,3 个选项:
- **相册** — `PickMultipleVisualMedia` (Android 13+ PhotoPicker,自动 fallback 到 SAF)
- **剪贴板** — `ClipboardManager.getPrimaryClip()` (读 image URI item;空剪贴板静默 no-op)
- **文件** — 现有 SAF `OpenMultipleDocuments` (保留作为"我要从 Downloads/SD 卡取特定文件"的 power-user 入口)

新增 state wire:
- `PockRefUiState.importSourceMenuOpen: Boolean`
- `PockRefAction.SetImportSourceMenuOpen(open: Boolean)`
- `PureRefViewModel.setImportSourceMenuOpen()` reducer

空画布 fallback (中央"导入图片"按钮) 保留原 SAF 直开行为 — popup 锚在顶栏,从画布中央 tap 会跳到屏幕角落,jumpy。

### 3. Corner handle 拖完自动 unfocus (#3)

fitted 状态下 (双击图 → canvas zoom-in) 拖 corner handle resize,松手后 canvas 自动 unfocus:
- 旧行为:corner drag 松手只 reset local var,fittedImageId 不清空,canvasScale 还是 zoomed-in,需要用户再双击才能 unfocus
- 新行为:corner drag up handler 重新 dispatch `FitImageToScreen(imageId)`,reducer toggle 路径恢复 `preFitCanvasScale`

### 4. Lock-mode 行为对齐 (#4)

3 个 sub-fix:
- **#4a** per-image `image.locked = true` 上单指拖 = dragCanvas (移画布),跟 `globalImageLock` 行为一致 (旧行为: silent no-op)
- **#4b** `lockCanvasFrozen = true` 下双指 gesture 在 UI 层 early-skip transform math (保留 hit-test / image select,但跳过 centroid/distance/mode commit/dispatch),reducer 反正拒绝,消除 wasted CPU + 用户感知的 lag
- **#4c** locked image 双击 → 拒绝 fit 新的 (locked 是 view-only,zoom in 没意义);如果当前有 `fittedImageId`,把它 unfocus;否则 no-op

### 5. 卫生修复 (8d9d74e merge residue)

`PureRefViewModel.kt` reducer 有 3 处 `Duplicate label in when` warning (来自 8d9d74e cherry-pick 残留):
- `SetCanvasOffsetFromMinimap` 第二个 arm 既重复又 **丢了 lockCanvasFrozen 守卫**(如果执行会破坏 frozen-mode 的 "minimap click-to-jump is blocked" 契约)
- `SetSelectedImageScale` + `SetSelectedImagesScale` 完全重复

删 dead duplicate。

### 测试

- 291 unit tests 全过(原 276 + #2 加 `expectedVariantCount` 132→133 + #4 没新增 test 因为 fitImageToScreen 在 ViewModel 内需要 Robolectric,留作 follow-up)
- Build 干净:0 Duplicate label warnings,0 unclosed comment warnings
- 2 个 cosmetic "Unnecessary safe call" 警告残留 (`PureRefApp.kt:6601/6617`),base HEAD 已有,与本次改动无关

### Build artifacts

- `~/Desktop/pockref-v1.2.1-20260610-0537.apk` (含 #4)
- 后续 polish APK 见 commit history 的 timestamp 命名

***

## 1.2.1 follow-up #1 — 顶栏 import popup 视觉/位置/动画统一

user bug report: "#2 import popup 跟 chevron 的最近文件 popup 看起来是两种东西, 但功能上是一类 (都是顶栏的二级选项面板), 不一致很怪. 让我之前的 import popup 跟最近的最近文件 popup 样式、位置、动画一致".

### 改了什么

- **删 M3 `DropdownMenu`**: 旧的 import popup 是独立 Popup window (跟顶栏 Surface 不连续, 浮在 canvas 上方, 圆角/边框/背景跟顶栏脱节). 删掉.
- **改 in-toolbar Surface**: 新的 import popup 是顶栏 Surface 内的 sibling `AnimatedVisibility` (跟 recent files popup 同一层), 共享 Surface 的圆角/边框/背景, 展开时顶栏 Surface 高度向下生长 = 一个连续 chrome 块.
- **同款动画**: `expandVertically(tween 200, FastOutSlowInEasing)` + `shrinkVertically(tween 200, FastOutSlowInEasing)`, 跟 recent files popup 完全相同, 两者同开/同关时间感.
- **同款 width 跟随**: popup 宽度绑定 `toolbarRowWidth` (跟 recent files popup 一样, 顶栏窄时 popup 窄, 顶栏宽时 popup 宽).
- **toggle 行为**: import 按钮再点一次 = 关闭 (跟 chevron 一致), 不依赖 `DropdownMenu` 内置的 `onDismissRequest`.
- **chrome-mutex**: 打开 import popup 时 force-close recent files popup (反之亦然), 两者不会同时显示.
- **scrim**: parent level 加 tap-outside dismiss (跟 recent files popup 的 scrim 一样, `matchParentSize` + `clickable(indication=null)`).
- **state hoist**: `importSourceMenuOpen` 跟 `menuExpanded` 一样 hoist 到 [PockRefApp] (经 `PockRefAction.SetImportSourceMenuOpen` 走 ViewModel reducer), 不是 `TopToolbar` 内的 local var — 让 parent scrim 跟 chrome-mutex 跟 state 一致.

### 行为

- 单击 import 按钮 → 弹出 3 选项 popup (相册/剪贴板/文件), 顶栏 Surface 高度增加, 视觉上一个连续 chrome 块
- 单击 popup 内某项 → 关闭 popup + 触发对应 launcher
- 单击 popup 外部 (canvas / 其它区域) → 关闭 popup (scrim)
- 再单击 import 按钮 → 关闭 popup (toggle)
- 单击 chevron 打开 recent files popup 时 → 自动 force-close import popup
- 3 选项的 visual: leading 18dp icon (PhotoLibrary/ContentPaste/FileOpen, onSurfaceVariant tint) + bodyMedium Text (onSurface), clickable + 12h/10v padding, 跟 recent files Row 视觉一致

### 测试

- 291 unit tests 全过 (没新增 test 因为 UI 改动不涉及 ViewModel 行为, state wire 沿用 #2 加的 `SetImportSourceMenuOpen`)
- Build 干净: 0 Duplicate label warnings, 0 unclosed comment warnings
- `onPickGallery` / `onPickClipboard` / `onPickFiles` 参数 `never used` warning 消失 (新 popup 在用)

### Build artifacts

- `~/Desktop/pockref-v1.2.1-20260610-1019.apk` (本次)
