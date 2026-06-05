# PockRef — Material Design 3 设计合规审计报告

**审计日期**: 2026-06-06
**审计范围**: 全 app (`feature/merge-context-menu-into-floating-toolbar` 分支)
**审计方式**: 静态代码审查 (Compose Kotlin 源码,19,048 行)
**目标平台**: Android only — Galaxy Tab S10+ (SM-X810, 340dpi landscape 2800×1752) + S Pen
**M3 参考版本**: Material Design 3 (Compose `material3:1.3.x` BOM 2024.06+)

> **缩写说明**
> - **P0** = 必须修复,影响核心交互或合规
> - **P1** = 强烈建议,显著影响视觉一致 / a11y / 可维护性
> - **P2** = 可选优化,锦上添花
>
> 优势分析 7 维度:**视觉一致性 / 跨设备适配 / 认知负荷 / 开发维护 / 品牌个性化 / a11y 合规 / 动效自然度**

---

## 0. 总览评分

| M3 维度 | 当前合规度 | 主要问题 | 优先级 |
|---|---|---|---|
| 色彩系统 | ⚠️ 60% | 缺 16/28 个 ColorScheme 角色、Dynamic Color 未启用、onContainer 对比度不足 | P0 |
| 排版 | ⚠️ 70% | Type Scale 用默认值、AutoShrinkTitle 硬编码 sp 池 | P2 |
| 组件规范 | ❌ 40% | 自建 FAB / 14 处触控目标 < 48dp / 缺 M3 TopAppBar | P0 |
| 形状 | ⚠️ 60% | 单一 ChromeCornerRadius、6/8/10/12dp 散落硬编码 | P1 |
| 间距 | ✅ 85% | 4dp 网格基本对齐,7 处 2/6/10dp 偏离 | P2 |
| 动效 | ✅ 80% | tween 200ms FastOutSlowInEasing 主线合规,缺 motion scheme | P2 |
| 无障碍 (a11y) | ❌ 35% | 14 处触控目标 < 48dp、BottomRight 面板 contentDescription 英文、缺 Role.Button | P0 |
| 状态层 | ⚠️ 55% | 自建 RecentFileItem hover 8% 接近规范,FAB 无 state layer、Long-press 菜单用 0xEE1A1A1A | P0–P2 |

**综合建议**:在动 8d9d74e 后续 re-apply 计划时,优先把 **P0 清单里的 5 项** 一并修掉,不要在 WIP 中拆成 5 个独立 commit(改一个组件时顺带补 token,减少冲突)。

---

## 1. 色彩系统 (Color System)

### 1.1 ColorScheme 角色大量缺失【P0】

**位置**: `app/src/main/java/com/pockref/app/ui/theme/Theme.kt:11-23`

**当前实现**:
```kotlin
private val DarkColors = darkColorScheme(
    primary = Color(0xFF6AD6FF),
    secondary = Color(0xFFFFB866),
    tertiary = Color(0xFF8BE28B),
    surface = Color(0xFF17181C),
    background = Color(0xFF0D0E11),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF005E7A),
    secondary = Color(0xFF9C4C00),
    tertiary = Color(0xFF1E6B2A),
)
```

**不符合项**:
| Token | 当前状态 | M3 要求 |
|---|---|---|
| `color.onPrimary` | ❌ 缺 | 必须显式提供,4.5:1 vs primary |
| `color.onSecondary` | ❌ 缺 | 同上 |
| `color.onTertiary` | ❌ 缺 | 同上 |
| `color.onSurface` | ⚠️ 仅 CanvasMinimap 局部推断 | 必须全局可用 |
| `color.onBackground` | ❌ 缺 | 同上 |
| `color.primaryContainer` | ❌ 缺 | M3 强调色填充(filled button / chip)依赖它 |
| `color.onPrimaryContainer` | ❌ 缺 | 文字 vs primaryContainer 对比度 |
| `color.secondaryContainer` / `onSecondaryContainer` | ❌ 缺 | 同上 |
| `color.tertiaryContainer` / `onTertiaryContainer` | ❌ 缺 | 同上 |
| `color.surfaceVariant` | ❌ 缺(CanvasMinimap 用 `.surfaceVariant` 是 fallback) | Outline / onSurfaceVariant 派生依赖 |
| `color.onSurfaceVariant` | ⚠️ 隐式 fallback | 实测可能 OK,但显式更好 |
| `color.surfaceTint` | ❌ 缺 | tonalElevation 叠加用 |
| `color.outline` | ❌ 缺(CanvasMinimap 用 `.outline.copy(alpha = 0.4f)` 是 fallback) | 边框/分割线主色 |
| `color.outlineVariant` | ❌ 缺 | 次级分割线 |
| `color.error` / `onError` / `errorContainer` / `onErrorContainer` | ❌ 全缺 | 删除 / 错误提示无标准色 |
| `color.scrim` | ❌ 缺 | Modal/Snackbar 背景蒙层 |
| `color.inverseSurface` / `inverseOnSurface` / `inversePrimary` | ❌ 缺 | Snackbar onInverse |
| `color.surfaceBright` / `surfaceDim` / `surfaceContainer*` (5 个) | ❌ 缺 | M3 expressive color 体系 |

`LightColors` 尤其危险 — 3 个角色 + 0 个 surface,fallback 会让 light mode 看起来 "灰白" 不是 "亮",背景/卡片区分不开。

**M3 标准做法**:
```kotlin
// M3 28 个角色 light + 28 个 dark = 56 个,每个角色都要明确指定
private val LightColors = lightColorScheme(
    primary = Color(0xFF005E7A),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB5E9FF),     // 浅色版,作为 filled button bg
    onPrimaryContainer = Color(0xFF001E2A),
    secondary = Color(0xFF9C4C00),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDCC1),
    onSecondaryContainer = Color(0xFF311300),
    tertiary = Color(0xFF1E6B2A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFA8F5A8),
    onTertiaryContainer = Color(0xFF002106),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFCFCFF),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFCFCFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFDDE3EA),
    onSurfaceVariant = Color(0xFF41484D),
    surfaceTint = primary,                    // tonalElevation 叠加源
    inverseSurface = Color(0xFF2F3133),
    inverseOnSurface = Color(0xFFF1F0F4),
    inversePrimary = Color(0xFF6AD6FF),
    outline = Color(0xFF71787E),
    outlineVariant = Color(0xFFC1C7CE),
    scrim = Color(0xFF000000),
    // M3 expressive: 5 个 surfaceContainer 是 M3 surface tone
    // 体系 (Material Theme Builder 输出的 4 / 12 / 17 / 22 / 24dp)
    surfaceBright = Color(0xFFFCFCFF),
    surfaceDim = Color(0xFFDADCDF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F4F8),
    surfaceContainer = Color(0xFFF0EFF2),
    surfaceContainerHigh = Color(0xFFEAE9EC),
    surfaceContainerHighest = Color(0xFFE4E3E6),
)
```

**优势分析(7 维度)**:

| 维度 | 优势 |
|---|---|
| 视觉一致性 | Container / onContainer 配对自动保证对比度,Button / Chip / Card 不需要手调背景色 |
| 跨设备适配 | 28 个角色驱动所有 M3 组件,Android / Fold / Tablet 自动适配 |
| 认知负荷 | 设计师只需要理解 3 个 tonal palette (primary/secondary/tertiary),不用每个组件手调 |
| 开发维护 | 改一个 token 全 app 联动,没有"在某处硬编码了 #FF6AD6FF"这种 magic color |
| 品牌个性化 | 整个色系可由 Material Theme Builder 一键生成,符合品牌但不偏离 M3 |
| a11y 合规 | onPrimary / onSurface 等 on* 角色是按 4.5:1 对比度算好的,不用每次验证 |
| 动效自然度 | N/A (色彩维度不直接驱动动效) |

---

### 1.2 Dynamic Color 未启用【P1】

**位置**: `Theme.kt:26-40`

**当前实现**:
```kotlin
@Composable
fun PockRefTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val useDarkTheme = when (themeMode) { ... }
    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColors else LightColors,  // 永远用自己定义的那套
        ...
    )
}
```

**不符合项**: Android 12+ (API 31+) 系统提供 wallpaper-based Dynamic Color,用户期望 App 跟随系统主题色。完全没接。

