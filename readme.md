# Crosshair Bobbing Remake — 26.1.2 Fabric 移植

把 1.21.1 的 **Crosshair Bobbing**（准星跟随视角摇晃）移植到 **Minecraft 26.1.2 / Fabric**，
并保证与 **DynamicCrosshair 9.12+26.1-fabric** 同时安装时两者互不干扰。

* 上游工程：`CrosshairBobbingMod-Remake-1.21.1-fabric-1.21.1`（包名 `krash220.xbob`，GPL-3.0）
* 目标版本：Minecraft `26.1.2`，Fabric Loader `>=0.18.4`，Fabric API，Java `25`
* 产物：`build/libs/xbob-2.0.0+26.1.2.jar`（client-only）

## 1. 构建

```bash
# 需要 JDK 25 与 Gradle 9.8+（Loom 1.19.3 要求 Gradle >= 9.8）
export JAVA_HOME=/path/to/jdk-25
gradle build            # 或 ./gradlew build
```

本仓库的 `gradle.properties` 里有两个关键开关：

```properties
# 26.1+ 的 Minecraft 不再提供 yarn/mojang mappings，Loom 必须关闭混淆处理
fabric.loom.disableObfuscation=true
org.gradle.vfs.watch=false
```

`build.gradle` 里额外把命名后的客户端 jar 直接挂到编译期 classpath：

```groovy
def minecraftClientJar = file("${gradle.gradleUserHomeDir}/caches/fabric-loom/${project.minecraft_version}/minecraft-client.jar")
dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    implementation files(minecraftClientJar)   // disableObfuscation 时 Loom 生成的 merged jar 是空壳
    implementation "net.fabricmc:fabric-loader:${project.loader_version}"
    implementation "net.fabricmc.fabric-api:fabric-api:${project.fabric_version}"
    compileOnly "com.terraformersmc:modmenu:${project.modmenu_version}"
}
```

## 2. 相对 1.21.1 的移植改动

| 1.21.1 | 26.1.2 |
| --- | --- |
| `InGameHud.renderCrosshair(DrawContext, RenderTickCounter)` | `Gui.extractCrosshair(GuiGraphicsExtractor, DeltaTracker)`（`GuiMixin` 注入 HEAD/TAIL） |
| `MatrixStack` / `DrawContext.getMatrices()` | `GuiGraphicsExtractor.pose()` → `org.joml.Matrix3x2fStack`（二维仿射，`pushMatrix/translate/rotate/scale/popMatrix`） |
| 渲染时直接用当前矩阵 | 每次 `blitSprite` 提交时快照 `new Matrix3x2f(pose)`，因此准星 sprite 会继承我们压入的变换 |
| `GameRenderer.getFov` / `getBasicProjectionMatrix` | `CameraRenderState.projectionMatrix` + `hudFov` 透视（`0.05f ~ 100f`，aspect = 窗口宽/高） |
| `GameRenderer.tiltViewWhenHurt/bobView(MatrixStack)` | `@Invoker bobHurt/bobView(CameraRenderState, PoseStack)`（26.1 的签名） |
| `Options.getDistortionEffectScale/getNauseaIntensity` | `OptionsRenderState.screenEffectScale`、`LocalPlayer.oPortalEffectIntensity/portalEffectIntensity`、`getEffectBlendFactor(MobEffects.NAUSEA, partial)`、`spinningEffectTime/Speed` |
| `EntityPose` / `isUsingRiptide` / `getItemUseTimeLeft` | `Pose` / `isAutoSpinAttack() + autoSpinAttackTicks` / `getUseItemRemainingTicks()` |
| `HeldItemRenderer.equipProgressMainHand` | `ItemInHandRenderer.mainHandHeight / oMainHandHeight / mainHandItem` |
| `UseAction`、`Item.getUseAction` | `ItemUseAnimation`（新增 `SPEAR`）、`ItemStack.getUseDuration(LivingEntity)` / `getUseAnimation()` |
| `Screen.render(DrawContext, …)` | `Screen.extractRenderState(GuiGraphicsExtractor, mouseX, mouseY, partialTick)`；背景由 `extractRenderStateWithTooltipAndSubtitles` 负责（自己再调 `extractBackground` 会抛 `Can only blur once per frame`） |
| `ButtonWidget.builder(...).dimensions(...)` | `Button.builder(...).bounds(x, y, w, h).build()` + `addRenderableWidget` |
| 文字颜色 `0xFFFFFF` | 26.1 文字颜色是 **ARGB**，必须写 `0xFFFFFFFF`（否则 alpha=0，直接看不见） |
| `damage(...)` | `Entity.hurtOrSimulate(DamageSource, float)`（客户端安全的伤害模拟，`EntityHurtMixin` 注入 HEAD） |
| `ClientPlayerInteractionManager.breakBlock` | `MultiPlayerGameMode.destroyBlock(BlockPos)` |

准星变换仍然完全在 CPU 上算：用 `org.joml.Matrix4f` 复制 HUD 投影 → `bobHurt` → `bobView` → 反胃/传送门扭曲 →
CameraOverhaul（可选，反射）→ 手持物品切换位移，然后把屏幕中心点的世界坐标 `(0,0,-dist)` 与 `(0,1,-dist)`
投影回 NDC 得到 `offsetX/offsetY` 与倾斜角 `angle`，最后以
`translate(center) → translate(offset) → rotate(angle) → scale(scale, scale*scaleBow) → translate(-center)`
的顺序应用到 `pose()` 上。

