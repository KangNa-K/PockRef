# 灰度图片色调分离观察设计

## 目标

在现有选中图片的“灰度”功能之后，增加一个用于观察明暗结构的“色调分离”功能。用户点击“灰度”后，选中图片先按现有逻辑变为灰度；`SelectionBottomSheet` 顶部左侧的“已选 N 张”区域替换为四个紧凑按钮：`2级`、`4级`、`8级`、`16级`。

色调分离只改变当前画布预览，不修改原始图片像素，也不改变现有灰度功能的保存、撤销和导出行为。

## 范围

### 包含

- 保留现有“灰度”按钮及其 `ColorAdjust.saturation` 行为。
- 灰度操作成功后打开色调分离按钮组。
- 支持 `2 / 4 / 8 / 16` 四种灰度级数。
- 当前级数按钮显示激活状态。
- 再次点击当前激活按钮时关闭色调分离，恢复普通灰度。
- 点击其它级数时直接切换，不经过关闭状态。
- 多选时对当前选中图片使用同一个级数。
- 支持普通 Bitmap、分块 Bitmap 和 GIF 当前帧的画布预览。
- 在选择变化、场景切换和退出灰度时清理不再适用的临时预览状态。

### 不包含

- 不增加阈值滑杆。
- 不把 `2级`命名为“阈值”；它表示两级色调分离，即黑白二分。
- 不增加独立的“色调分离开关”。关闭方式是再次点击当前已激活的级数按钮。
- 不把色调分离级数写入 `SceneImage`、`.pur` 文件、自动保存或最近文件。
- 不让色调分离效果进入画布导出、选中图片原图导出或缩略图持久化。
- 不重写已有灰度的混合选择语义、颜色调整保存格式或撤销模型。
- 不把色调分离按钮放入底部动作网格；按钮只出现在 sheet 顶部左侧区域。

## 术语和效果定义

本功能的“色调分离”指 posterize：先得到当前图片的亮度，再将亮度量化到固定数量的灰度等级。它不是连续阈值调节，也不是把图片裁切成黑白掩码。

量化采用“最近等级”规则：

```text
quantized = round(luminance * (levels - 1)) / (levels - 1)
```

其中 `luminance` 在 `0..1`，`levels` 只能是 `2、4、8、16`。这样：

- `2级`只有黑和白，分界位于中间亮度附近；
- `4级`保留黑、深灰、浅灰、白四个等级；
- `8级`和 `16级`依次保留更多明暗细节；
- Alpha 通道保持原值，透明图片不会因为观察滤镜变成不透明。

对于彩色输入，色调分离阶段使用当前已渲染颜色的亮度并输出灰度等级。正常入口仍要求用户先点击“灰度”，因此常规路径是“现有灰度调整 → 色调分离观察”。

## 交互设计

### 灰度按钮

现有“灰度”动作保持不变：

- 选中图片后点击“灰度”，按当前实现修改选中图片的 `colorAdjust.saturation`；
- 该修改继续进入现有场景状态、撤销/重做、保存和画布导出；
- 灰度动作完成后，打开当前选中集合的色调分离控制区；
- 打开时 `posterizeLevels == null`，表示当前为普通灰度，四个级数按钮都未激活。

当用户再次点击“灰度”并按现有逻辑退出灰度时，清除色调分离临时状态并隐藏按钮组。已有“复原”按钮仍只负责现有的图片变换复原，不承担关闭色调分离的职责。

### 顶部左侧区域

普通状态保持现有布局：

```text
已选 N 张                         选择模式 [单选/多选/框选]
```

灰度观察工具打开时，左侧的数量文本被四个紧凑按钮替换：

```text
[2级] [4级] [8级] [16级]          选择模式 [单选/多选/框选]
```

- 左侧不再显示“已选 N 张”；
- 右侧“选择模式”及其控件保持原位置和行为；
- 当前锁定状态徽标继续保留在左侧按钮组之后；
- 按钮使用现有 Material 3 chrome token，保持与 sheet 其它控件一致；
- 每个按钮保持稳定的最小触控尺寸，避免数字切换造成布局跳动；
- 目标设备为横屏平板，按钮组应在 sheet 顶部单行显示，不新增第二行。