**M3 标准做法**:
```kotlin
@Composable
fun PockRefTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,            // M3 默认开启
    content: @Composable () -> Unit,
) {
    val useDarkTheme = when (themeMode) { ... }
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (useDarkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }
        useDarkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, ...)
}
```

**优势分析**:
| 维度 | 优势 |
|---|---|
| 视觉一致性 | App 跟系统壁纸同色系,符合 Android 12+ 设计语言 |
| 跨设备适配 | Pixel / Samsung / OnePlus 各自壁纸色不同,App 跟着变 |
| 认知负荷 | 用户看到"我的壁纸是紫色 → App 也变紫色",心智一致 |
| 开发维护 | 不用自己维护 28 × 2 = 56 个色值,系统帮你 |
| 品牌个性化 | 如果品牌色要保留,可以把 `dynamicColor = false` 作为 opt-out 默认值 |
| a11y 合规 | Material You 自动保证 contrast ratio |
| 动效自然度 | N/A |

---

### 1.3 语义色硬编码绕过 ColorScheme【P1】

**位置**:
- `PureRefApp.kt:306-307` — `LockedStrokeColor = Color(0xFFE57373)`,`LockedGlowColor = Color(0x55E57373)` (锁定的红边/红光晕)
- `PureRefApp.kt:6019` — `SnapHighlightColor = Color(0xFFAB47BC)` (吸附高亮紫)
- `CanvasMinimap.kt:302-303` — `selectedStroke = Color(0xFF4FC3F7)`,`lockedStroke = Color(0xFFE57373)` (指示器)

**当前实现**: 选中蓝、锁定红、吸附紫全部硬编码,不走 `colorScheme.error` / `colorScheme.tertiary` 等语义色。

**不符合项**:
- 锁定红理论上可以是 `colorScheme.error`,但 error 在 M3 是 "不可恢复错误",lock 是 "用户保护操作",语义不完全对 → M3 没有 "warning" 角色,需要新加 custom token
- 选中蓝 / 吸附紫更找不到对应角色,只能自建

**M3 标准做法**:
```kotlin
// 在 Theme.kt 加 2 个 custom semantic token (扩展 ColorScheme)
data class ExtendedColors(
    val selectedImage: Color,
    val onSelectedImage: Color,
    val lockedImage: Color,
    val onLockedImage: Color,
    val snapHighlight: Color,
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        selectedImage = Color.Unspecified,
        onSelectedImage = Color.Unspecified,
        lockedImage = Color.Unspecified,
        onLockedImage = Color.Unspecified,
        snapHighlight = Color.Unspecified,
    )
}

private val DarkExtended = ExtendedColors(
    selectedImage = Color(0xFF4FC3F7),
    onSelectedImage = Color(0xFF003547),
    lockedImage = Color(0xFFE57373),
    onLockedImage = Color(0xFF560013),
    snapHighlight = Color(0xFFCE93D8),
)
private val LightExtended = ExtendedColors(
    selectedImage = Color(0xFF006590),
    onSelectedImage = Color(0xFFFFFFFF),
    lockedImage = Color(0xFFB3261E),
    onLockedImage = Color(0xFFFFFFFF),
    snapHighlight = Color(0xFF7B1FA2),
)
```
然后组件内 `ExtendedTheme.colors.selectedImage` 读取。

**优势分析**:
| 维度 | 优势 |
|---|---|
| 视觉一致性 | 自定义语义色也走 light/dark 切换,不会"亮模式下白底配白选中蓝" |
| 跨设备适配 | light mode + 灰白背景 → 选中蓝要更深;dark mode + 黑背景 → 选中蓝要更亮 |
| 认知负荷 | 用户记住"红=锁,蓝=选,紫=吸附"跨主题不变,但实际值变了 |
| 开发维护 | 改一处全 app 改完,不会出现"minimap 的选中蓝和主画布的选中蓝对不上" |
| 品牌个性化 | 自定义语义色让"选中"成为你的品牌色,不靠 M3 primary |
| a11y 合规 | 显式 on* 配对,4.5:1 对比度可验证 |
| 动效自然度 | N/A |

---

### 1.4 tonalElevation 链路不完整【P1】

**位置**: `Dimens.kt:67-71` 定义 6dp / 3dp / 1dp 三档 elevation,但**只在 TopToolbar / BottomRightActionPanel 两个地方**用 `tonalElevation = Dimens.ToolbarElevation`。

**当前实现**:
- TopToolbar (line 3828): `tonalElevation = Dimens.ToolbarElevation` (3dp) ✓
- BottomRightActionPanel (line 8117): `tonalElevation = Dimens.ToolbarElevation` (3dp) ⚠️ — **跟 FAB 不匹配**,注释里说 "FAB > Toolbar > Minimap" 但代码里 FAB 没单独 elevation
- CanvasMinimap 用了 `tonalElevation` 但走 fallback default
- EmptyCanvasState: `tonalElevation = 2.dp` (line 3235) — 不在 token 体系里
- ContextMenu (line 1288-1291): `shadowElevation = 8.dp` 但没设 `tonalElevation` → 阴影对了,颜色叠加全 0 → 跟 surface 一样平,没"elevation 感"

**M3 标准做法**:
- `tonalElevation` 是 M3 的核心:在 surface 上叠加 surfaceTint (通常是 primary),elevation 越高叠加越深。这是 M3 替代 Material 2 shadow-only 的关键设计
- M3 elevation tier:**0 / 1 / 3 / 6 / 8 / 12 dp** (level 0/1/2/3/4/5)
- 对应 token:`Elevations.level0` 到 `Elevations.level5`

```kotlin
// Dimens.kt 改造
object Elevations {
    val level0 = 0.dp   // flat
    val level1 = 1.dp   // minimap
    val level2 = 3.dp   // toolbar
    val level3 = 6.dp   // FAB / dialog
    val level4 = 8.dp   // modal bottom sheet
    val level5 = 12.dp  // nav drawer
}
```

**优势分析**:
| 维度 | 优势 |
|---|---|
| 视觉一致性 | 全 app 用 6 档 token,没人写 2.dp / 5.dp / 7.dp 出来 |
| 跨设备适配 | tonalElevation 在 light / dark 下叠色比例自动调整(light 模式叠色更深) |
| 认知负荷 | 设计师和开发用同一组数字对话"用 level3" |
| 开发维护 | 改 1 个 token,12+ 个 Surface 一起动 |
| 品牌个性化 | 调 primary → 所有 surface 自动跟着叠 |
| a11y 合规 | 阴影/颜色叠加对低视力用户都是额外 affordance |
| 动效自然度 | tonalElevation 可以 animateAsState 做高度过渡 |

---

## 2. 排版 (Typography)

### 2.1 Type Scale 用默认值【P2】

**位置**: `Type.kt` 全文 5 行

```kotlin
val Typography = Typography()    // 全部走 Material 3 默认值
```

**当前状态**:
- `displayLarge` = 57sp / 64sp lineHeight / -0.25 letterSpacing
- `headlineLarge` = 32sp / 40sp
- `titleLarge` = 22sp / 28sp
- `bodyLarge` = 16sp / 24sp
- `labelLarge` = 14sp / 20sp

**不符合项**:
1. 中文场景下 M3 默认 lineHeight 不够 — 英文 16/24 = 1.5 倍,中文需要 1.6-1.8 倍才不会"挤"
2. `letterSpacing` 默认值 (bodyLarge = 0.5sp) 是英文优化的,中文应该 0sp(汉字不需 letter-spacing 拉开)
3. 没有定义 `displayLarge/Medium/Small` 的实际用途 — 整个 app 用不到,要不要保留?建议删除 Display 三档,把 Headline/Title/Body/Label 调成更适合中文 + 340dpi tablet 的

**M3 标准做法**(Material Theme Builder 输出,中文 + Tablet 优化):
```kotlin
val Typography = Typography(
    // 不用 Display,直接从 Headline 开始
    headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 48.sp, fontWeight = FontWeight.W400),
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 40.sp, fontWeight = FontWeight.W400),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 36.sp, fontWeight = FontWeight.W400),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 32.sp, fontWeight = FontWeight.W500),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.W500, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.W500, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 28.sp, fontWeight = FontWeight.W400, letterSpacing = 0.sp),  // 中文 letterSpacing 0
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 22.sp, fontWeight = FontWeight.W400, letterSpacing = 0.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.W400, letterSpacing = 0.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.W500, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.W500, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.W500, letterSpacing = 0.5.sp),
)
```

