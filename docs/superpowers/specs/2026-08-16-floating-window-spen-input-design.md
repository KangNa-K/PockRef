# 悬浮窗 S Pen 笔身按键输入设计

## 目标

让 PockRef 的 S Pen 笔身按键在应用自己的悬浮窗内继续触发既有选择模式：按下进入框选，松开后按当前选中数量回到单选或多选。

范围仅限笔尖或笔悬停在 PockRef 悬浮窗范围内的输入。不支持在其他应用界面、悬浮窗外或后台全局监听，因此不引入 Shizuku、无障碍服务或读取 `/dev/input` 的实现。

## 根因

当前检测器只在 `MainActivity.dispatchTouchEvent()` 和 `MainActivity.dispatchGenericMotionEvent()` 中读取 `MotionEvent.buttonState`。开启浮窗时，`FloatingWindowService` 创建可聚焦的 `TYPE_APPLICATION_OVERLAY`，随后 `MainActivity` 被移到后台；浮窗内的事件投递给其 `ComposeView`，不会经过 Activity 的两个 dispatch hook。

浮窗与 Activity 共享 ViewModel，但没有共享输入分发入口，导致 `SpenButtonDetector` 没有收到按下或松开的边沿。

## 设计

### 可复用输入桥

新增一个位于 `spen` 包的轻量输入桥。它持有：

- 一个 `SpenButtonDetector`；
- 上一帧的 stylus button bit；
- `onSpenEvent` 回调。

桥接器接收 `MotionEvent`，从 `BUTTON_STYLUS_PRIMARY`、`BUTTON_STYLUS_SECONDARY` 和 `BUTTON_TERTIARY` 计算当前 bit，并仅在 `0 -> 非 0` 与 `非 0 -> 0` 时向 detector 发送 `onDown()` / `onUp()`。`reset()` 清理边沿状态与 detector 状态。

Activity 改为把两个 dispatch hook 转交给此桥接器。S Pen action 到 ViewModel 的映射提取为可由 Activity 和浮窗共同调用的函数，保持现有 `BoxStart`、`BoxEnd` 与 `Single` 语义不变。

### 悬浮窗接入

`FloatingWindowService` 以一个轻量 `FrameLayout` 作为 WindowManager 根 View，并将 `ComposeView` 作为其唯一的满尺寸子 View。根 View 重写：

- `dispatchTouchEvent()`：覆盖笔尖接触屏幕时的 `ACTION_DOWN`、`ACTION_MOVE`、`ACTION_UP`；
- `dispatchGenericMotionEvent()`：覆盖笔悬停时的 `ACTION_HOVER_*` 等事件。

两个方法都先交给输入桥，再调用 `super` 把原始事件继续交给 `ComposeView`。因此 Compose 仍可处理画布拖拽、缩放、浮窗移动、尺寸调整和按钮点击；输入桥只观察原始事件，不消费它们。使用根 View 而非给 `ComposeView` 再装 listener，可以避免与画布现有的原始事件诊断 listener 互相覆盖。

浮窗销毁、恢复全屏或关闭时重置桥接器，防止按住笔身切换窗口后产生遗留的“已按下”状态。

### 生命周期与并发

Activity 和浮窗分别拥有输入桥及 detector，不共享状态机。浮窗显示时 Activity 已转后台，输入事件只会进入浮窗；恢复全屏时浮窗停止，事件重新只进入 Activity。这避免两个宿主同时处理同一事件。两者共享同一个 ViewModel，故选择状态、撤销历史和场景内容保持原样。

### 设备边界与诊断

浮窗 View 收到的原始 `MotionEvent.buttonState` 若包含 S Pen bit，以上设计即可工作。若三星系统在 `TYPE_APPLICATION_OVERLAY` 路径已将该字段置为 `0`，应用层不能重建丢失的硬件状态；本次不以 Shizuku 作为降级方案。

保留或补充仅用于调试的日志，记录浮窗内 stylus 事件的 action、tool type 与 buttonState，便于一次真机 `logcat` 验证区分“桥未接入”和“系统已清零”。生产交互不依赖日志。

## 测试与验收

### 自动测试

- 为抽出的输入桥添加纯 JVM 测试。
- 覆盖三种支持的 stylus bit 的按下、保持、松开，以及无关 buttonState 不触发 detector。
- 继续运行现有 `SpenButtonDetectorTest`，确保原状态机语义未变。

### 真机验收（Galaxy Tab S10+）

1. 全屏与浮窗模式下，笔尖接触画布时按住笔身均进入框选；松开均正确退出。
2. 悬停在浮窗画布上按笔身也能进入和退出框选。
3. 笔身按键不影响画布原有的拖动、缩放和浮窗移动。
4. 从按住状态恢复全屏或关闭浮窗后，下一次按键仍从干净的状态开始。
5. 若不触发，检查浮窗 log；只有 buttonState 持续为 `0` 时，记录为 OEM overlay 输入限制，而非 Compose 消费事件。

## 非目标

- 不支持其他应用或浮窗范围外的笔身按键。
- 不修改 S Pen 设置页、按键手势模式或现有选择模式规则。
- 不修改 `TYPE_APPLICATION_OVERLAY` 为 PiP 或尝试设备专有的 freeform window。