### 级数按钮行为

- 点击未激活的级数：设置该级数并立即更新当前选中图片预览；
- 点击当前已激活的级数：将级数设为 `null`，关闭色调分离，恢复普通灰度；
- 点击其它级数：直接替换当前级数；
- 级数按钮激活状态只表示当前临时预览，不表示图片已经被永久转换；
- sheet 被用户收起后，若选中状态和灰度观察上下文仍然有效，再次打开 sheet 时恢复这组按钮和当前激活状态。

### 选择变化

色调分离上下文绑定到点击灰度时的选中集合。选中集合发生变化时：

- 清除 `posterizeLevels`；
- 隐藏四个级数按钮；
- 不回滚已经保存的灰度调整；
- 新的选中集合需要重新点击“灰度”后才能进入色调分离观察。

这样可以避免用户从 A 图片切换到 B 图片后，B 图片意外继承 A 图片的临时观察效果。

## 状态模型

在共享 `PockRefUiState` 中增加以下临时字段：

```kotlin
/** True while the current grayscale selection exposes posterize controls. */
val posterizeControlsVisible: Boolean = false,

/** Null means ordinary grayscale; otherwise one of 2, 4, 8, 16. */
val posterizeLevels: Int? = null,

/** Selection captured when the grayscale observation controls were opened. */
val posterizeTargetImageIds: Set<String> = emptySet(),
```

状态不变量：

```text
posterizeControlsVisible == false
    -> posterizeLevels == null
    -> posterizeTargetImageIds.isEmpty()

posterizeControlsVisible == true
    -> posterizeTargetImageIds == state.selectedImageIds
    -> posterizeLevels == null 或 2/4/8/16
```

`posterizeLevels == null` 表示“控制区已打开但色调分离关闭”，不是控制区隐藏。这样可以区分以下两个 UI 状态：

1. 灰度已开启，控制区可见，当前是普通灰度；
2. 灰度未开启，控制区隐藏。

### Action

新增两个 UI action：

```kotlin
data class TogglePosterizeLevel(val levels: Int) : PockRefAction
data object ClearPosterizePreview : PockRefAction
```

`TogglePosterizeLevel` 只修改临时 UI 状态，不调用 `mutateScene()`，因此不会进入撤销栈、不会触发场景脏状态，也不会触发自动保存。

`ClearPosterizePreview` 用于灰度关闭、选择清空、场景切换、导入新场景和其它需要显式清理观察状态的路径。

现有 `ConvertToGrayscale` action 保持原 action 名称和场景变更路径；在灰度变更完成后，ViewModel 负责打开或清理上述临时上下文。

## 数据流

### 打开色调分离控制区

1. 用户在 `SelectionBottomSheet` 点击现有“灰度”按钮。
2. `PureRefViewModelTransform.convertToGrayscale()` 按现有逻辑修改选中图片的 `colorAdjust`。
3. 场景变更进入现有历史记录和保存通知流程。
4. ViewModel 在同一选中集合上设置：
   - `posterizeControlsVisible = true`；
   - `posterizeLevels = null`；
   - `posterizeTargetImageIds = currentSelectedIds`。
5. sheet 顶部左侧由“已选 N 张”重组为四个级数按钮。

### 修改级数

1. 用户点击 `2级 / 4级 / 8级 / 16级`。
2. ViewModel 校验级数属于允许集合。
3. 若点击值等于当前 `posterizeLevels`，写入 `null`；否则写入点击值。
4. Canvas 根据临时状态重新绘制目标图片；其它图片保持当前已有渲染行为。

### 清理

以下情况必须清理三个临时字段：

- 再次点击“灰度”并退出灰度；
- 选中集合发生变化；
- 选中被清空；
- 导入或加载新的场景；
- 关闭当前画布并回到首页；
- ViewModel 重建后没有对应的活动场景上下文。

清理操作不修改 `SceneImage.colorAdjust`，因此不会把普通灰度错误地恢复成彩色。灰度恢复彩色仍由现有“灰度”按钮逻辑负责。