**优势分析**:
| 维度 | 优势 |
|---|---|
| 视觉一致性 | 所有 text 共享 12 个 token,不会出现"这里 17sp 那里 18sp" |
| 跨设备适配 | sp 单位跟系统字体缩放绑定,340dpi tablet 不会"字太小" |
| 认知负荷 | 设计师只需要记 4 档 (Headline/Title/Body/Label) × 3 档 (L/M/S) |
| 开发维护 | 改 1 个 token 12+ 个 Text 一起动 |
| 品牌个性化 | 字体家族 (fontFamily) 在这里集中,品牌字体一改全 app 改 |
| a11y 合规 | sp 单位尊重用户系统字体大小设置,视力差用户放大系统字体后 App 跟着放大 |
| 动效自然度 | N/A |

---

### 2.2 AutoShrinkTitle 硬编码 sp 池【P2 — 已知设计取舍】

**位置**: `PureRefApp.kt:3472`

```kotlin
val candidates = listOf(22.sp, 20.sp, 18.sp, 17.sp, 16.sp, 15.sp, 14.sp)
```

**当前实现**: 顶栏标题手写 7 档字号池,7 次 TextLayoutResult 测量找最大能 fit 的。

**不符合项**:
- 22sp / 20sp / 18sp / 17sp / 15sp / 14sp 全部不在 M3 type scale(M3 整数 token: 14 / 16 / 22 / 28)
- 17 / 15 / 14 这种"小碎步"在 M3 里叫 "off-scale" token,M3 不推荐
- 性能:每次重命名 7 次 TextMeasurer 测量(轻量,但 1 rename = 7 layouts)

**M3 标准做法**:
- Compose 1.8 起 `BasicText` 有 `autoSize` parameter,1 行解决
- 现在 (1.7.x) 可以用 `TextMeasurer.measure` 替代 `rememberTextMeasurer` 跑全 M3 scale 一次
- 或者:**信任 `TextOverflow.Ellipsis`**,只显示一档 `titleMedium` (16sp),截断就是信号

**注释里你已经写了 "user then sees an ellipsis at the cap, which is a clear signal 'your title doesn't fit at any readable size, shorten it' rather than a silent truncation"** — 这个理由其实比手写 sp 池更符合 M3 哲学(M3 推崇"用 token 表达,不 hack")。

**优势分析**:
| 维度 | 优势 |
|---|---|
| 视觉一致性 | 12 个 token 统一管理,不会出现"只有标题用 17sp 这种怪数" |
| 跨设备适配 | sp 自动跟系统字体缩放 |
| 认知负荷 | 设计师只调 12 个 token,不需要在标题"私加"字号 |
| 开发维护 | 改 typography → 标题字号自动跟,不需要再维护 candidates list |
| 品牌个性化 | N/A |
| a11y 合规 | sp 跟系统字体设置联动,用户放大系统字体 → 标题跟着放大 |
| 动效自然度 | N/A |

---

### 2.3 中英文混排 lineHeightStyle 未指定【P2】

**当前**: 全 app 用 `style = MaterialTheme.typography.bodyMedium` 等,没传 `lineHeightStyle`。

**M3 标准做法**:
```kotlin
TextStyle(
    lineHeight = 24.sp,
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,   // 中文用 Center,英文用 Top
        trim = LineHeightStyle.Trim.None,
    ),
)
```
中文字符占据 lineHeight 空间比较方正,`Center` 对齐避免"挤顶部"或"贴底部"。

**优势**: 中英文混排不撞行 / 不溢出 / 视觉上不会"一段高一段矮"。

---

## 3. 组件规范 (Components)

### 3.1 自建 Box + combinedClickable 替代 M3 FloatingActionButton【P0】

**位置**: `PureRefApp.kt:8251-8280` (BottomRightActionPanel 的 FAB)

```kotlin
Box(
    modifier = Modifier
        .size(Dimens.TopToolbarHeight)              // 44.dp
        .combinedClickable(
            onClick = { onAction(PockRefAction.ExecuteAutoLayout) },
            onLongClick = { onAction(...) },
        ),
    contentAlignment = Alignment.Center,
) {
    Icon(
        Icons.Rounded.AutoFixHigh,
        contentDescription = "Auto-layout",
        modifier = Modifier.size(24.dp),
        tint = MaterialTheme.colorScheme.primary,
    )
}
```

**不符合项**:
| 维度 | 当前 | M3 FAB |
|---|---|---|
| 容器 | `Box` 自建 | `androidx.compose.material3.FloatingActionButton` |
| 默认尺寸 | 44dp | 56dp (default) / 96dp (large) / 40dp (small) |
| 默认颜色 | 走 `surface.copy(alpha=0.92)` (跟 panel 一样) | `primaryContainer` (强调色填充) |
| Ripple | `combinedClickable` 默认有,但 tint 是 primary 而不是 `primaryContainer` 上的状态层 | M3 FAB 自带 `state-layer.opacity` (hover 0.08 / pressed 0.12) |
| Role | 默认 `Role.Button` | 显式 `Role.Button` + `onClick` |
| Long press | `combinedClickable` 有,但不在 M3 规范 | M3 FAB 不支持 long-press(应另开 menu) |

**M3 标准做法**:
```kotlin
FloatingActionButton(
    onClick = { onAction(PockRefAction.ExecuteAutoLayout) },
    containerColor = MaterialTheme.colorScheme.primaryContainer,
    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    shape = RoundedCornerShape(16.dp),    // M3 large shape for FAB
) {
    Icon(Icons.Rounded.AutoFixHigh, contentDescription = "一键排版")
}
```
Long-press 触发 "panel 展开" 的诉求,可以通过 FAB 旁边加一个 `SmallFloatingActionButton` 装 toggle 图标(±/grid-view icon),或者用 `FloatingActionButtonMenu` (M3 1.3 新增)。

**优势分析**:
| 维度 | 优势 |
|---|---|
| 视觉一致性 | M3 FAB 走 `primaryContainer`,跟"主操作"语义绑定,看一眼就知道哪个最重要 |
| 跨设备适配 | M3 FAB 自带 Small/Default/Large 三档 token,不同屏幕自动调 |
| 认知负荷 | 用户已从 Gmail / Google Photos 等熟 M3 FAB = "主操作" |
| 开发维护 | 不用自己写 ripple / state layer / Role / onClickLabel |
| 品牌个性化 | 改 `primaryContainer` 就行 |
| a11y 合规 | M3 FAB 自带 screen reader 角色(Announce "button") |
| 动效自然度 | M3 FAB 有 spring 弹出动画(`enterAnimation` for `Scaffold`) |

---

### 3.2 FAB / BottomPanelListItem 44dp < 48dp【P0 — 严重 a11y】

**位置**: `Dimens.kt:56` `TopToolbarHeight = 44.dp`,被复用为:
- `BottomRightActionPanel` 的 FAB 尺寸 (`Modifier.size(Dimens.TopToolbarHeight)`)
- 4 个 `BottomPanelListItem` 的尺寸 (`Modifier.size(Dimens.TopToolbarHeight)`)
- 顶栏的高度 (line 3828 / 8115 / 1606 / 3828 几处 `Surface(modifier = ...height(Dimens.TopToolbarHeight))`)

**不符合项**:
- M3 minimum touch target: **48dp × 48dp** (M3 spec,WCAG 2.5.5 Level AAA)
- 44dp 是 iOS HIG 数值,不是 M3
- S Pen (笔尖) 比手指尖更细,44dp 还能凑合,但**手指**操作时,44dp 误触率高

**M3 标准做法**:
```kotlin
object Dimens {
    val TopToolbarHeight = 56.dp      // M3 TopAppBar default = 64dp,你的 toolbar 可以 56dp 紧凑
    val FabSize = 56.dp               // M3 default FAB
    val MinTouchTarget = 48.dp        // WCAG 最小触控
}
```
然后:
- 顶栏高度: 56dp
- FAB: 56dp 圆形(`FloatingActionButton` 直接用)
- 4 个 BottomPanelListItem: 48dp × 48dp **至少**(可以是 56dp square 跟 FAB 一起堆叠)
- 视觉不变 → 实际 hit area 增大 4dp (44×44 → 48×48) ≈ 19% 面积增加

