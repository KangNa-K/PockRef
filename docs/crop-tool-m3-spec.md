# PockRef 裁切工具 — Material Design 3 UI 设计规范

> 版本: 1.0 | 日期: 2026-06-06
> 适用平台: Android (Galaxy Tab S10+ landscape 340dpi)
> 设计基准: Material Design 3 (M3) Compose 组件库

---

## 1. 触发场景与入口

| 属性 | 值 |
|---|---|
| 触发图标 | `Icons.Rounded.Crop` (Material Symbols `crop`) |
| 入口位置 | SelectionFloatingToolbar 折叠态第 3 位 / 展开态第 3 位 |
| 触发条件 | 单选图片 + 点击裁切图标 |
| 前置守卫 | `image.locked == true` → 拦截,不进入裁切模式 |
| 触发 Action | `PockRefAction.EnterCropMode` |

---

## 2. 界面层级树

```
ModalBottomSheet (cropSheet)                    ← M3 ModalBottomSheet
├── HandleBar                                   ← 32dp × 4dp, 圆角 16dp
├── CropCanvasArea (weight=1f)                  ← 主视觉区域
│   ├── ImageLayer                              ← 原图 + 旋转渲染
│   ├── DimOverlay                              ← 四区半透明遮罩
│   ├── GridLines                               ← 九宫格参考线
│   ├── CropBorder                              ← 裁切框白色描边
│   ├── CornerHandles (×4)                      ← 四角 L 型手柄
│   ├── EdgeHandles (×4)                        ← 四边中点圆形手柄
│   └── DimensionChips                          ← 宽/高/宽高比浮层
├── AspectChipRow                               ← 宽高比选择
│   └── FilterChip × 8                          ← 自由/1:1/4:3/3:4/16:9/9:16/3:2/2:3
└── ActionBar                                   ← 操作按钮行
    ├── Spacer(weight=1f)
    ├── TextButton「取消」
    ├── Spacer(8dp)
    └── Button「应用」                           ← Filled, primary
```

---

## 3. 组件清单与 M3 Token 引用

### 3.1 ModalBottomSheet

| 属性 | 值 | Token / 说明 |
|---|---|---|
| `sheetState` | `rememberModalBottomSheetState(skipPartiallyExpanded = false)` | medium + expanded 两档 detent |
| `shape` | `RoundedCornerShape(topStart=28.dp, topEnd=28.dp)` | M3 `Shape.CornerExtraLarge` |
| `containerColor` | `surfaceContainerLow` | M3 `surface-container-low` |
| `scrimColor` | `scrim` alpha 0.32 | M3 `scrim` |
| `tonalElevation` | 1.dp | M3 level 1 |
| `dragHandle` | 自定义 HandleBar (见下) | 替代默认 drag indicator |

### 3.2 HandleBar

| 属性 | 值 | Token |
|---|---|---|
| 宽度 | 32.dp | — |
| 高度 | 4.dp | — |
| 圆角 | 16.dp | — |
| 颜色 | `onSurfaceVariant` alpha 0.4 | M3 `on-surface-variant` |
| 位置 | Sheet 顶部居中, padding top 16.dp | — |

### 3.3 CropCanvasArea

| 属性 | 值 | Token |
|---|---|---|
| 背景 | `surface` | M3 `surface` |
| 圆角 | `ChromeCornerRadius` (12.dp) | 项目 token |
| 内边距 | 0.dp (图片贴边) | — |
| 图片渲染 | `drawImage` + `rotate(cropRotation)` + `scale(flipX/Y)` | — |
| 遮罩色 | `Color.Black.copy(alpha=0.45f)` | 半透明遮罩 |
| 网格线色 | `outlineVariant` alpha 0.5 | M3 `outline-variant` |
| 裁切框描边 | `Color.White`, 2.dp | — |

### 3.4 Resize Handles

| 类型 | 尺寸 | 视觉 | 触控区域 | Token |
|---|---|---|---|---|
| 角手柄 (Corner) | L 型, 臂长 28dp × 臂宽 4dp | 白色实线 | 48dp × 48dp | `Dimens.MinTouchTarget` |
| 边手柄 (Edge) | 圆形, 半径 7dp | 白色填充 + 蓝色内圈 4dp | 48dp × 48dp | `Dimens.MinTouchTarget` |

