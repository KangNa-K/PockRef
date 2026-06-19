# PockRef v1.3.1

- ● 新增 激活全局锁定(或冻结模式)后,点一下应用界面,右上角锁定按钮也会隐藏起来,再点一下会显示出来 — 之前 v1.3.0 的 spec 是"全局锁定 + 冻结模式都要开",但因为 soft mode(`globalImageLock = true`, `lockCanvasFrozen = false`)下顶栏 / FAB / minimap / 选区 sheet / mode chip 这些 chrome 也都被 `!isLockEngaged` gate 收起,视觉上跟 hard mode 一样只剩"画布 + 锁定按钮",所以用户反馈 soft mode 也应该有这个 affordance。具体实现:在 outer `BoxWithConstraints` 的 `CanvasPanel` 之后、chrome 元素之前插入一个全屏透明 tap-catch overlay(`matchParentSize() + 自定义 pointerInput`),gate 改为 `isLockEngaged && images.isNotEmpty()`(任一锁定 flag 为 true 即激活,不只是 hard mode),每次 tap dispatch `PockRefAction.ToggleLockButtonVisibility`。z-order 上锁定按钮仍然在最顶层,所以点击锁定按钮本身仍走原有解锁流程(`ToggleLockCanvasFrozen` / `ToggleGlobalImageLock`),不会被 overlay 拦截。新增 `PockRefUiState.lockButtonHidden: Boolean` view-local 字段(不持久化)+ `ToggleLockButtonVisibility` action + reducer 中两个 reset 点(`ToggleGlobalImageLock` 关闭时 + `ToggleLockCanvasFrozen` 退出 hard mode 时),保证再次进入 lock mode 时按钮默认显示。`PockRefActionsTest.kt` 的 variant count 从 152 → 158(本次 +1 `ToggleLockButtonVisibility` + 此前已 drift 5 个未更新)
- ▲ 修复 启用全局锁定后画布无法移动和缩放 — 第一版 overlay 用了 `Modifier.clickable(indication = null)`,而 `clickable` 内部用 `detectTapGestures` + `waitForUpOrCancellation`,后者会在任意 sibling pointerInput(画布的 pan / pinch handler)消费 move 事件时返回 null(取消 tap 检测)。soft mode 下画布的 pointerInput 每个 move 都调 `change.consume()`,所以 overlay 的 tap 检测反复 cancel,导致 tap + drag 交替序列不可靠。改成手写 `pointerInput(detectTapGestures 不需要)`:await down 不消费、track movement 不消费、up 时若 movement < slop 且 duration < longPress 才 fire toggle 并消费 up;drag 则完全不消费,画布的 pan / pinch 不受影响

## 🛠 内部改进

- 无新增依赖、无破坏性 API 变更、纯增量功能
- `./gradlew :app:testDebugUnitTest` 与 `:app:compileDebugKotlin` 全绿
- `versionCode` 11 → 12,`versionName` 1.3.0 → 1.3.1