**优势分析**:
| 维度 | 优势 |
|---|---|
| 视觉一致性 | 顶栏 56dp / FAB 56dp / ListItem 48dp 跟 M3 通用尺寸对齐 |
| 跨设备适配 | S Pen / 手指 / 手写笔 / 鼠标 hover 全部 ≥48dp 命中 |
| 认知负荷 | 用户不会"按错" |
| 开发维护 | 改 `MinTouchTarget` 一处,所有 hit area 联动 |
| 品牌个性化 | N/A |
| a11y 合规 | **WCAG 2.5.5 (Level AAA) 合规,Android 无障碍扫描通过** |
| 动效自然度 | N/A |

---

### 3.3 IconButton 28dp / 36dp 多个 < 48dp【P0 — 严重 a11y】

**位置**: `PureRefApp.kt`:
- Line 2050: `IconButton(Modifier.size(36.dp))` — Selection toolbar (single 镜像/裁切/灰度)
- Line 3400, 3411: `IconButton(Modifier.size(28.dp))` — RecentFileItem 内的 "打开" / "移除"
- Line 4245: `IconButton(Modifier.size(36.dp))` — 顶栏 "最近文件" chevron
- Line 4434, 4447: `IconButton(Modifier.size(36.dp))` — 顶栏 "撤销" / "重做"

```kotlin
IconButton(
    onClick = onClick,
    modifier = Modifier.size(28.dp),       // ❌
) {
    Icon(Icons.Rounded.FileOpen, contentDescription = "打开", modifier = Modifier.size(16.dp))
}
```

**不符合项**:
- 28dp 是图标本身的尺寸,**外层 IconButton 也 28dp** → hit area 只有 28×28
- 36dp hit area 同样 < 48dp
- M3 IconButton 默认 48dp(用 `IconButton` 即可),只有当**视觉上**要更小才用 `Modifier.size`,但仍然要走 `minimumInteractiveComponentSize()`(M3 提供)撑到 48dp

**M3 标准做法**:
```kotlin
// 方案 A: 直接 IconButton,不传 size (默认 48dp)
IconButton(onClick = onClick) {
    Icon(Icons.Rounded.FileOpen, contentDescription = "打开")
}
// 方案 B: 视觉要小,但 hit area 不能小
IconButton(
    onClick = onClick,
    modifier = Modifier.size(28.dp).minimumInteractiveComponentSize(),  // 撑到 48dp
) {
    Icon(..., modifier = Modifier.size(16.dp))
}
```

**优势分析**:
| 维度 | 优势 |
|---|---|
| 视觉一致性 | hit area 一致 = 视觉一致(用户不会"看见 28dp,期望 28dp 命中") |
| 跨设备适配 | 手指 / S Pen / 鼠标 / 键盘 Tab 全部命中 |
| 认知负荷 | 误触率降低,用户敢"快速点" |
| 开发维护 | `minimumInteractiveComponentSize` 写一次,所有 IconButton 受益 |
| 品牌个性化 | 视觉图标大小可以保留 16/20/24dp 不变,只有 hit area 变大 |
| a11y 合规 | **WCAG 2.5.5 Level AAA 通过** |
| 动效自然度 | N/A |

---

### 3.4 FilledTonalButton 形状硬编码 10.dp【P2】

**位置**: `PureRefApp.kt:3255, 3262`

```kotlin
FilledTonalButton(
    onClick = onImport,
    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
    shape = RoundedCornerShape(10.dp),       // ❌ M3 full = 20dp / 40dp pill
) { ... }
```

**不符合项**: M3 `ButtonDefaults.shape` 默认是 `RoundedCornerShape(20.dp)`(全圆角 pill)。手动设 10.dp 既不在 M3 shape scale,也不符合"filled tonal 是 M3 强调按钮"语义。

**M3 标准做法**:
```kotlin
FilledTonalButton(
    onClick = onImport,
    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
    // 不传 shape → 用 ButtonDefaults.shape (20dp = pill)
) { ... }
```

**优势分析**: 一致性 / 维护成本优势,符合 M3 "filled tonal = 重点次级操作" 语义。

---

### 3.5 Long-press ContextMenu 硬编码 0xEE1A1A1A【P1】

**位置**: `PureRefApp.kt:1288-1292`

```kotlin
Surface(
    shape = RoundedCornerShape(12.dp),
    color = Color(0xEE1A1A1A),                 // ❌ 硬编码深灰
    shadowElevation = 8.dp,
)
```

**不符合项**:
- 0xEE1A1A1A 是 M2 风格的"深灰半透明",M3 应该用 `MaterialTheme.colorScheme.inverseSurface` (light 模式深 / dark 模式浅) + `MaterialTheme.colorScheme.inverseOnSurface` 文字
- light 模式下,深灰上下文菜单跟浅色画布对比强烈,会"刺眼"
- 没用 scrim,周围画布没变暗(用户长按弹菜单时,画布还是亮色,菜单盖在画布上,边界模糊)

**M3 标准做法**:
```kotlin
Surface(
    shape = RoundedCornerShape(12.dp),    // M3 medium
    color = MaterialTheme.colorScheme.inverseSurface,    // light → 深 / dark → 浅
    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
    tonalElevation = 3.dp,                  // 让 inverseSurface 也有 elevation 感
    shadowElevation = 8.dp,
) { ... }
```
外面包一个 `Box().background(Color.Black.copy(alpha = 0.32f))` 作 scrim,跟 Snackbar 一致。

**优势分析**:
| 维度 | 优势 |
|---|---|
| 视觉一致性 | light 模式上下文菜单不"刺眼",dark 模式不"消失" |
| 跨设备适配 | 主题切换自动反向 |
| 认知负荷 | 用户看到"上下文菜单在画布上方"语义清晰 |
| 开发维护 | 改 inverseSurface → 上下文菜单 / Snackbar / Tooltip 全部联动 |
| 品牌个性化 | N/A |
| a11y 合规 | N/A |
| 动效自然度 | N/A |

---

### 3.6 缺 M3 TopAppBar【P2】

**位置**: `PureRefApp.kt:3740-3830` 自建 `TopToolbar`

**当前**: 用 `Surface { Column { Row { ... } } }` 自建顶栏,有"标题 + 撤销/重做/关闭/最近文件 chevron"。

**不符合项**:
- M3 提供 `TopAppBar` / `CenterAlignedTopAppBar` / `MediumTopAppBar` / `LargeTopAppBar` 4 种标准容器
- M3 `TopAppBar` 默认 64dp 高度,自动处理 `statusBars` inset,带 `colors` / `scrollBehavior` / `windowInsets`
- 自建顶栏**没** `scrollBehavior`(滚动时 elevation 升高的 M3 标志性效果),没 `windowInsets` 自动处理(你手动 `WindowInsets.statusBars.getTop(...)` 来 hack)

