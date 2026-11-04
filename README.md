# Imaginary Space Meteorite Excavation

虚数陨星采矿场，一个面向 Minecraft 1.7.10、目标整合包为 GTNH 2.9.0 正式稳定版的附属 Mod 工程。

| 配置项 | 值 |
| --- | --- |
| Project Name / Mod Name | Imaginary Space Meteorite Excavation |
| Root Project Name / Artifact Name | Imaginary-Space-Meteorite-Excavation |
| Mod ID | `imaginaryspacemeteoriteexcavation` |
| Group ID / Java Package | `com.kyou.imaginaryspacemeteoriteexcavation` |
| Mod 主类 | `ImaginarySpaceMeteoriteExcavation` |
| 资源命名空间 | `assets/imaginaryspacemeteoriteexcavation/` |

Java 源码位于 `src/main/java/com/kyou/imaginaryspacemeteoriteexcavation/`；Mod 元数据位于 `src/main/resources/mcmod.info`。

在项目根目录执行构建：

```powershell
.\gradlew.bat build
```

构建产物输出到 `build/libs/`，基础名称为 `Imaginary-Space-Meteorite-Excavation`，版本沿用现有构建版本机制。

当前[阶段 2 基础机器](docs/STAGE2_IMPLEMENTATION.md)已实现并通过完整构建与模块测试：控制器及制作配方、基础过滤书物品、IV 几何通用多方块、互斥能源仓组合和安全的三槽 GUI。实际成型与 GUI 网络行为待[用户游戏验收](docs/STAGE2_GAME_ACCEPTANCE.md)。

当前不会执行陨星配方、扣除 LP/钻井液或产出物品。期望引擎、过滤书页面和输出属于后续阶段，尚未开始阶段 3。

开发目标采用理想环境标准配方、无试剂、直接方块物品输出、整批累计期望后向下取整、最终物品过滤和过滤前钻井液计费。使用原生 128 Tick 批处理及普通/ME 输出，不自研 Long 输出。

当前规则见[技术设计](docs/STAGE1_FINAL_DESIGN.md)，阶段安排见[开发计划](docs/DEVELOPMENT_PLAN.md)，决定依据见[技术决策](docs/TECHNICAL_DECISIONS.md)。旧调查方案作为来源证据保留，实施以最新反馈和 AGENTS.md 为准。