**手柄拖拽反馈 (Active 状态):**

| 状态 | 阴影 | 色变 |
|---|---|---|
| Idle | 无阴影 | `Color.White` |
| Dragging | `elevation/3` (6dp) | `primary` (蓝) |

### 3.5 DimensionChips (实时数值浮层)

| 属性 | 值 | Token |
|---|---|---|
| 组件 | `AssistChip` | M3 |
| 位置 | 裁切框上方 12dp 居中 | — |
| 内容 | `{width} × {height}` + `{ratio}` | — |
| 文字色 | `onSurfaceVariant` | M3 |
| 容器色 | `surfaceContainerHighest` alpha 0.9 | M3 |
| 可见条件 | `cropState == Active` (拖拽中) | — |

### 3.6 AspectChipRow

| 属性 | 值 | Token |
|---|---|---|
| 组件 | `FilterChip` × 8 | M3 |
| 间距 | `Arrangement.spacedBy(8.dp)` | M3 chip 间距 |
| 水平内边距 | 16.dp | — |
| 选中态 | `FilterChip(selected=true)` | M3 自动着色 |
| 未选中态 | `FilterChip(selected=false)` | M3 自动着色 |
| 文字样式 | `MaterialTheme.typography.labelSmall` | M3 |

**宽高比枚举 (扩展后):**

| 枚举 | label | ratioW | ratioH |
|---|---|---|---|
| `FREE` | 自由 | 0 | 0 |
| `RATIO_1_1` | 1:1 | 1 | 1 |
| `RATIO_4_3` | 4:3 | 4 | 3 |
| `RATIO_3_4` | 3:4 | 3 | 4 |
| `RATIO_16_9` | 16:9 | 16 | 9 |
| `RATIO_9_16` | 9:16 | 9 | 16 |
| `RATIO_3_2` | 3:2 | 3 | 2 |
| `RATIO_2_3` | 2:3 | 2 | 3 |

### 3.7 ActionBar

| 属性 | 值 | Token |
|---|---|---|
| 布局 | `Row`, 右对齐, 16.dp 水平内边距 | — |
| 取消按钮 | `TextButton` | M3, text-only |
| 取消文字色 | `onSurface` | M3 |
| 应用按钮 | `Button` (Filled) | M3 primary |
| 应用图标 | `Icons.Rounded.CheckCircle` 20.dp | — |
| 按钮间距 | 8.dp | M3 dialog action spacing |
| 垂直内边距 | 8.dp | — |
| 底部安全区 | `WindowInsets.navigationBars` padding | — |

---

## 4. 状态机流程图

```
                    ┌─────────────────────────────────────┐
                    │         用户点击 Crop 图标            │
                    └──────────────┬──────────────────────┘
                                   │ EnterCropMode
                                   ▼
                    ┌─────────────────────────────────────┐
                    │           IDLE (待裁切)               │
                    │  · cropRect = CropRect(5%,5%,95%,95%)│
                    │  · cropAspect = FREE                 │
                    │  · cropRotation = 0°                 │
                    │  · BottomSheet → expanded             │
                    └──────┬──────────┬───────────────────┘
                           │          │
              手柄/框拖拽   │          │  点击宽高比 Chip
                           ▼          ▼
                    ┌──────────┐  ┌──────────────────────┐
                    │  ACTIVE  │  │  ASPECT_LOCKED       │
                    │ (拖拽中)  │  │  · cropAspect ≠ FREE │
                    │          │  │  · 裁切框约束到比例    │
                    └────┬─────┘  └──────────┬───────────┘
                         │                   │
              松手/拖完   │                   │ 切回 FREE
                         ▼                   ▼
                    ┌─────────────────────────────────────┐
                    │           IDLE (待裁切)               │
                    │  · cropRect 更新为拖拽结果             │
                    └──────┬──────────┬───────────────────┘
                           │          │
                  点击应用  │          │ 点击取消 / 手势返回
                           ▼          ▼
              ┌────────────────┐  ┌──────────────────┐
              │   CONFIRMED    │  │    CANCELLED      │
              │ · commitCrop() │  │ · cancelCrop()    │
              │ · 像素级裁切    │  │ · 恢复原状         │
              │ · Sheet dismiss│  │ · Sheet dismiss   │
              └────────────────┘  └──────────────────┘
```