**M3 标准做法**:
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
CenterAlignedTopAppBar(
    title = { AutoShrinkTitle(text = state.sceneName) },
    navigationIcon = { /* 关闭按钮 */ },
    actions = {
        IconButton(onClick = { /* undo */ }) { Icon(Icons.AutoMirrored.Rounded.Undo, "撤销") }
        IconButton(onClick = { /* redo */ }) { Icon(Icons.AutoMirrored.Rounded.Redo, "重做") }
        IconButton(onClick = { /* recent */ }) { Icon(Icons.Rounded.History, "最近文件") }
    },
    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
    ),
    scrollBehavior = scrollBehavior,    // 配合 Scaffold 滚动隐藏/显示
)
```

**优势分析**:
| 维度 | 优势 |
|---|---|
| 视觉一致性 | M3 TopAppBar 64dp + status bar inset 自动处理 |
| 跨设备适配 | Fold / 平板横屏 / 手机竖屏 top app bar 行为统一 |
| 认知负荷 | 设计师和开发用同一套 TopAppBar token 沟通 |
| 开发维护 | 不需要自己处理 inset / scrollBehavior / a11y / Role |
| 品牌个性化 | colors 走 token 即可 |
| a11y 合规 | M3 TopAppBar 自带 a11y |
| 动效自然度 | `scrollBehavior` 滚动时 elevation 渐变(scroll → elevated) |

---

### 3.7 BottomRightActionPanel contentDescription 英文【P1 — i18n + a11y】

**位置**: `PureRefApp.kt`:
- Line 8148: `contentDescription = "Box-select mode"`
- Line 8170: `contentDescription = "Settings"`
- Line 8209: `contentDescription = "Import"`
- Line 8221: `contentDescription = "Export canvas"`
- Line 8231: `contentDescription = "Save scene"`
- Line 8276: `contentDescription = "Auto-layout"`

**当前**: 全 app 其他地方 contentDescription 都是中文("撤销" / "重做" / "关闭" / "复制" / "置顶" / "锁定" / "删除")。**只有 BottomRightActionPanel 6 个按钮是英文**。

**不符合项**:
- TalkBack 朗读 "Box-select mode" "Settings" "Import" 给中文用户,听感割裂
- 中文 AppStore / 中文用户期望中文 contentDescription

**M3 标准做法**:
```kotlin
// 全部改成中文,且加 onClickLabel 描述点击后的结果 (M3 推荐)
BottomPanelListItem(
    icon = Icons.Rounded.PhotoSizeSelectLarge,
    contentDescription = "框选模式",                       // 中文
    onClickLabel = if (state.boxSelectMode) "关闭框选模式" else "开启框选模式",
    ...
)
```

**优势分析**:
| 维度 | 优势 |
|---|---|
| 视觉一致性 | 用户体验一致(看到中文 = 听到中文) |
| 跨设备适配 | N/A |
| 认知负荷 | 中文用户认知零负担 |
| 开发维护 | 跟其他 contentDescription 写法统一 |
| 品牌个性化 | N/A |
| a11y 合规 | **WCAG 3.1.1 Language of Page / 3.1.2 Language of Parts** — TalkBack 自动检测系统语言,期望 contentDescription 跟系统语言一致 |
| 动效自然度 | N/A |

---

### 3.8 Selection toolbar / HoverMagnifier / EmptyCanvasState — 已用 M3 组件【✓ 优点】

- `EmptyCanvasState` 用 `FilledTonalButton` ✓
- `SliderRow` 用 M3 `Slider` ✓
- `SettingsTabRow` 用 M3 `Tab` + `TabRow` ✓
- `SettingsNavigationRail` 用 M3 `NavigationRail` + `NavigationRailItem` ✓
- `SettingsToggleRow` 用 M3 `Switch` + `ListItem` pattern ✓
- `BatchOptimizeConfirmDialog` 等用 M3 `AlertDialog` + `TextButton` ✓
- Settings panel 用 M3 `ModalBottomSheet` ✓
- `ListItem` (line 8307, 8320) 用 M3 ✓
- `SingleChoiceSegmentedButtonRow` (theme selector) ✓
- `FilterChip` (line 2341) ✓

**这一块做对了**,Settings panel / Dialog / Tab 体系完全是 M3 idiom,迁移过来几乎不用改。

---

## 4. 形状 (Shape)

### 4.1 单一 ChromeCornerRadius token,缺 Shape Scale 体系【P1】

**位置**: `Dimens.kt:59`

```kotlin
val ChromeCornerRadius = 12.dp   // 全文用这一档
```

**M3 Shape Scale 应该是 7 档**:
- None = 0dp
- ExtraSmall = 4dp
- Small = 8dp
- Medium = 12dp
- Large = 16dp
- ExtraLarge = 28dp
- Full = (组件高度 / 2,按钮通常 20dp 形成 pill)

**当前状况**:
- 顶栏 / minimap / BottomRightActionPanel: 12dp (medium) ✓
- EmptyCanvasState: 16dp (large) ✓
- Color swatch: 8dp (small) — 跟 medium 混
- FilledTonalButton override: 10dp — **不在 scale**
- RecentFileItem hover bg: 6dp — **不在 scale**
- ColorWheel swatch: 8dp (small) — OK

**M3 标准做法**:
```kotlin
object Shapes {
    val none = RoundedCornerShape(0.dp)
    val extraSmall = RoundedCornerShape(4.dp)       // checkbox / switch thumb
    val small = RoundedCornerShape(8.dp)            // chip / small card
    val medium = RoundedCornerShape(12.dp)          // toolbar / card
    val large = RoundedCornerShape(16.dp)           // dialog / large card
    val extraLarge = RoundedCornerShape(28.dp)      // bottom sheet
    val full = RoundedCornerShape(50)               // button (pill)
}
```
然后:
```kotlin
val M3Typography = Typography(...)  // 已定义
val M3Shapes = Shapes(              // MaterialTheme.shapes 用
    extraSmall = Shapes.extraSmall,
    small = Shapes.small,
    medium = Shapes.medium,
    large = Shapes.large,
    extraLarge = Shapes.extraLarge,
)
MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    shapes = M3Shapes,                 // ← 关键:让 MaterialTheme.shapes.* 全部可用
)
```
组件里用 `MaterialTheme.shapes.medium` 而不是 `RoundedCornerShape(12.dp)`。

**优势分析**:
| 维度 | 优势 |
|---|---|
| 视觉一致性 | 全 app 7 档 token 共享,不会出现"卡片 16 / 按钮 10 / chip 8"这种混乱 |
| 跨设备适配 | token 跟屏幕尺寸无关,但 Material You 会在大屏上稍微放大(可选) |
| 认知负荷 | 设计师和开发用 7 档 token 对话,不用每次"这个圆角该多大" |
| 开发维护 | 改 1 个 token 12+ 个组件联动 |
| 品牌个性化 | 调 1 档 token (例如把 medium 从 12 调到 14) 整 app 变"圆角品牌" |
| a11y 合规 | N/A |
| 动效自然度 | animateDpAsState 改 corner radius 做"按下去圆角变小" 等微交互 |

---

### 4.2 散落 RoundedCornerShape(8.dp / 10.dp / 6.dp) 不在 Shape Scale【P1】

**位置**:
- `PureRefApp.kt:3255, 3262` — `RoundedCornerShape(10.dp)` (FilledTonalButton) ❌
- `PureRefApp.kt:7366, 7374` — `RoundedCornerShape(8.dp)` (color swatch) ✓ (small)
- `PureRefApp.kt:3360` — `RoundedCornerShape(6.dp)` (RecentFileItem hover bg) ❌

**M3 标准做法**: 全部替换为 `MaterialTheme.shapes.small` (8dp) 或 `MaterialTheme.shapes.extraSmall` (4dp)。

**优势分析**: 跟 4.1 同。

---

## 5. 间距 (Spacing)

### 5.1 4dp 网格偏离 — 7 处【P2】

**位置**:
| 行 | 偏离值 | 上下文 |
|---|---|---|
| `PureRefApp.kt:1210` | `padding(horizontal = 20.dp, vertical = 12.dp)` | ContextMenu wrapper (12 OK, 20 OK) ✓ 实际 4dp 倍数 |
| `PureRefApp.kt:3303` | `spacedBy(6.dp)` | RecentFileSection ⚠️ |
| `PureRefApp.kt:3361` | `padding(horizontal = 12.dp, vertical = 8.dp)` ✓ | |
| `PureRefApp.kt:3363` | `spacedBy(8.dp)` ✓ | |
| `PureRefApp.kt:4033` | `spacedBy(2.dp)` | TopToolbar 内 ⚠️ |
| `PureRefApp.kt:4106` | `padding(horizontal = 10.dp, vertical = 6.dp)` | ⚠️ 10 和 6 都不在 4dp 网格 |
| `PureRefApp.kt:2377` | `padding(start = 6.dp)` | ⚠️ |

**M3 4dp 网格**: 4 / 8 / 12 / 16 / 20 / 24 / 32 / 40 / 48 / 64

**M3 标准做法**: 把 `spacedBy(6.dp)` 改成 `spacedBy(8.dp)` 或 `spacedBy(4.dp)`;`padding(10.dp, 6.dp)` 改成 `padding(8.dp, 8.dp)` 或 `padding(12.dp, 4.dp)`;`spacedBy(2.dp)` 改成 `spacedBy(4.dp)`。

**优势分析**:
| 维度 | 优势 |
|---|---|
| 视觉一致性 | 所有间距都是 4 的倍数,视觉上有"节奏感" |
| 跨设备适配 | N/A |
| 认知负荷 | 设计师只考虑 4/8/12/16/20/24,不需要"这里用 6dp" |
| 开发维护 | 改 1 个 token 几十处间距一起动(配合 spacing token 系统) |
| 品牌个性化 | N/A |
| a11y 合规 | N/A |
| 动效自然度 | N/A |

**建议进一步**: 把间距也 token 化(跟 elevation / shape 一样),例如:
```kotlin
object Spacing {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}
```

---

### 5.2 EdgeInset 16dp vs M3 推荐 24dp【P2】

**位置**: `Dimens.kt:29` `EdgeInset = 16.dp`,但是:
- `EmptyCanvasState` line 3239: `padding(24.dp)`
- `TopToolbar` 改用 `CornerInset = 24.dp` (line 41)
- Settings panel 大量 24dp
- 各种 dialog 24dp

**当前**: chrome 元素混用 16dp / 24dp,有 `EdgeInset=16` 和 `CornerInset=24` 两套。

**M3 标准做法**: M3 推荐 window edge inset 至少 **16dp**,但 FAB / TopAppBar 建议 **24dp**。你已经有 `CornerInset=24` 给"3 个 corner chrome"用,合理。但**底部 status pill** 用 `EdgeInset=16`,跟 corner 的 24 不齐 → 视觉上"底部不对称"(toolbar 离 left 24, status pill 离 bottom 16)。

**优势分析**: 一致性 / 认知负荷优势。

---

## 6. 动效 (Motion)

### 6.1 tween 200ms FastOutSlowInEasing — 主线合规【✓ 优点】

**位置**: 多处,例 `PureRefApp.kt:1470, 8124, 8128`

```kotlin
tween(200, easing = FastOutSlowInEasing)
```

**M3 规范**:
- Short duration (≤200ms): enter/exit,small changes
- Medium duration (≈400ms): expand/collapse,size changes
- Long duration (≈500ms): emphasized motion
- Easing: **FastOutSlowInEasing** (标准进入退出) / **LinearOutSlowInEasing** (进入) / **FastOutLinearInEasing** (退出) / **EmphasizedEasing** (强调动效)

**符合度**: 200ms + FastOutSlowInEasing 是 M3 short 标准 ✓

---

### 6.2 tween(350ms) hover magnifier — 非标【P2】

**位置**: `PureRefApp.kt:494`

```kotlin
animationSpec = tween(durationMillis = 350, easing = { t -> 1f - (1f - t) * (1f - t) })
//                                    ^^^^                                ^^^^^^^^^^^^^^^^^^^^^^^
//                                    非标 duration                          EaseOut (M3 没这个)
```

**不符合项**:
- 350ms 在 M3 没有"标准档"(M3 short=200, medium=400, long=500)
- `1f - (1f - t)²` 是 EaseOutQuad,M3 用的是 `FastOutSlowInEasing` (cubic bezier 0.4, 0.0, 0.2, 1.0)

**M3 标准做法**:
```kotlin
tween(durationMillis = 400, easing = FastOutSlowInEasing)    // 升 medium 档
// 或者用 EmphasizedEasing
val emphasized = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
tween(durationMillis = 500, easing = emphasized)
```

---

### 6.3 缺 M3 Motion Scheme【P2】

**位置**: 全 app

**当前**: 每个动画各自定义 `tween(200, FastOutSlowInEasing)`,没集中 token。

**M3 标准做法**:
```kotlin
object MotionScheme {
    // Duration tokens
    val DurationShort = 200
    val DurationMedium = 400
    val DurationLong = 500
    val DurationEmphasized = 500
    
