# 裁剪场景保存改用 PRFA 新格式

## 背景

当前 PockRef 的手动保存和自动保存会把场景写成 PureRef 1.10 兼容的旧格式。旧格式保存图片像素和基础变换，但没有保存 PockRef 的非破坏性 `cropRect`，因此用户裁剪图片后保存 `.pur`，重新打开时会重新显示原始完整图片。

项目已经有 `PRFA` 自定义新格式写入器和读取器。新格式会保存 `SceneImage` 的 `cropRect`、`displaySize`、锁定状态、高清显示偏好以及其它 PockRef 场景字段。

## 目标与非目标

### 目标

- 手动保存 `.pur` 时使用 `PurSceneCodec.write()` 写入 `PRFA` 新格式。
- 自动保存到当前场景 URI 时也使用 `PurSceneCodec.write()`，保证自动保存不会丢失裁剪状态。
- 保留读取端对旧 PureRef `.pur` 格式的兼容，用户已有文件仍可打开。
- 裁剪后的图片经过保存、重新读取后，保持 `cropRect` 和裁剪后的 `displaySize`。
- 增加新格式裁剪往返回归测试，并验证输出文件以 `PRFA` 标识。

### 非目标

- 不修改旧格式解析器或旧文件迁移逻辑。
- 不把裁剪像素永久重编码成新的图片文件；裁剪仍是非破坏性场景属性。
- 不保证桌面 PureRef 直接打开 PockRef `PRFA` 文件。旧格式读取兼容保留，但保存格式切换后，桌面互操作不再是保存路径的目标。
- 不处理与本问题无关的工作区未提交改动。

## 方案

### 编解码边界

`PurSceneCodec.write(scene, output)` 作为 PockRef 当前保存格式的唯一新写入口。该入口写入 `PRFA` magic 和当前自定义格式版本，并逐图保存：

- 原始图片数据和 MIME 类型
- 图片尺寸、位置、显示尺寸、旋转、缩放、镜像和层级
- `locked`
- 可选的 `cropRect` 四个归一化边界
- `showsOriginalResolution`
- 场景标记和场景设置

`PurSceneCodec.readFromFile()` 保持现有格式探测顺序：先识别 `PRFA`，否则继续走桌面/旧版 PureRef 解析路径。这样新文件走完整新格式，历史文件继续可读。

### 手动保存数据流

`PureRefViewModelScene.writeSceneToFile()` 在临时文件中调用 `codec.write(scene, output)`，写完后仍通过现有 URI 输出流复制到目标文件。临时文件和 URI 写入的原子性、截断模式、错误处理保持不变。

### 自动保存数据流

`AutoSaveManager` 的备份文件本来已经调用 `codec.write()`；当前场景 URI 的自动保存改为同样调用 `codec.write(scene, output)`。这样备份文件和原场景文件使用同一套编码逻辑，裁剪状态不会因保存来源不同而分叉。

## 裁剪行为

`commitCrop()` 继续把裁剪区域写入 `SceneImage.cropRect`，并根据裁剪后像素宽高调整 `SceneImage.displaySize`。新格式读取时恢复这两个字段，现有渲染和瓦片规划逻辑会继续根据 `cropRect` 显示裁剪后的区域。

## 错误处理与兼容性

- 新格式写入失败时沿用当前保存错误处理，不修改当前场景状态。
- 写入目标 URI 失败时保留临时文件清理和错误提示行为。
- 旧 PureRef 文件仍由读取端解析；旧文件没有 `cropRect` 时保持无裁剪默认值。
- `PRFA` 文件读取失败时不回退到旧格式解析，避免把自定义格式误判为旧格式。

## 测试计划

1. 在 `PurSceneCodecCustomFormatTest` 增加一个 `PRFA` 新格式裁剪往返测试：构造带 `cropRect` 和非默认 `displaySize` 的 `SceneImage`，写入文件，再用 `readFromFile()` 读取并断言字段一致。
2. 断言新格式输出文件头为 `PRFA`，确保保存路径没有意外继续使用旧格式。
3. 运行裁剪/编解码相关 JVM 测试，以及完整 `testDebugUnitTest`；若完整测试受现有工作区改动影响，单独报告失败原因。