## 渲染方案

### 渲染边界

新增一个独立的 posterize 渲染阶段，位于图片已有 `ColorAdjust` 效果之后、选择框和锁定边框之前。普通图片没有启用色调分离时，继续走现有 `buildColorFilter(image.colorAdjust)` 路径，避免扩大普通渲染的回归范围。

`drawSceneImage()` 通过 `posterizeLevels` 和目标图片 ID 判断是否启用该阶段；`drawImageTiles()` 和 GIF 当前帧也必须使用同一判断和同一量化规则。

### 实现策略

使用“GPU 优先、CPU 降级”的渲染器边界：

- 在支持运行时 shader 的系统版本上，使用运行时 shader 对当前 Bitmap、tile 或 GIF 当前帧进行逐像素亮度量化，保证拖动/切换级数时不修改源 Bitmap；
- 在不支持该能力的系统版本上，使用后台线程生成量化后的显示 Bitmap，并按 `imageId + sourceGeneration + levels + colorAdjust` 缓存；
- tile 路径按 tile 独立缓存，不能为了色调分离重新解码整张超大原图；
- 缓存生成期间继续显示现有灰度结果，生成完成后触发 Canvas 重绘；
- 缓存失效时只清理观察层缓存，不清理现有原图 Bitmap 和 tile 缓存。

渲染器输入至少包括：

```text
source bitmap/tile/frame
current ColorAdjust
posterize levels
crop source rect
destination rect
```

渲染器输出只影响当前帧的画布显示。图片的旋转、翻转、裁切、缩放、选择框和锁定边框继续复用现有几何路径，不在 posterize 渲染器中重复实现。

### 透明度和 GIF

- Alpha 保持源图 Alpha；
- GIF 每帧使用同一 `posterizeLevels`，切换帧时不改变级数状态；
- GIF 播放控制、帧索引和现有 `colorAdjust` 行为保持不变；
- GIF 没有可用帧或渲染器暂时无法生成观察缓存时，显示现有灰度帧，不阻塞播放。

## 持久化、撤销和导出边界

### 灰度

现有灰度逻辑不变：

- `SceneImage.colorAdjust.saturation` 继续被保存到 `.pur`；
- 灰度修改继续进入撤销/重做；
- 画布导出继续使用已保存的 `colorAdjust`，所以普通灰度会保留在导出结果中。

### 色调分离

新增的 `posterizeControlsVisible`、`posterizeLevels` 和 `posterizeTargetImageIds`：

- 不进入 `PockRefScene.toScene()`；
- 不进入 `toHistoryEntry()` 或 `SceneDiff`；
- 不参与场景 quick hash、自动保存和未保存更改判断；
- 不写入 `PurSceneCodec`、`PurFileParser` 或最近文件元数据；
- 不传入 `exportCanvas()`、选中图片原图导出和封面缩略图导出；
- 关闭应用或重新加载场景后默认关闭。

因此，用户可以用 `2级` 观察黑白结构，再导出原图或普通灰度结果，而不会意外导出色调分离后的预览图。

## 文件边界

### 修改

- `app/src/main/java/com/pockref/app/model/Models.kt`
  - 增加临时色调分离状态字段。
  - 在选择归一化或场景状态转换处维护临时状态不变量。
- `app/src/main/java/com/pockref/app/ui/PureRefActions.kt`
  - 增加切换/清理色调分离的 action。
- `app/src/main/java/com/pockref/app/ui/PureRefViewModel.kt`
  - 接入新的临时状态 reducer。
  - 在灰度操作、选择变化和场景切换边界清理或建立观察上下文。
- `app/src/main/java/com/pockref/app/ui/PureRefViewModelTransform.kt`
  - 保持现有灰度场景变更。
  - 提供建立/清理色调分离上下文的变换入口，避免把临时效果写入场景。
- `app/src/main/java/com/pockref/app/ui/PureRefApp.kt`
  - 将 sheet 左上角的选中数量区域改为条件渲染的四按钮组。
  - 将临时状态传入 Canvas。
  - 在 Bitmap、tile 和 GIF 绘制路径接入 posterize 渲染器。