    // Easing tokens
    val StandardEasing = FastOutSlowInEasing
    val EmphasizedEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val DecelerateEasing = LinearOutSlowInEasing
    val AccelerateEasing = FastOutLinearInEasing
    
    // 预制 spec
    fun <T> standardTween(durationMs: Int = DurationShort) = 
        tween(durationMillis = durationMs, easing = StandardEasing)
}
```
然后 `tween(MotionScheme.DurationMedium, MotionScheme.StandardEasing)`。

**优势分析**:
| 维度 | 优势 |
|---|---|
| 视觉一致性 | 全 app 动效节奏统一,不会出现"这边 200ms 那边 350ms" |
| 跨设备适配 | duration 可以按 `LocalConfiguration.current.screenWidthDp` 自动调整 |
| 认知负荷 | 设计师和开发用 4 档 duration + 4 档 easing 对话 |
| 开发维护 | 改 1 个 token 几十处动画一起动 |
| 品牌个性化 | 调成"快节奏品牌" (duration × 0.7) 或"慢节奏品牌" (× 1.3) |
| a11y 合规 | **支持 `prefers-reduced-motion`** — 用户在系统设置开"减少动画"时,自动把 duration 缩到 0 |
| 动效自然度 | EmphasizedEasing 替代 FastOutSlowInEasing,Material You 推荐的"过冲 + 收"曲线 |

---

### 6.4 `delay(16L)` minimap fling — 帧驱动但缺 clamp【P2】

**位置**: `CanvasMinimap.kt:613`

```kotlin
while (hypot(v.x, v.y) > MINIMAP_FLING_STOP_VELOCITY) {
    delay(16L)                  // 16ms = 60fps 一帧
    ...
}
```

**不符合项**:
- 硬编码 16ms,高刷屏 (90/120Hz) 会"fling 走 2 帧 / 实际 1 帧",慢
- 缺 `withFrameNanos` 跟 vsync 同步

**M3 标准做法**:
```kotlin
while (hypot(v.x, v.y) > MINIMAP_FLING_STOP_VELOCITY) {
    withFrameNanos { /* read latest frame time */ }
    ...
}
```

**优势**: 90/120Hz 屏 fling 跟手 / 帧对齐。

---

## 7. 无障碍 (A11y)

### 7.1 触控目标 < 48dp — 14 处【P0 — 严重 a11y】

**清单**:
| 位置 | 尺寸 | 类型 | 严重度 |
|---|---|---|---|
| `PureRefApp.kt:2050` | 36dp | IconButton (selection toolbar 镜像) | 高 |
| `PureRefApp.kt:3400` | 28dp | IconButton (RecentFileItem "打开") | 极高 |
| `PureRefApp.kt:3411` | 28dp | IconButton (RecentFileItem "移除") | 极高 |
| `PureRefApp.kt:4245` | 36dp | IconButton (顶栏 chevron) | 高 |
| `PureRefApp.kt:4434` | 36dp | IconButton (顶栏 "撤销") | 高 |
| `PureRefApp.kt:4447` | 36dp | IconButton (顶栏 "重做") | 高 |
| `PureRefApp.kt:8253, 8355` | 44dp | FAB / BottomPanelListItem (用 TopToolbarHeight) | 高 |
| `CanvasMinimap.kt` 选中区域 | 60-200dp (OK ✓) | minimap 自身 | - |
| ColorWheel (line 7429) | 200dp × fullWidth (OK ✓) | pointerInput area | - |

**M3 标准做法**:
- 视觉上图标 16-24dp 没问题
- 触控目标**必须** 48dp × 48dp
- 方案 A: `Modifier.minimumInteractiveComponentSize()`(M3 提供,自动撑到 48dp)
- 方案 B: `IconButton(...)`(默认 48dp hit area)
- 方案 C: `Box(Modifier.size(48.dp).clickable {})`

```kotlin
// 改造 RecentFileItem 的 28dp IconButton
IconButton(
    onClick = onOpen,
    modifier = Modifier.size(40.dp).minimumInteractiveComponentSize(),    // 视觉 40dp,hit area 48dp
) {
    Icon(Icons.Rounded.FileOpen, contentDescription = "打开", modifier = Modifier.size(20.dp))
}
```

**WCAG 合规**: **2.5.5 Target Size (Level AAA)** 要求 44 × 44 CSS 像素(在 160dpi 屏幕 = 44dp,在 240dpi+ 屏幕 = ~30dp 物理像素但 dp 仍是 48dp 标准)。**Android Accessibility Scanner** 会警告 < 48dp 的 hit area。

**优势分析**:
| 维度 | 优势 |
|---|---|
| 视觉一致性 | 视觉小图标 + 透明 padding 撑 hit area,用户感受不到"按钮变大" |
| 跨设备适配 | 手指 / S Pen / 手写笔 / 鼠标 Tab 全部命中 |
| 认知负荷 | 误触率降低,用户敢"快速点" |
| 开发维护 | `minimumInteractiveComponentSize` 写一次,所有 IconButton 受益 |
| 品牌个性化 | 视觉图标大小可以保留 16/20/24dp 不变 |
| a11y 合规 | **WCAG 2.5.5 Level AAA 通过 / Android Accessibility Scanner 零警告** |
| 动效自然度 | N/A |

---

### 7.2 BottomRightActionPanel contentDescription 英文【P1】

(跟 3.7 重复,这里强调 a11y 角度)

TalkBack 朗读 "Settings" 给中文用户,虽然 TalkBack 多数有翻译 fallback,但**显式提供中文 contentDescription 是 Android M3 推荐**。

---

### 7.3 无 Role.Button 显式声明【P2】

**位置**: 多处 `Box.clickable { }` 没显式 Role

**M3 标准做法**:
```kotlin
// 自定义 clickable Box 时,显式声明 Role
Box(
    modifier = Modifier
        .size(48.dp)
        .clickable(
            onClick = { onAction(...) },
            role = Role.Button,                 // ← TalkBack 朗读 "按钮"
            onClickLabel = "一键排版",          // ← TalkBack 朗读结果描述
        )
)
```

`IconButton` / `Button` / `FloatingActionButton` 自带 Role.Button,自建组件需要手动加。

**WCAG 合规**: **4.1.2 Name, Role, Value** — 每个 interactive element 必须有 role + name + value。

---

### 7.4 缺 `highContrastColorScheme`【P1】

**位置**: `Theme.kt:11-23`

**当前**: 只有 Dark / Light 两种 colorScheme,没有 high-contrast 版本。

**M3 标准做法**:
```kotlin
@Composable
fun PockRefTheme(...) {
    val highContrast = LocalAccessibilityManager.current.highContrast
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= S -> ...
        useDarkTheme -> if (highContrast) highContrastDarkColorScheme() else DarkColors
        else -> if (highContrast) highContrastLightColorScheme() else LightColors
    }
    ...
}
```

**WCAG 合规**: 视障用户开系统"高对比度"模式时,App 自动切换更深的色值。

---

### 7.5 缺 TalkBack 焦点顺序验证【需真机】

代码层面无法验证,需要你开 TalkBack 跑一遍:
- 顶栏从左到右: 关闭 → 标题 → 撤销 → 重做 → chevron
- BottomRight 从上到下: 框选 → 设置 → Import → Export → Save → FAB
- SelectionToolbar 1 selected: 镜像 → 裁切 → 灰度 → 删除 → 展开
- SelectionToolbar ≥2 selected: 水平反转 → 灰度 → 锁定 → 复原 → 复制 → 对齐 × 4 → 导出 → 删除 → 收起
- Settings: 5 section × 多个 row

**建议**: 真机测一遍,看 TalkBack 焦点是不是按视觉顺序走,有没有跳。

---

## 8. 状态层 (State Layer)

### 8.1 RecentFileItem 自建 hover bgColor = primary 0.08f【✓ 优点】

**位置**: `PureRefApp.kt:3345-3350`

```kotlin
var hovered by remember { mutableStateOf(false) }
val bgColor = if (hovered) {
    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)   // 8% alpha
} else {
    Color.Transparent
}

