# PockRef 使用说明

## 1. 项目简介

PockRef 是一款专为 Android 平板（搭配 S Pen）设计的**参考图板**应用，灵感源自桌面端 PureRef。它让你在创作时可以把参考图片自由摆放在画布上，随时拖拽、缩放、旋转、对齐，打造属于自己的视觉灵感板。

**核心特色：**

- 兼容 PureRef 桌面端的 `.pur` 场景文件格式
- S Pen 侧键一键框选，无需切换工具
- 智能边缘吸附与角度吸附
- 多选批量操作（对齐、翻转、灰度、导出等）
- 自动排版一键整理
- 自动保存，不怕意外退出

---

## 2. 界面布局

PockRef 的界面由以下区域组成：

```
┌─────────────────────────────────────────────┐
│  [顶栏] 场景名称 · 最近文件 · 撤销  · 重做 · 关闭│
├─────────────────────────────────────────────┤
│                                             │
│                                             │
│              画布（主工作区）                  │
│                                             │
│    [选图浮动工具栏]                           │
│                                             │
├──────┬──────────────────────────┬────────────┤
│缩略图│                          │  自动排版   │
│导航  │                          │  FAB       │
└──────┴──────────────────────────┴────────────┘
```

| 区域 | 位置 | 说明 |
|------|------|------|
| 顶栏 | 屏幕顶部 | 场景名称、最近文件、撤销、重做、关闭 |
| 画布 | 屏幕中央 | 放置和操作参考图的主工作区 |
| 选图浮动工具栏 | 选中图片附近 | 根据选中数量自动显示，提供编辑操作 |
| 缩略图导航 | 左下角 | 全局缩略图，移动可快速跳转 |
| 自动排版 FAB | 右下角 | 一键自动整理图片布局 |

---

## 3. 核心功能介绍

### 3.1 导入图片

1. 点击右下角 FAB（自动排版按钮）**长按**，展开操作面板
2. 点击 **导入** 按钮（<img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/folder_open.svg" width="18" height="18" valign="middle"/> 文件打开图标）
3. 选择 **导入图片**（<img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/image.svg" width="18" height="18" valign="middle"/>）或 **打开文件**（<img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/file_open.svg" width="18" height="18" valign="middle"/>）：
   - **导入图片**：从相册/文件中选择一张或多张图片
   - **打开文件**：打开 `.pur` 场景文件

> 也可以通过顶栏的最近文件菜单快速打开之前编辑过的场景。

### 3.2 画布操作

| 操作 | 手势 | 说明 |
|------|------|------|
| 平移画布 | 单指在空白处拖动 | 移动整个画布视口 |
| 缩放画布 | 双指捏合 | 放大/缩小画布 |
| 旋转画布 | 双指旋转 | 旋转画布视角（吸附 0°/45°/90°/180°/270°） |
| 竖直滑动缩放 | 双指竖直上下滑 | 向上滑放大，向下滑缩小 |
| 适应窗口 | 双击空白区域 | 自动调整画布使所有图片可见 |
| 撤销 | 双指点击 | 撤销上一步操作 |
| 重做 | 三指点击 | 重做已撤销的操作 |

> 以上手势均可在 **设置 → 手势** 中单独开关。

### 3.3 图片操作

#### 选中图片

| 操作 | 说明 |
|------|------|
| 单击图片 | 选中该图片（单选模式） |
| 单击另一张图 | 切换选中到新图片 |
| 开启多选模式后单击 | 累积选中多张图片 |
| 框选 | 拖拽矩形区域，区域内图片全部选中 |
| 点击空白处 | 取消所有选中 |

#### 移动与变换

| 操作 | 手势 | 说明 |
|------|------|------|
| 移动图片 | 单指拖动选中的图片 | 移动图片位置 |
| 缩放图片 | 双指捏合选中的图片 | 调整图片大小 |
| 旋转图片 | 双指旋转选中的图片 | 旋转图片角度 |

#### 边缘吸附

当 **边缘吸附** 开启时（默认开启），拖动图片会自动对齐到其他图片的边缘或中心线，松手后有短暂的高亮反馈。

#### 角度吸附

当 **旋转角度吸附** 开启时（默认开启），旋转图片时会在接近 0°/45°/90°/135°/180°/225°/270°/315° 时自动吸附，停顿 200ms 后锁定。

### 3.4 选图浮动工具栏

选中图片后，图片附近会自动弹出浮动工具栏。工具栏根据选中数量和锁定状态显示不同按钮：

#### 单选模式（选中 1 张图片）

**折叠状态（5 个按钮）：**

