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
