# iOS 标记移植续作验证（2026-10-25）

状态：基础共享编辑与部分高级交互已落地，全面移植仍未完成。

## 隔离与保留

- 用户批准保留现有 UI，复制未提交状态到独立目录继续。
- 独立目录：`/Volumes/KNJ/.codex-work/pockref-markup-20261025-212711`；`snapshot.json` 记录基线 SHA-256，`baseline/` 保留源文件。
- 未重置工作区、未提交、未发布、未安装物理设备；只使用独立 Android 模拟器 emulator-5556。
- 仅回写本轮相对基线发生变化的文件，回写前校验原目录没有同文件并发修改。回写前备份位于 `merge-backup/`，清单为 `merge-record.json`。

## 本轮实现

- 多对象选区拖拽缩放/旋转手柄；从手势初始状态计算，避免累计误差，提交一次撤销，取消完整还原。
- 笔输入锁定原 pointer，笔绘制期间额外触点不触发双指导航；不宣称完整真机防误触能力。
- 新增真实 Compose ACTION_CANCEL 回归，复现取消仍提交笔画的问题后修正：已消费的取消 release 不更新/提交，协程退出也取消事务。
- 局部擦除 Android -> iOS -> Android 真实 PUR 回环验证分片 ID、擦除间隙与对象完整性。
- 保留现有玻璃工具栏与原画布布局；新增混合对象/选区截图检查。

## 实际验证

- Android JVM：563 项，560 通过，3 跳过，0 失败/错误；debug 与 androidTest APK 构建成功。
- 独立 Android 35 模拟器：runner 25 项，24 通过、1 可选大文件 fixture 跳过，0 失败。共享文件互通 8、会话 9、Compose UI 5；另有解析器 3（含 1 跳过）。
- iOS 18 独立模拟器：146 项，141 通过、5 跳过，0 失败。AnnotationDocument 9、会话 3、PencilKit adapter 61、PUR parser 73（含 5 跳过）。
- iOS 跳过涉及可选外部/大文件 fixture 与不可用的新系统 PaperKit 路径，不能算这些能力已验收。
- 检查 `markup-selection-review.png`：混合形状/文字/笔画及旋转/缩放手柄正常出现，现有 chrome 保留。工具栏横向滚动；文本精确边界/字体度量仍需继续对齐。
- 日志：`android-cancel-fix.log`、`android-instrumentation-final.log`、`ios-tests.log`；Swift xcresult 在 `ios-derived/Logs/Test/`。

## 尚未完成

PaperKit 26 全对象转换、AndroidX Ink/压感倾斜、更多形状与填充、完整锁定界面、可配置 S Pen 临时橡皮/套索、字体/文本框精确对齐，以及实际 S Pen、锁屏、进程死亡、保存故障与性能验收仍待完成。
当前使用 schema 1 扩展标志，不是原方案建议的 schema 2 revision/指纹机制；不支持的原生/未知内容以保护优先，不静默删除。