| 图标 | 功能 | 说明 |
|------|------|------|
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/check_box_outline_blank.svg" width="18" height="18" valign="middle"/> / <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/check_box.svg" width="18" height="18" valign="middle"/> | 多选模式 | 开启/关闭多选图片模式 |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/flip.svg" width="18" height="18" valign="middle"/> | 水平反转 | 镜像翻转图片 |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/crop.svg" width="18" height="18" valign="middle"/> | 裁剪 | 进入裁剪模式 |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/layers.svg" width="18" height="18" valign="middle"/> | 灰度 | 切换灰度显示 |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/delete.svg" width="18" height="18" valign="middle"/> | 删除 | 删除选中图片 |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/expand_more.svg" width="18" height="18" valign="middle"/> | 展开 | 展开更多操作 |

**展开状态（11 个按钮）：**

| 图标 | 功能 | 说明 |
|------|------|------|
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/check_box_outline_blank.svg" width="18" height="18" valign="middle"/> / <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/check_box.svg" width="18" height="18" valign="middle"/> | 多选模式 | 同上 |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/flip.svg" width="18" height="18" valign="middle"/> | 水平反转 | 同上 |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/crop.svg" width="18" height="18" valign="middle"/> | 裁剪 | 同上 |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/layers.svg" width="18" height="18" valign="middle"/> | 灰度 | 同上 |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/lock.svg" width="18" height="18" valign="middle"/> | 锁定 | 锁定/解锁图片（蓝色=已锁定） |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/rotate_left.svg" width="18" height="18" valign="middle"/> | 向左旋转 90° | 逆时针旋转 90° |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/rotate_right.svg" width="18" height="18" valign="middle"/> | 向右旋转 90° | 顺时针旋转 90° |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/restart_alt.svg" width="18" height="18" valign="middle"/> | 复原图片 | 重置所有变换到初始状态 |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/content_copy.svg" width="18" height="18" valign="middle"/> | 复制 | 复制选中图片 |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/save.svg" width="18" height="18" valign="middle"/> | 导出图片 | 导出为 PNG/JPG |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/delete.svg" width="18" height="18" valign="middle"/> | 删除 | 同上 |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/expand_less.svg" width="18" height="18" valign="middle"/> | 收起 | 收起工具栏 |

#### 多选模式（选中 2 张及以上）

**折叠状态（5 个按钮）：**

| 图标 | 功能 | 说明 |
|------|------|------|
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/flip.svg" width="18" height="18" valign="middle"/> | 水平反转 | 批量水平翻转 |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/layers.svg" width="18" height="18" valign="middle"/> | 灰度 | 批量切换灰度 |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/lock.svg" width="18" height="18" valign="middle"/> | 锁定 | 批量锁定/解锁 |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/delete.svg" width="18" height="18" valign="middle"/> | 删除 | 批量删除 |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/expand_more.svg" width="18" height="18" valign="middle"/> | 展开 | 展开更多操作 |

**展开状态（12 个按钮）：**

| 图标 | 功能 | 说明 |
|------|------|------|
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/flip.svg" width="18" height="18" valign="middle"/> | 水平反转 | 同上 |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/layers.svg" width="18" height="18" valign="middle"/> | 灰度 | 同上 |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/lock.svg" width="18" height="18" valign="middle"/> | 锁定 | 同上 |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/restart_alt.svg" width="18" height="18" valign="middle"/> | 复原 | 批量重置变换 |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/content_copy.svg" width="18" height="18" valign="middle"/> | 复制 | 批量复制 |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/vertical_align_top.svg" width="18" height="18" valign="middle"/> | 向上对齐 | 所有选中图片顶部对齐 |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/vertical_align_bottom.svg" width="18" height="18" valign="middle"/> | 向下对齐 | 所有选中图片底部对齐 |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/align_horizontal_left.svg" width="18" height="18" valign="middle"/> | 向左对齐 | 所有选中图片左侧对齐 |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/align_horizontal_right.svg" width="18" height="18" valign="middle"/> | 向右对齐 | 所有选中图片右侧对齐 |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/save.svg" width="18" height="18" valign="middle"/> | 导出选中图片 | 批量导出为 PNG/JPG |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/delete.svg" width="18" height="18" valign="middle"/> | 删除 | 同上 |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/expand_less.svg" width="18" height="18" valign="middle"/> | 收起 | 同上 |

#### 锁定图片模式

当选中的单张图片处于锁定状态时，工具栏切换为精简 4 按钮面板：