## 3. 与 DynamicCrosshair 9.12+26.1 的兼容

DynamicCrosshair 在 26.1 里依然是替换 `Gui.extractCrosshair` 内那次 `GuiGraphicsExtractor.blitSprite` 调用
（`@WrapOperation`），也就是说它画出的准星同样走我们的 pose：

1. **默认配置**（`fixCenteredCrosshair = false`）：DC 用原版坐标绘制，自动继承晃动位移 + 倾斜，无需额外处理。
2. **开启 "fix centered crosshair"**（`dynamiccrosshair.json5` 里 `"fixCenteredCrosshair": true`）：
   DC 会执行 `pose.setTranslation(dx, dy)` —— 绝对位移会把晃动整个抹掉。
   本模组用 `@Pseudo @Mixin(targets = "mod.crend.dynamiccrosshair.render.CrosshairRenderer")`
   拦截 `fixCenteredCrosshairPre`，把绝对 `setTranslation` 换成相对 `translate`（见 `DynamicCrosshairCompat.applyRelativeCenter`），
   于是“居中修正”与“准星晃动”可以同时生效。
3. 未安装 DC 时（`FabricLoader.isModLoaded("dynamiccrosshair") == false`）以上逻辑全部不走，回落到原版准星，已实测无异常。

## 4. 运行时自动化验证（Fabric client gametest）

`src/main/java/krash220/xbob/gametest/BobbingClientGameTest.java` 是一个 Fabric 客户端 gametest：
创建单人世界 → 低头看地面（让屏幕中心点距离足够近，位移可测量）→ 记录站立/行走/关闭 `bob` 三个状态的
准星偏移与倾角 → 截图 → 打开 ModMenu 配置界面截图。

```bash
# dev 环境启动（需要把 LWJGL natives 一起放进 classpath）
java -Dfabric.development=true -Dfabric.side=client -Dfabric.gameVersion=26.1.2 \
     -Dfabric.client.gametest=true -Dfabric.client.gametest.modid=xbob \
     -Dfabric.client.gametest.test=BobbingClientGameTest \
     -cp "<compileClasspath>;build/libs/xbob-2.0.0+26.1.2.jar;<dynamiccrosshair jar>;<lwjgl natives>" \
     net.fabricmc.loader.impl.launch.knot.KnotClient \
     --version 26.1.2 --gameDir ./run --assetsDir ./run/assets --username Dev \
     --uuid 00000000000000000000000000000000 --accessToken 0
```

实测结果（GUI scale = 2，截图 854×480）：

| 场景 | 准星像素重心相对屏幕中心 | 说明 |
| --- | --- | --- |
| 站立不动 | (-0.5, -1.5) | 居中 |
| 行走（bob 开启） | (+4.5, +14.5) | = GUI 空间 (+2.3, +7.3)，与内部计算的 offset (2.34, -7.91) 一致 |
| 行走（bob 关闭） | (-0.5, -1.5) | 完全回到中心 |
| DC "fix centered" 开启 | 钩子命中 187 次，位移同上 | 兼容路径生效且画面不漂移 |
| 未安装 DC | 偏移一致（68 个白色像素 = 原版准星） | 回落正常 |

截图见 `run/screenshots/`（`xbob-1-idle`、`xbob-2-walking`、`xbob-4-walking-nobob`、`xbob-3-config`）。

## 5. 目录

```
src/main/java/krash220/xbob/
├── MainMod.java                     # 准星变换主逻辑（pose + Matrix4f 投影）
├── game/api/
│   ├── Config.java                  # xbob.json + ModMenu 配置界面
│   ├── DynamicCrosshairCompat.java  # DC 兼容层（相对居中）
│   ├── Player.java                  # 潜行/挥动/拉弓/切物品等进度量
│   ├── Render.java                  # HUD 投影矩阵、bobHurt/bobView、扭曲、准星中心深度
│   ├── Loader.java / Logger.java
│   └── bus/{GuiBus,PlayerBus}.java
├── mixin/
│   ├── GuiMixin.java                # Gui.extractCrosshair HEAD/TAIL → GuiBus
│   ├── EntityHurtMixin.java         # Entity.hurtOrSimulate → PlayerBus.onAttack
│   ├── MultiPlayerGameModeMixin.java# destroyBlock → PlayerBus.onBreakBlock
│   ├── GameRendererAccessor.java    # bobHurt/bobView/spinningEffect*
│   ├── CameraAccessor.java          # eyeHeight / eyeHeightOld
│   ├── ItemInHandRendererAccessor.java
│   ├── LivingEntityAccessor.java    # autoSpinAttackTicks
│   └── DynamicCrosshairCompatMixin.java
├── platform/FabricLoader.java       # ClientModInitializer
└── gametest/BobbingClientGameTest.java
```

配置文件：`config/xbob.json`，8 个布尔开关（`bob/sneak/item/aim/spear/xbow/eat/atk`，默认全开），
可在 ModMenu 中可视化修改。

## 6. 许可

沿用上游 GPL-3.0（见 `LICENSE`）。
