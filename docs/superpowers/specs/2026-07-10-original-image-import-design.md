# 原图导入设计

## 目标

从相册、文件、剪贴板和拍照导入图片时，`SceneImage.imageBytes` 必须保存内容提供方返回的原始字节，不缩放、不重新编码、不改变图片格式。

## 方案

- 导入阶段只读取原始字节，并使用 `BitmapFactory.Options.inJustDecodeBounds` 获取宽高。
- `SceneImage.mimeType` 优先使用 `ContentResolver` 返回值；缺失时根据图片字节解码结果或 URI 后缀补全，但不修改字节。
- 相册、文件和拍照继续共用 `ImportImages`；剪贴板路径使用同一原图解析逻辑，避免行为分叉。
- 初始画布显示尺寸仍限制为最多 720 场景单位宽，仅影响布局，不改变原始像素。
- 画布渲染继续使用现有 `SceneImageDecoder` 分级解码，以控制内存；需要原图的保存、导出和聚焦解码仍可访问完整原始字节。

## 不包含

- 不修改用户主动触发的图片优化功能。
- 不改变场景文件保存格式。
- 不改变相机拍摄质量设置；应用只保证接收并保留相机写入 `FileProvider` URI 的完整文件。
- 不保留内容提供方未写入图片文件的外部数据库元数据。

## 验收标准

1. 导入超过 2048px 长边的图片后，`intrinsicWidth` / `intrinsicHeight` 保持原始尺寸。
2. 导入 JPEG、PNG、WebP、GIF 后，`imageBytes` 与输入字节逐字节一致，MIME 不因导入而转换。
3. 相册/文件/拍照共用路径和剪贴板路径均满足上述条件。
4. 无效或无法解码的图片仍被拒绝，不创建 0×0 的 `SceneImage`。
