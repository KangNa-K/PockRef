# PockRef v1.2.4

- ▲ 修复 启动页长按文件重命名后,启动页标签和文件管理器里的文件名不同步 — 重命名会同步改写磁盘上的 .pur 文件(file:// 走 File.renameTo,content:// 走 DocumentsContract.renameDocument,provider 不支持时降级为只改标签)
- ● 新增 剪贴板多张图片批量导入 — 系统剪贴板里有 N 张图(部分相册 / 浏览器的多选复制)现在会全部导入画布,按 8f 间距往下 cascade 排版;部分失败时 snackbar 提示「已导入 N 张,M 张失败」
- ▲ 修复 打开失败时的 snackbar 提示过长(原本会带出完整 URI + 字节数 + 尝试路径堆栈,看着像黑底长条)— 统一收敛为「打开文件失败:找不到文件」,完整诊断信息仍在 logcat