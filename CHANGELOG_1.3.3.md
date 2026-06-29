# PockRef v1.3.3

- ▲ 修复 缩小画布到一定比例后无法继续缩小,画布周围铺开的参考图被截掉 — 之前 `canvasScale` 在 6 处写路径上(`zoomCanvas` / `setCanvasZoom` / `SetCanvasScaleAndOffset` reducer + 3 处 minimap slider UI 预 clamp)都被字面量 `coerceIn(0.1f, 10f)` 锁住,10% 是最大缩放比。当用户在画布周围铺了 20+ 张参考图时,bbox 远超视口,实际需要缩到 3-5% 才能看全,0.1 这个下限直接挡住了。改成 `PureRefViewModel.kt` 顶部新增的两个顶层 `internal const val` —— `MIN_CANVAS_SCALE = 0.02f` / `MAX_CANVAS_SCALE = 10f`,6 处全部替换。0.02 远高于 sub-1px 渲染地板(~0.005),缩略图不会出现 GPU 抖动;手动缩放 + 自动 fit 共享同一个下限,以后改一处就够。`./gradlew :app:testDebugUnitTest` 336 tests 全绿
- ▲ 修复 双击空白处"自动 fit 到屏幕"只显示画布和周围一小圈 — 走的是另一条代码路径 `PockRefViewModelBatch.fitToScreen`(被 `PockRefAction.FitToScreen` 触发),它**额外**有一道独立的 downscale floor = `0.15f`(字面量),比手动缩放的 0.1 还高。同样的 bbox-vs-viewport 量级问题被这道更高的 cap 二次截断,viewport 内只能看到中心一撮图。`0.15f` → `MIN_CANVAS_SCALE`(同 58b9782 的 0.02f 常量),现在 fit 路径和 pinch 路径共用同一缩放下限,几何 fit scale 可以真正按 bbox 算,画布周围所有图都能塞进视口。"content 比视口小时保持 1:1 不放大"的分支(`fitScale >= 1f → 1f`)没动,小场景仍然是 1× 自然显示,只有大场景获得了更宽的 shrink 余量

## 🛠 内部改进

- 无新增依赖、无破坏性 API 变更、纯 bug fix
- 新增两个 `internal const val` 顶层常量在 `PureRefViewModel.kt:55-66`(`MIN_CANVAS_SCALE` / `MAX_CANVAS_SCALE`),kdoc 里写清楚为什么 0.02 / 10
- `0.1f` / `0.15f` / `10f` 三处字面量全部清除,以后调画布缩放范围只改 1 个常量
- `./gradlew :app:testDebugUnitTest` 与 `:app:compileDebugKotlin` 全绿(336 tests, 0 failures, 2 ignored)
- `versionCode` 13 → 14,`versionName` 1.3.2 → 1.3.3