**状态字段映射:**

| 状态 | `cropImageId` | `dragEdge` | 说明 |
|---|---|---|---|
| 无裁切 | `null` | — | 主画布正常交互 |
| IDLE | 非 null | `null` | 裁切框可见,等待用户操作 |
| ACTIVE | 非 null | 非 null | 手柄/框拖拽中 |
| ASPECT_LOCKED | 非 null | `null` | 宽高比锁定,拖拽受约束 |
| CONFIRMED | → null | — | `commitCrop` 执行中 |
| CANCELLED | → null | — | `cancelCrop` 已清理 |

---

## 5. 关键页面线框图描述

### 5.1 BottomSheet — Expanded (全屏裁切模式)

```
┌──────────────────────────────────────────────────────┐
│                    ═══ HandleBar ═══                   │  ← 32dp 宽, 居中
│                                                       │
│  ┌─────────────────────────────────────────────────┐  │
│  │                                                 │  │
│  │              [图片渲染区域]                       │  │  ← 圆角 12dp
│  │                                                 │  │
│  │    ┌──────────────────────────────────┐         │  │
│  │    │  ▓▓ 裁切框 ▓▓                    │         │  │  ← 白色描边
│  │    │    ┼──────┼──────┼              │         │  │  ← 九宫格线
│  │    │    │      │      │              │         │  │
│  │    │    ┼──────┼──────┼              │         │  │
│  │    │    │      │      │              │         │  │
│  │    │    └──────┘──────┘              │         │  │
│  │    ○  ○                          ○  ○│         │  │  ← 角/边手柄
│  │    │                              │  │         │  │
│  │    └──────────────────────────────────┘         │  │
│  │                                                 │  │
│  │    ┌─ 1920 × 1080 · 16:9 ─┐                    │  │  ← DimensionChip (拖拽时)
│  └─────────────────────────────────────────────────┘  │
│                                                       │
│  ┌─────────────────────────────────────────────────┐  │
│  │ [自由] [1:1] [4:3] [3:4] [16:9] [9:16] [3:2] [2:3] │  ← FilterChip 行
│  └─────────────────────────────────────────────────┘  │
│                                                       │
│                          [取消]  [✓ 应用]              │  ← 右对齐
│                                                       │
└──────────────────────────────────────────────────────┘
```

### 5.2 BottomSheet — Medium (半屏预览)

```
┌──────────────────────────────────────────────────────┐
│                    ═══ HandleBar ═══                   │
│                                                       │
│  ┌─────────────────────────────────────────────────┐  │
│  │              [图片渲染区域 — 缩小]                │  │  ← 高度 ≈ 40% 视口
│  │    ┌──────────────────────────┐                  │  │
│  │    │  ▓▓ 裁切框 ▓▓            │                  │  │
│  │    └──────────────────────────┘                  │  │
│  └─────────────────────────────────────────────────┘  │
│                                                       │
│  ┌─────────────────────────────────────────────────┐  │
│  │ [自由] [1:1] [4:3] [3:4] [16:9] [9:16] [3:2] [2:3] │
│  └─────────────────────────────────────────────────┘  │
│                          [取消]  [✓ 应用]              │
└──────────────────────────────────────────────────────┘
```

### 5.3 手柄布局详图

```
         ○───────○───────○
         │                │
         │                │
         ○                ○
         │                │
         │                │
         ○───────○───────○

  ○ = 角手柄 (L 型, 28×4dp 臂)  +  边手柄 (圆形, r=7dp)
  触控热区 = 48dp × 48dp (Dimens.MinTouchTarget)
```

---

## 6. 动效参数表