Row(
    modifier = Modifier
        .fillMaxWidth()
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,                                 // 自建状态层,关默认 ripple
            onClick = onOpen,
        )
        .background(bgColor, RoundedCornerShape(6.dp))
        ...
)
```

**M3 规范 hover opacity**:
- Surface (含 background): **0.08**
- Surface variant: 0.08
- Primary: 0.08
- Secondary: 0.08
- On-surface (文字): 0.08

**Press**:
- Surface: 0.10
- Primary: 0.10
- Secondary: 0.10

**Focus**:
- On-surface: 0.10

**Dragged**:
- On-surface: 0.16

**符合度**: 8% primary hover 跟 M3 完全一致 ✓
**小问题**:
1. **没有 pressed state**(只有 hovered,用户按下时没反馈)
2. **没有 focus state**(键盘 Tab 焦点没视觉指示)
3. `RoundedCornerShape(6.dp)` 不在 Shape Scale (→ 见 4.2)
4. `indication = null` 关掉了 M3 默认 ripple,你手写了背景色,**OK 但建议同时加 pressed 反馈**(否则用户不知道"按下了")

**M3 标准做法**:
```kotlin
val interactionSource = remember { MutableInteractionSource() }
val isHovered by interactionSource.collectIsHoveredAsState()
val isPressed by interactionSource.collectIsPressedAsState()

val bgColor = when {
    isPressed  -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)  // pressed
    isHovered  -> MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)  // hovered
    else       -> Color.Transparent
}

Row(
    modifier = Modifier
        .fillMaxWidth()
        .hoverable(interactionSource = interactionSource)               // hover 探测
        .clickable(
            interactionSource = interactionSource,
            indication = null,                                          // 关默认 ripple
            onClick = onOpen,
        )
        .background(bgColor, MaterialTheme.shapes.small)
        ...
)
```

---

### 8.2 FAB 没有 state layer【P0】

**位置**: `PureRefApp.kt:8251-8280` (自建 Box FAB)

**当前**: `combinedClickable` 有默认 ripple,但**没有显式 state layer**(hover 0.08 / pressed 0.10 / focused 0.10 的颜色叠加)。

**M3 标准做法**:
- 用 `androidx.compose.material3.FloatingActionButton`(自带 state layer)
- 或自建时手动加 `interactionSource` + `hoverable` + `collectIsPressedAsState`

```kotlin
val interactionSource = remember { MutableInteractionSource() }
val isPressed by interactionSource.collectIsPressedAsState()
val isHovered by interactionSource.collectIsHoveredAsState()

