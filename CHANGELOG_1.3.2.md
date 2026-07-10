# PockRef v1.3.2

- ▲ 修复 新建的 `.pur` 场景文件无法保存到所选文件夹(SM-X810 / Samsung One UI 6 复现) — Save 流程走的是两段式 SAF:[OpenDocumentTree] 让用户选目录 → 后台把返回的 tree URI 直接喂给 [DocumentsContract.createDocument]。问题是 `createDocument` 要求的 parent 是 **document URI**(路径末段是 `/document/<id>`),而 [OpenDocumentTree] 返回的是 **tree URI**(路径末段是 `/tree/<id>`),两者不互通,DocumentsProvider 直接抛 `java.lang.IllegalArgumentException: Invalid URI: content://com.android.externalstorage.documents/tree/primary%3APictures` 并把异常吞回 `null`,UI 没有任何提示,文件没写出来。修复在 `app/src/main/java/com/pockref/app/ui/PureRefApp.kt` 的 `saveSceneTreeLauncher` 回调里加了 tree → document 转换,跟 batch-export 那条路径(`exportBatchToTree` in `PureRefViewModelScene.kt`)用同一套 API:[DocumentsContract.getTreeDocumentId] 抽 id → [DocumentsContract.buildDocumentUriUsingTree] 拼 document URI → 再喂给 [createDocument]。修完之后 Pictures / Downloads / 自定义目录都能正常写,`PockRefSave` 日志里不再出现 `Invalid URI`,PockRef 主屏能直接打开刚保存的 `.pur` 文件

## 🛠 内部改进

- 无新增依赖、无破坏性 API 变更
- 修复合并了 batch-export 已经验证过的 tree → document 模式,代码量净 +6 行
- `./gradlew :app:testDebugUnitTest` 与 `:app:compileDebugKotlin` 全绿
- `versionCode` 12 → 13,`versionName` 1.3.1 → 1.3.2