| 交互 | 属性 | 曲线 / 参数 | 时长 | 备注 |
|---|---|---|---|---|
| BottomSheet 弹出 | `animateColorAsState` + slide | `EmphasizedDecelerate` (cubic-bezier(0.05,0.7,0.1,1.0)) | 250ms | M3 standard bottom sheet |
| BottomSheet 收起 | slide + fade | `EmphasizedAccelerate` (cubic-bezier(0.3,0.0,0.8,0.15)) | 200ms | M3 standard dismiss |
| 裁切框吸附 (宽高比切换) | `animateRectAsState` | `Spring(stiffness=Spring.StiffnessMediumLow, dampingRatio=0.75)` | ~200ms | RubberBand 弹性效果 |
| 手柄拖拽阴影 | `animateDpAsState` | `EaseInOut` | 100ms | elevation/1 → elevation/3 |
| 手柄拖拽色变 | `animateColorAsState` | `EaseInOut` | 100ms | White → primary |
| DimensionChip 出现 | `AnimatedVisibility` + fade | `EaseIn` | 150ms | 拖拽开始时淡入 |
| DimensionChip 消失 | `AnimatedVisibility` + fade | `EaseOut` | 150ms | 拖拽结束后淡出 |
| 遮罩区透明度 | `animateFloatAsState` | `EaseInOut` | 100ms | 裁切框移动时实时更新 |
| 宽高比 Chip 选中 | `FilterChip` 内置动画 | M3 default | M3 default | 涟漪 + 填充过渡 |

---

## 7. 主题与色彩 Token 映射

| 用途 | Light Token | Dark Token | Compose 引用 |
|---|---|---|---|
| Sheet 容器 | `#F7F2FA` (surface-container-low) | `#1D1B20` (surface-container-low) | `MaterialTheme.colorScheme.surfaceContainerLow` |
| 遮罩 | `Black 45%` | `Black 55%` | `Color.Black.copy(alpha=0.45f/0.55f)` |
| 网格线 | `#CAC4D0` (outline-variant) 50% | `#49454F` (outline-variant) 50% | `MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.5f)` |
| 裁切框描边 | `#FFFFFF` | `#FFFFFF` | `Color.White` |
| 角手柄 | `#FFFFFF` | `#FFFFFF` | `Color.White` |
| 边手柄外圈 | `#FFFFFF` | `#FFFFFF` | `Color.White` |
| 边手柄内圈 | `#2196F3` (primary) | `#D0BCFF` (primary) | `MaterialTheme.colorScheme.primary` |
| 手柄 Active 色 | `#2196F3` / `#D0BCFF` | — | `MaterialTheme.colorScheme.primary` |
| DimensionChip 容器 | `#E6E0E9` (surface-container-highest) 90% | `#373540` 90% | `surfaceContainerHighest.copy(alpha=0.9f)` |
| DimensionChip 文字 | `#49454F` (on-surface-variant) | `#CAC4D0` | `onSurfaceVariant` |
| 取消按钮文字 | `#1D1B20` (on-surface) | `#E6E0E9` | `onSurface` |
| 应用按钮容器 | `#6750A4` (primary) | `#D0BCFF` | `primary` |
| 应用按钮文字 | `#FFFFFF` (on-primary) | `#381E72` (on-primary) | `onPrimary` |
| HandleBar | `#49454F` (on-surface-variant) 40% | `#CAC4D0` 40% | `onSurfaceVariant.copy(alpha=0.4f)` |

---

## 8. 操作逻辑详细规范

### 8.1 进入裁切 (EnterCropMode)

```
前置条件: selectedImageId != null && image.locked != true
状态变更:
  cropImageId = selectedImageId
  cropRect = CropRect(0.05, 0.05, 0.95, 0.95)
  cropAspect = FREE
  cropRotation = 0f
UI 变更:
  ModalBottomSheet show → expanded detent
  主画布手势暂停 (pointerInput 消耗所有事件)
```

### 8.2 拖拽手柄 (Handle Drag)