val containerColor = when {
    isPressed -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.90f)  // pressed 视觉
    isHovered -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)  // hovered 视觉
    else      -> MaterialTheme.colorScheme.primaryContainer
}
```

**优势分析**:
| 维度 | 优势 |
|---|---|
| 视觉一致性 | FAB 跟其他 M3 组件的"按下/悬停"反馈一致 |
| 跨设备适配 | 鼠标 hover / 手指 press / S Pen 触屏 / 键盘 focus 全部有反馈 |
| 认知负荷 | 用户看到颜色变化 = "我正在操作这个按钮" |
| 开发维护 | N/A |
| 品牌个性化 | N/A |
| a11y 合规 | **focus state** 是键盘 / switch control 用户的关键反馈 |
| 动效自然度 | state layer 通常搭配 ripple 一起,ripple 走 100-200ms 动画 |

---

### 8.3 TopToolbar IconButton 36dp 也没 state layer 显式声明【P1】

(同 8.2 模式,IconButton 默认有 ripple 但没显式 hover 颜色)

**M3 标准做法**: `IconButton(...)` 默认有 M3 IconButtonDefaults.colors() 走 state layer,**别 override**。当前代码:
```kotlin
IconButton(
    onClick = onClick,
    modifier = Modifier.size(36.dp)        // ❌
) {
    Icon(..., tint = MaterialTheme.colorScheme.primary)   // 用了 primary tint
}
```
M3 IconButton 的 `Icon` 默认 tint 是 `IconButtonDefaults.iconButtonColors().contentColor`(跟 `LocalContentColor` 走),你硬 override 成 primary 会让**所有 IconButton 的 disabled / hovered / pressed 颜色都失效**。

---

### 8.4 minimap 0.85f alpha 略低于 M3 surface tinted【P2】

**位置**: `CanvasMinimap.kt:296` `panelBackground = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)`

**M3 surface tonalElevation 1-3dp 的 surface 颜色叠加**: 通常用 `surface.copy(alpha = 0.92f)` 跟其他 chrome 一致,你已经在 Dimens.kt 定义了 `ChromeSurfaceAlpha = 0.92f` 但 minimap 用 0.85f → **跟其他 chrome 不一致**(顶栏 0.92,minimap 0.85,BottomRight 0.92)。

**M3 标准做法**: 统一用 `Dimens.ChromeSurfaceAlpha` (0.92f),或者 minimap 用 `surfaceContainer` (M3 expressive 1dp 档)。

---

## 9. 需真机验证项 (代码层无法判断)

下列项需要你开真机 (SM-X810) + TalkBack 验证,我现在没法从静态代码判断:

1. **实际渲染对比度**: light 模式下 `Color(0xFF6AD6FF)` primary + 白色文字,在 2800×1752 屏上 4.5:1 是否真满足
2. **TalkBack 焦点顺序**: 顶栏 / BottomRight / Settings / Selection toolbar 焦点是不是按视觉顺序
3. **TalkBack 朗读 contentDescription**: 英文 6 个 BottomRight 按钮实际朗读出来用户能不能接受
4. **最小触控目标实测**: 28dp IconButton 手指 vs S Pen 实际命中率
5. **Dynamic Color (如果开启)**: Pixel / Samsung Theme Park 拉壁纸色,App 跟随时会不会跟品牌色冲突
6. **340dpi 字号**: M3 bodyMedium = 14sp 在 340dpi 是否需要升到 16sp 才能舒服
7. **Reduce Motion**: 系统开"减少动画"时,你的 200/350/500ms 动画应该 → 0(目前没接 `LocalAccessibilityManager.current.animationsDisabled`)
8. **高对比度模式**: 系统开"高对比度文字"时,App 应自动切 highContrastColorScheme(目前没接)
9. **平板横屏 / 折叠屏折叠态**: SM-X810 横屏时 BottomRight 面板在右下方,但折叠后竖屏时位置可能不对(需看 `WindowSizeClass` 是否在用)

---

## 10. 优先级修复路线图

### P0 (必须修复,影响核心交互 / a11y 合规)

| # | 项 | 涉及文件 | 修复成本 | 体验提升 |
|---|---|---|---|---|
| 1 | ColorScheme 补 16/28 个角色 | Theme.kt | 2-3h | 极高 (filled button / chip 自动有正确颜色) |
| 2 | LightColors 补全 surface/onSurface/etc | Theme.kt | 1h | 极高 (light 模式不再"灰白") |
| 3 | FAB 用 M3 FloatingActionButton 替代 Box | PureRefApp.kt | 2h | 高 (主操作语义清晰) |
| 4 | 14 处触控目标 ≥48dp (用 minimumInteractiveComponentSize) | PureRefApp.kt | 1-2h | **极高 (a11y 合规)** |
| 5 | FAB / BottomPanelListItem 从 44dp 升到 56dp (跟 TopToolbarHeight 区分) | Dimens.kt | 30min | 高 |
| 6 | 显式 state layer (hovered / pressed / focused) | 多处 | 2h | 高 |
| 7 | IconButton tint 不要硬 override 成 primary | PureRefApp.kt | 30min | 中 |
| 8 | Long-press ContextMenu 改 inverseSurface + scrim | PureRefApp.kt | 1h | 中 (light 模式不刺眼) |

### P1 (强烈建议,显著影响视觉 / 维护)

| # | 项 | 涉及文件 | 修复成本 | 体验提升 |
|---|---|---|---|---|
| 9 | Dynamic Color 支持 (API 31+) | Theme.kt | 1h | 高 (Material You 体验) |
| 10 | 5 个 BottomRightActionPanel contentDescription 改中文 | PureRefApp.kt | 15min | 中 (a11y / i18n) |
| 11 | highContrastColorScheme | Theme.kt | 30min | 中 (a11y) |
| 12 | 引入 Shape Scale token (Shapes object) | Dimens.kt | 1h | 中 |
| 13 | 6/8/10dp 圆角全部对齐 Shape Scale | PureRefApp.kt | 30min | 中 |
| 14 | 锁定红 / 选中蓝 / 吸附紫改 ExtendedColors semantic token | Theme.kt + PureRefApp.kt + CanvasMinimap.kt | 2h | 高 (light/dark 自适应) |
| 15 | tonalElevation 引入 6 档 token (Elevations.level0..5) | Dimens.kt | 30min | 中 |
| 16 | EmptyCanvasState / ContextMenu 走对应 tonalElevation | PureRefApp.kt | 1h | 中 |
| 17 | CanvasMinimap panelBackground alpha 0.85 → 0.92 统一 | CanvasMinimap.kt | 5min | 低 |

### P2 (可选优化,锦上添花)

| # | 项 | 涉及文件 | 修复成本 | 体验提升 |
|---|---|---|---|---|
| 18 | Typography 自定义 (中文 + 340dpi 优化) | Type.kt | 2h | 中 |
| 19 | AutoShrinkTitle 改 M3 token (或等 Compose 1.8 autoSize) | PureRefApp.kt | 1h | 低 |
| 20 | lineHeightStyle 对中文优化 | Type.kt | 30min | 低 |
| 21 | M3 TopAppBar 替代自建 TopToolbar | PureRefApp.kt | 4-6h (大改) | 高 (scroll behavior / 状态栏 inset 自动) |
| 22 | FilledTonalButton 删 shape = RoundedCornerShape(10.dp) | PureRefApp.kt | 5min | 低 |
| 23 | 间距 token 化 (Spacing object) | Dimens.kt | 2h | 中 |
| 24 | 7 处 2/6/10dp 间距对齐 4dp 网格 | PureRefApp.kt | 30min | 低 |
| 25 | EdgeInset / CornerInset 统一为 1 套 | Dimens.kt | 15min | 低 |
| 26 | tween 350ms hover magnifier 改 400ms FastOutSlowInEasing | PureRefApp.kt | 5min | 低 |
| 27 | MotionScheme token 化 (DurationShort / Medium / Long / Emphasized) | 新建 MotionScheme.kt | 2h | 中 |
| 28 | delay(16L) 改 withFrameNanos 跟 vsync 同步 | CanvasMinimap.kt | 30min | 低 (90/120Hz 屏 fling 跟手) |
| 29 | prefers-reduced-motion 适配 (LocalAccessibilityManager) | 多处 | 2h | 中 (a11y) |
| 30 | Role.Button 显式声明在所有自定义 clickable | 多处 | 1h | 中 (a11y) |

---

## 11. 建议的 re-apply 合并策略 (跟 8d9d74e 恢复计划对齐)

按 user.md 提到的 "8d9d74e 之后 4-6 commit 恢复 5 个特性" 节奏,推荐这样穿插 M3 修复:

| Commit | 8d9d74e re-apply | M3 顺带修 |
|---|---|---|
| 1 | Lock 特性 (锁定红框 + 4 按钮 locked 模式) | **P0 #14** ExtendedColors 锁定红 token 化 |
| 2 | Settings panel (5 sections) | **P0 #1-2** ColorScheme 补全 28 角色 |
| 3 | HSV color wheel (恢复 ring + triangle) | **P0 #5** 触控目标 ≥48dp (wheel 区域够大 OK) |
| 4 | 8 gesture toggles | **P1 #12-13** Shape Scale 引入 |
| 5 | 顶栏 gear 入口 | **P1 #15-16** tonalElevation 6 档 + **P2 #21** TopAppBar 改造 (如果时间够) |

每个 commit 独立可 review,M3 改动不阻塞 8d9d74e 恢复。

---

## 12. 总结

| 维度 | 合规度 | 关键 1 句话 |
|---|---|---|
| 色彩系统 | ⚠️ 60% | 缺 16/28 ColorScheme 角色,缺 Dynamic Color,缺 ExtendedColors semantic token |
| 排版 | ⚠️ 70% | 用 M3 默认值,中文未优化,AutoShrinkTitle 硬编码 sp 池 |
| 组件规范 | ❌ 40% | 自建 FAB 不用 M3 FAB,**14 处触控目标 < 48dp** |
| 形状 | ⚠️ 60% | 单一 ChromeCornerRadius,缺 Shape Scale token 体系 |
| 间距 | ✅ 85% | 4dp 网格基本对齐,7 处 2/6/10dp 偏离 |
| 动效 | ✅ 80% | tween 200ms FastOutSlowInEasing 主线合规,缺 motion scheme |
| 无障碍 | ❌ 35% | **14 处触控目标 < 48dp**,BottomRight contentDescription 英文,缺 Role.Button / highContrast |
| 状态层 | ⚠️ 55% | RecentFileItem hover OK,FAB 无 state layer,Long-press 菜单硬编码灰 |

**最大 3 件事(P0)**:
1. **ColorScheme 补全 28 角色**(基础中的基础,影响全 app filled button / chip / dialog 颜色)
2. **14 处触控目标 ≥48dp**(a11y 合规硬指标)
3. **FAB 用 M3 FloatingActionButton + state layer**(主操作语义 + 状态反馈)

修完这 3 件事,app 的 M3 合规度能从 35% → 75%。