| 图标 | 功能 | 说明 |
|------|------|------|
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/flip.svg" width="18" height="18" valign="middle"/> | 水平反转 | 锁定图片仍可翻转 |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/layers.svg" width="18" height="18" valign="middle"/> | 灰度 | 切换灰度 |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/lock.svg" width="18" height="18" valign="middle"/> | 解锁 | 点击解锁图片 |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/delete.svg" width="18" height="18" valign="middle"/> | 删除 | 删除图片 |

> 锁定的图片会显示红色双层边框，无法拖动/缩放/旋转，但仍可点击选中。

### 3.5 裁剪图片

1. 选中一张图片，在浮动工具栏点击 <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/crop.svg" width="18" height="18" valign="middle"/> **裁剪** 按钮
2. 拖动裁剪框的 8 个控制点调整裁剪区域
3. 可选择裁剪比例：自由、1:1、4:3、3:2、16:9、9:16
4. 点击 **确认** 完成裁剪，或点击 **取消** 退出

### 3.6 自动排版

点击右下角的 **自动排版 FAB**（<img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/auto_fix_high.svg" width="18" height="18" valign="middle"/>），一键将所有图片整齐排列。图片之间保持 8 个场景单位的间距。

### 3.7 缩略图导航

左下角的缩略图显示整个场景的全局预览：

| 操作 | 说明 |
|------|------|
| 点击缩略图中的位置 | 画布视口跳转到该位置 |
| 长按拖动缩略图 | 移动缩略图面板位置 |
| 双指捏合缩略图 | 调整缩略图缩放比例（0.5x ~ 2.0x） |
| 将缩略图拖到屏幕边缘 | 折叠为边缘图标 |
| 点击折叠后的边缘图标 | 重新展开缩略图 |

> 缩略图在 3 秒无操作后自动折叠为边缘图标。

### 3.8 场景管理

#### 保存场景

- 点击右下角的 <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/save.svg" width="18" height="18" valign="middle"/> **保存** 按钮，将当前场景保存为 `.pur` 文件
- 首次保存需要选择保存位置和文件名
- 之后再次保存会直接覆盖

#### 重命名场景

- **长按** 顶栏的场景名称，进入重命名模式
- 输入新名称后按回车确认

#### 最近文件

- 点击顶栏的场景名称，展开最近文件列表
- 点击列表中的条目可快速打开对应场景

#### 关闭场景

- 在最近文件菜单中选择关闭当前场景

### 3.9 导出

| 导出类型 | 操作 | 说明 |
|----------|------|------|
| 导出选中图片 | 浮动工具栏 → <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/save.svg" width="18" height="18" valign="middle"/> 导出图片 | 选择 PNG 或 JPG 格式，保存单张/多张图片 |
| 导出画布 | 右下角面板 → <<img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/image.svg" width="18" height="18" valign="middle"/>> 导出画布 | 将整个画布导出为 PNG（透明背景） |

---

## 4. 图标对照表


### 右下角面板图标

| 图标 | 名称 | 功能 |
|------|------|------|
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/auto_fix_high.svg" width="18" height="18" valign="middle"/> | 自动排版 FAB | 点击：一键排版；长按：展开/收起面板 |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/photo_size_select_large.svg" width="18" height="18" valign="middle"/> | 框选模式 | 开启/关闭框选（开启时图标变蓝） |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/settings.svg" width="18" height="18" valign="middle"/> | 设置 | 打开设置面板 |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/folder_open.svg" width="18" height="18" valign="middle"/> | 导入 | 打开导入菜单（导入图片/打开文件） |
| <<img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/image.svg" width="18" height="18" valign="middle"/>> | 导出画布 | 将整个画布导出为 PNG |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/save.svg" width="18" height="18" valign="middle"/> | 保存场景 | 保存当前场景 |

### 浮动工具栏图标