```
触发: pointerInput detectDragGestures
  onDragStart → 判定 hit 区域 (touchPadding = 28dp)
    - 角手柄: "lt" / "rt" / "lb" / "rb"
    - 边手柄: "l" / "r" / "t" / "b"
    - 框内: "move"
  onDrag →
    if cropAspect == FREE:
      直接更新 cropRect (各边独立移动)
    else:
      锁定宽高比,联动更新 (同现有 _lock 逻辑)
    dispatch UpdateCropRect(newRect)
  onDragEnd → dragEdge = null
```

### 8.3 宽高比切换 (SetCropAspect)

```
触发: FilterChip onClick
状态变更:
  cropAspect = selected aspect
  cropRect = cropRect.withAspect(aspect, imgW, imgH)
动画:
  animateRectAsState(cropRect, Spring(RubberBand))
```

### 8.4 确认裁切 (CommitCrop)

```
触发: Button「应用」onClick → PockRefAction.CommitCrop
前置条件: cropRect.isValid() && cropPixelW > 0 && cropPixelH > 0
异步流程:
  1. setBusy(true)
  2. IO 线程:
     a. 解码原图 bitmap
     b. 若 cropRotation ≠ 0: matrix.postRotate → 旋转像素
     c. 按 cropRect 裁切旋转后 bitmap
     d. 编码输出 (PNG/JPEG 95%)
  3. 主线程:
     a. 计算 newDisplaySize (保持面积,适应新宽高比)
     b. rotationDegrees = image.rotationDegrees (不加 cropRotation)
     c. mutateScene → 更新 image
     d. setBusy(false)
  4. BottomSheet dismiss
```

### 8.5 取消裁切 (CancelCrop)

```
触发: TextButton「取消」onClick / 手势返回
状态变更:
  cropImageId = null
  cropRect = CropRect()
  cropAspect = FREE
  cropRotation = 0f
UI 变更:
  BottomSheet dismiss
```

---

## 9. 与现有实现的差异对照

| 维度 | 当前实现 | 新规范 |
|---|---|---|
| 容器 | 自定义 Column + Box 浮层 | M3 ModalBottomSheet |
| Detent | 单档 (全屏) | medium + expanded 两档 |
| 宽高比选项 | 6 个 (缺 3:4, 2:3) | 8 个 (补 3:4, 2:3) |
| 手柄触控区 | 28dp touchPadding | 48dp MinTouchTarget |
| 手柄拖拽反馈 | 无 | 阴影 elevation/1→3 + 色变 White→primary |
| 实时数值 | 无 | DimensionChip (宽×高·比例) |
| 动效 | 无 | Spring RubberBand 吸附 + 手柄阴影动画 |
| 旋转控件 | 已移除 (UI 层) | 不恢复 (保持精简) |
| HandleBar | 无 | 32dp × 4dp, 圆角 16dp |
| 裁切框描边 | 2px 白色 | 2dp 白色 (dp 替代 px) |
| 遮罩透明度 | 30% | 45% Light / 55% Dark |
| 网格线色 | White 35% | outlineVariant 50% |

---

## 10. CropAspect 枚举扩展

需在 `Models.kt` 中新增两个枚举值:

```kotlin
enum class CropAspect(val label: String, val ratioW: Float, val ratioH: Float) {
    FREE("自由", 0f, 0f),
    RATIO_1_1("1:1", 1f, 1f),
    RATIO_4_3("4:3", 4f, 3f),
    RATIO_3_4("3:4", 3f, 4f),       // 新增
    RATIO_16_9("16:9", 16f, 9f),
    RATIO_9_16("9:16", 9f, 16f),
    RATIO_3_2("3:2", 3f, 2f),
    RATIO_2_3("2:3", 2f, 3f),       // 新增
}
```

---

## 11. 无障碍 (Accessibility)

| 要素 | 实现 |
|---|---|
| BottomSheet 半模态 | `ModalBottomSheet` 自带 a11y 语义 |
| 手柄 contentDescription | "左上角裁切手柄" / "右边裁切手柄" 等 |
| 宽高比 Chip | `FilterChip` 自带 semantics |
| 应用/取消按钮 | `Button` / `TextButton` 自带 semantics |
| 裁切框数值 | `LiveRegion` + `announceForAccessibility` |
| 键盘导航 | Tab 顺序: Chip 行 → 取消 → 应用 |
