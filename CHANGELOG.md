# 更新日志

## 1.9.1 (NeoForge 1.21.1) - 2026-10-17

### 修复

- **修复整合包中动态准星（DynamicCrosshair）的准星不摇晃、而攻击指示器摇晃的问题。**
  在大型整合包中（DynamicCrosshair 9.x + libbamboo 2.25，同时装有 Sodium / Iris / ImmediatelyFast
  / ShoulderSurfing 等 HUD 相关模组），xbob 原先使用的“接管 DynamicCrosshair 的绘制、再对其
  libbamboo 帧缓冲整体做变换”这一方式会失效，结果准星保持静止，而攻击指示器照常摇晃。
  现在默认改为**整层变换**：xbob 不再接管 DynamicCrosshair 的绘制，而是变换整个
  `minecraft:crosshair` HUD 图层的位姿（`PoseStack`），DynamicCrosshair 的准星与攻击指示器由
  同一个变换驱动，因此**两者一起摇晃**。
  - 旧的“接管绘制”方式仍保留，可用启动参数 `-Dxbob.dynamiccrosshair.layerPose=false`，
    或配置文件 `config/xbob.json` 中的 `dynamicCrosshairLayerPose`（默认 `true`）切换回去，
    用于与 DynamicCrosshair 单独搭配时的排查。

### 新增

- **越肩视角（ShoulderSurfing 5.x）兼容选项 `shouldersurfingCrosshair`（默认关闭）**：
  ShoulderSurfing 会在自己的 HUD 图层（`shouldersurfing:post_crosshair`）里绘制遮挡指示标记，
  该绘制不属于原版准星代码路径。开启后 xbob 的摇晃变换也会覆盖该图层，标记随准星一起摇晃。
- **内置诊断日志（默认关闭）**：启动参数 `-Dxbob.debug=true` 开启，日志前缀 `[xbob-debug]`，
  用 `grep xbob-debug logs/latest.log` 即可过滤。用于反馈整合包中的准星/HUD 问题时附带日志。
  - `-Dxbob.debug.verbose=true`：输出逐帧/逐次绘制细节
  - `-Dxbob.debug.interval=<秒>`：汇总输出间隔，默认 5 秒
  - `-Dxbob.debug.sprites=true|false`：是否追踪准星相关贴图绘制，默认开
  - `-Dxbob.debug.bobScale=<倍数>`：放大摇晃偏移，便于肉眼确认某个 HUD 元素是否落在变换内

### 变更

- 版本号：`1.9fix` → `1.9.1`
- 配置界面新增条目：动态准星层级变换、越肩视角准星同步摇晃（中/英文本地化）

### 兼容性

- Minecraft 1.21.1 / NeoForge 21.1.x，客户端模组
- DynamicCrosshair 9.x（自带 libbamboo 2.25）
- 已在包含 Sodium / Iris / ImmediatelyFast / ShoulderSurfing 等模组的大型整合包中验证

## 1.9fix (NeoForge 1.21.1) - 2026-09-25

- 新增 DynamicCrosshair 兼容：准星与攻击指示器一起摇晃，并修复重复变换问题。

## 1.9 (NeoForge 1.21.1) - 2026-08-15

- 首个 NeoForge 1.21.1 移植版本。