| 图标 | 名称 | 功能 |
|------|------|------|
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/check_box_outline_blank.svg" width="18" height="18" valign="middle"/> / <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/check_box.svg" width="18" height="18" valign="middle"/> | 多选模式 | 切换单选/多选累积模式 |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/flip.svg" width="18" height="18" valign="middle"/> | 水平反转 | 水平镜像翻转 |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/crop.svg" width="18" height="18" valign="middle"/> | 裁剪 | 进入裁剪模式 |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/layers.svg" width="18" height="18" valign="middle"/> | 灰度 | 切换灰度显示（激活时变蓝） |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/lock.svg" width="18" height="18" valign="middle"/> | 锁定 | 锁定/解锁图片（蓝色=已锁定） |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/rotate_left.svg" width="18" height="18" valign="middle"/> | 向左旋转 | 逆时针旋转 90° |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/rotate_right.svg" width="18" height="18" valign="middle"/> | 向右旋转 | 顺时针旋转 90° |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/restart_alt.svg" width="18" height="18" valign="middle"/> | 复原 | 重置所有变换 |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/content_copy.svg" width="18" height="18" valign="middle"/> | 复制 | 复制选中图片 |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/vertical_align_top.svg" width="18" height="18" valign="middle"/> | 向上对齐 | 顶部对齐（多选） |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/vertical_align_bottom.svg" width="18" height="18" valign="middle"/> | 向下对齐 | 底部对齐（多选） |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/align_horizontal_left.svg" width="18" height="18" valign="middle"/> | 向左对齐 | 左侧对齐（多选） |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/align_horizontal_right.svg" width="18" height="18" valign="middle"/> | 向右对齐 | 右侧对齐（多选） |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/save.svg" width="18" height="18" valign="middle"/> | 导出图片 | 导出选中图片 |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/delete.svg" width="18" height="18" valign="middle"/> | 删除 | 删除选中图片 |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/expand_more.svg" width="18" height="18" valign="middle"/> | 展开 | 展开更多按钮 |
| <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/expand_less.svg" width="18" height="18" valign="middle"/> | 收起 | 收起工具栏 |

---

## 5. 设置面板

点击顶栏 <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/settings.svg" width="18" height="18" valign="middle"/> 或右下角面板中的 <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/settings.svg" width="18" height="18" valign="middle"/> 打开设置面板。设置分为三个标签页：

### 5.1 通用 <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/tune.svg" width="18" height="18" valign="middle"/>

| 设置项 | 默认值 | 说明 |
|--------|--------|------|
| 边缘吸附 | 开启 | 拖动图片时自动对齐到其他图片的边缘或中心线 |
| 旋转角度吸附 | 开启 | 旋转时自动吸附到 0°/45°/90° 等角度 |
| 自动保存 | 开启 | 定时将画布快照写入磁盘 |
| 自动保存间隔 | 5 分钟 | 可选 1~60 分钟（1/5/10/15/20/25/30/35/40/45/50/55/60） |
| S Pen 按钮模式 | 按住触发 | 按住触发（按住=框选，松开=退出）或 切换模式（按一次开，再按一次关） |

### 5.2 外观 <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/palette.svg" width="18" height="18" valign="middle"/>

| 设置项 | 说明 |
|--------|------|
| 主题 | 跟随系统 / 浅色 / 深色 |
| 背景颜色 | 7 种预设色 + HSV 自定义色轮 |

**预设背景色：**

| 名称 | 颜色 |
|------|------|
| 深灰 | #111111 |
| 黑色 | #000000 |
| 白色 | #FFFFFF |
| 浅灰 | #EEEEEE |
| 画布 | #E8DCC4 |
| 薄荷 | #B0E0D0 |
| 天空 | #B0D8E8 |

选择 **自定义** 后会出现 HSV 色轮，可自由选择任意颜色：

- **外圈**：拖动选择色相（Hue）
- **内三角**：拖动选择饱和度（S）和明度（V）
- **预览色块**：左侧为当前预览色，右侧为调整前的颜色
- 点击 **确定** 应用，点击 **取消** 放弃

### 5.3 手势 <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/swipe_right.svg" width="18" height="18" valign="middle"/>

7 个手势开关，可独立启用/禁用：

| 手势 | 默认 | 功能 |
|------|------|------|
| 单指拖动 | 开启 | 拖动选中的图片 |
| 双指捏合缩放 | 开启 | 双指捏合调整缩放 |
| 双指旋转 | 开启 | 双指旋转（吸附 0°/90°/180°/270°） |
| 双指竖直滑动缩放 | 开启 | 双指竖直滑动缩放画布 |
| 双指点击（撤销） | 开启 | 双指同时点击撤销上一步 |
| 三指点击（重做） | 开启 | 三指同时点击重做 |
| 双击空白（适应窗口） | 开启 | 双击空白区域自动适应窗口 |

---

## 6. S Pen 使用

PockRef 对 Samsung S Pen 提供了深度支持：

### 框选模式

按住 S Pen 侧键 + 触屏即可进入框选模式：

1. **按住侧键** → 屏幕触笔 → 进入框选模式
2. **拖动** → 绘制矩形选区
3. **松开侧键** → 退出框选模式，选区内的图片被选中

### S Pen 按钮模式

在 **设置 → 通用 → S Pen 按钮** 中选择：

| 模式 | 操作方式 |
|------|----------|
| 按住触发 | 按住侧键=框选，松开=退出（默认） |
| 切换模式 | 按一次=进入框选，再按一次=退出 |

