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

阶段 2 至 6 已完成开发端模块及完整构建。最新[阶段 6](docs/STAGE6_IMPLEMENTATION.md)提供原生普通/ME 输出适配、整批容量预检、同 Tick 提交和有界持久待输出；63 项总测试及实际 Java 8 运行通过。过滤书页面与配方见[阶段 5](docs/STAGE5_IMPLEMENTATION.md)，右键单本编辑，复制出的两本需先拆分。

全部待人工测试集中在[人工游戏测试总清单](docs/MANUAL_GAME_TESTS.md)，阶段 2 至 7 的开发实现及开发端验证完成后由用户统一导入游戏执行；不作为各开发阶段继续工作的前置条件。

当前机器不会执行陨星配方、扣除 LP/钻井液或产出物品。资源、过滤和输出模块尚未接入控制器；整机接线、运行期 EU 和生命周期属于尚未开始的阶段 7。原生批处理 helper 的完整 Forge 生命周期、实际注册表、ME 网络及 GUI/整机游戏行为仍未人工验收。

输出会保留普通堵塞后的余量。ME 接管表示进入原生总线缓存，按当前物理余量限制；默认缓存不足大批量时需足够普通空间或合适的原生介质容量。总线内 void/overflow 配置拒绝作为安全接收端；正常存档无效或提交结果不确定时保留记录并封锁自动发货。

书配方不支持目标 MUI2 的示例 InventoryCraftingWrapper 合成容器。复制余量未增加持久化暂存，外部取消满包掉落可能损失余量，详见阶段 5 报告的兼容边界。

开发目标采用理想环境标准配方、无试剂、直接方块物品输出、整批累计期望后向下取整、最终物品过滤和过滤前钻井液计费。使用原生 128 Tick 批处理及普通/ME 输出，不自研 Long 输出。

当前规则见[技术设计](docs/STAGE1_FINAL_DESIGN.md)，阶段安排见[开发计划](docs/DEVELOPMENT_PLAN.md)，决定依据见[技术决策](docs/TECHNICAL_DECISIONS.md)。旧调查方案作为来源证据保留，实施以最新反馈和 AGENTS.md 为准。