- `app/src/main/java/com/pockref/app/ui/ColorMatrixHelpers.kt` 或新增专用渲染辅助文件
  - 提供可测试的亮度量化和级数校验逻辑。

### 新增

- `app/src/main/java/com/pockref/app/ui/PosterizeRenderer.kt`
  - 封装 GPU 优先、CPU 降级的显示渲染路径及缓存 key。
- `app/src/test/java/com/pockref/app/ui/PosterizeRendererTest.kt`
  - 覆盖纯量化逻辑、级数校验、Alpha 保留和缓存键区分。
- `app/src/test/java/com/pockref/app/ui/PosterizeStateTest.kt`
  - 覆盖临时状态开关、重复点击关闭、级数切换和选择变化清理。

### 不修改

- `PurSceneCodec`、`PurFileParser` 的场景格式和颜色调整字段。
- 原图导出和画布导出的输入数据契约。
- 现有 Bitmap/tile 解码策略；观察层只消费已有解码结果或按现有 tile 请求生成显示缓存。
- 现有 SelectionBottomSheet 的动作网格、锁定逻辑和选择模式行为。

## 测试计划

### 纯逻辑测试

1. 允许级数只有 `2、4、8、16`，其它值被拒绝或不产生状态变化。
2. `2级`输出只包含黑、白两个灰度值。
3. `4/8/16级`输出不会超过对应的等级数量。
4. 亮度边界按最近等级量化，`2级`的中间亮度分界稳定。
5. Alpha 通道保持不变。
6. 不同 `imageId`、源版本、`ColorAdjust` 或级数不会错误复用观察缓存。

### 状态测试

1. 灰度操作完成后，控制区可见，`posterizeLevels == null`，目标集合等于当前选择。
2. 点击 `2级` 后状态为 `2`，再次点击后恢复 `null`。
3. 从 `2级`切换到 `8级` 时直接变为 `8`，不短暂隐藏控制区。
4. 灰度关闭后控制区、级数和目标集合全部清空。
5. 选择集合变化后临时状态全部清空，但已有 `SceneImage.colorAdjust` 不变。
6. 临时状态变化不改变 `toScene()` 的结果、不增加撤销记录、不改变 dirty/save 状态。
7. 场景加载、导入和返回首页后不会残留上一个场景的色调分离状态。

### 渲染验证

- 普通 Bitmap、分块 Bitmap 和 GIF 当前帧在四个级数下都能显示对应灰度层级。
- 旋转、翻转、裁切、缩放和选择框位置与未启用色调分离时一致。
- 未启用色调分离时，现有渲染路径和性能不发生回归。
- 观察缓存生成期间 Canvas 不白屏、不阻塞手势和 GIF 播放。
- 2 级预览关闭后立即恢复普通灰度，而不是恢复彩色或保留旧缓存。

### 构建验证

运行：

```bash
./gradlew :app:testDebugUnitTest --tests "com.pockref.app.ui.PosterizeRendererTest"
./gradlew :app:testDebugUnitTest --tests "com.pockref.app.ui.PosterizeStateTest"
./gradlew :app:testDebugUnitTest
```

在目标 Galaxy Tab 横屏尺寸上进行一次手动验证：单选、多选、锁定图片、GIF、超大 tile 图片，以及场景保存后重新打开，确认色调分离只影响预览而不会进入导出或持久化。

## 验收标准

- 点击“灰度”后，sheet 左上角的“已选 N 张”被四个级数按钮替换。
- 初始四个按钮均未激活，图片显示普通灰度。
- 点击 `2级` 后显示黑白二分；点击当前 `2级` 再关闭，恢复普通灰度。
- `4级、8级、16级` 可直接切换并显示对应灰度层级。
- 多选时所有选中图片统一使用当前级数。
- 重新点击灰度关闭后按钮消失，图片恢复彩色；已有灰度保存/撤销/导出行为保持不变。
- 色调分离本身不进入场景保存、撤销、自动保存和任何导出结果。
- 现有选择、旋转、缩放、裁切、锁定、GIF 播放和大图 tile 显示不回归。