> S Pen 功能不需要任何额外权限，直接使用系统标准输入事件。

---

## 7. 快速上手教程

### 场景：创建一个角色设计参考板

1. **导入参考图**
   - 长按右下角 <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/auto_fix_high.svg" width="18" height="18" valign="middle"/> FAB → 展开面板
   - 点击 <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/folder_open.svg" width="18" height="18" valign="middle"/> 导入 → 选择 **导入图片**
   - 从相册中选择 3~5 张角色参考图

2. **排列图片**
   - 点击 <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/auto_fix_high.svg" width="18" height="18" valign="middle"/> FAB → 自动排版，图片整齐排列
   - 或手动拖动每张图片到想要的位置

3. **调整大小和角度**
   - 单击选中一张图片
   - 双指捏合缩放到合适大小
   - 双指旋转调整角度（自动吸附到水平/垂直）

4. **翻转和灰度**
   - 选中图片 → 浮动工具栏 → <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/flip.svg" width="18" height="18" valign="middle"/> 水平反转
   - 选中图片 → 浮动工具栏 → <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/layers.svg" width="18" height="18" valign="middle"/> 灰度（查看明暗关系）

5. **裁剪细节**
   - 选中图片 → 浮动工具栏 → <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/crop.svg" width="18" height="18" valign="middle"/> 裁剪
   - 拖动裁剪框聚焦关键区域 → 确认

6. **锁定参考图**
   - 选中图片 → 浮动工具栏 → <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/lock.svg" width="18" height="18" valign="middle"/> 锁定
   - 锁定后图片不会被误操作移动

7. **保存场景**
   - 点击顶栏 <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/save.svg" width="18" height="18" valign="middle"/> 保存
   - 选择保存位置，命名为"角色设计参考"

8. **下次继续编辑**
   - 打开应用 → 点击顶栏标题 → 最近文件中选择

---

## 8. 常见问题及解答

### Q: 为什么我拖动图片时图片不动？
**A:** 请检查：
1. 图片是否被锁定（锁定图片有红色边框）→ 选中后点击 <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/lock.svg" width="18" height="18" valign="middle"/> 解锁
2. "单指拖动"手势是否被关闭 → 设置 → 手势 → 开启"单指拖动"

### Q: 工具栏不见了怎么办？
**A:** 工具栏会在 5 秒无操作后自动隐藏。重新点击任意图片即可再次显示。

### Q: 顶栏自动收起了怎么办？
**A:** 顶栏在 3 秒无操作后自动折叠。点击顶栏区域即可重新展开。

### Q: 如何选择多张图片？
**A:** 有三种方式：
1. 选中一张图片后，在浮动工具栏点击 <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/check_box.svg" width="18" height="18" valign="middle"/> 开启多选模式，再点击其他图片
2. 使用 S Pen 侧键框选
3. 右下角面板 → <img src="https://cdn.jsdelivr.net/npm/@material-design-icons/svg/round/photo_size_select_large.svg" width="18" height="18" valign="middle"/> 框选模式 → 在画布上拖出矩形

### Q: 如何取消选中？
**A:** 点击画布空白区域即可取消所有选中。

### Q: 自动保存的文件在哪里？
**A:** 自动保存的备份文件存储在应用内部存储的 `auto_save` 目录中，文件名格式为 `场景名.auto_backup.pur`。备份保留 7 天。

### Q: 支持哪些文件格式？
**A:**
- **导入**：支持常见图片格式（PNG、JPG 等）和 PureRef `.pur` 场景文件
- **导出**：支持 PNG（无损，支持透明）和 JPG（有损，体积更小）

### Q: 画布可以无限大吗？
**A:** 画布没有固定边界，你可以自由平移到任意位置放置图片。

### Q: 如何恢复误删的图片？
**A:** 双指点击（撤销）可以恢复最近删除的图片。如果已经保存了场景，可以从自动保存备份中恢复。


### Q: S Pen 按键没反应？
**A:** 请确认：
1. 使用的是 Samsung S Pen（第三方触控笔可能不支持侧键）
2. 在设置 → 通用中确认 S Pen 按钮模式已配置
3. PockRef 不需要额外权限即可检测 S Pen 侧键

### Q: 场景名称后面的 "•" 是什么意思？
**A:** "•" 表示场景有未保存的修改。保存后该标记会消失。

### Q: 如何更改画布背景颜色？
**A:** 设置 → 外观 → 背景颜色，选择预设色或使用 HSV 色轮自定义颜色。
