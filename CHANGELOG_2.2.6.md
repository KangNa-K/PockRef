# PockRef v2.2.6

- 设置的“通用”页新增检查更新，可查看新版说明、下载 GitHub Release APK 并启动系统安装。
- 更新包下载后会校验大小、SHA-256、包名、版本号和签名；失败时保留当前版本。

## 内部改进

- `versionCode` 31 -> 32，`versionName` 2.2.5 -> 2.2.6。
- 通过 `update/version.json` 发布更新信息；Release APK 使用不可变版本路径。
