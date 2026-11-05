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

阶段 2 至 7 已完成控制器接线、开工扣费、原生耗能、完工输出、暂停和正常持久化；后续 ME 适配、[规划优化/WAILA](docs/OPTIMIZATION_WAILA.md)及[空间塔公式调整](docs/TOWER_RADIUS_RULE.md)也已完成。96 项测试、完整构建和独立 Java 8 通过。过滤书右键单本编辑，复制出的两本需先拆分。

全部待人工测试集中在[人工游戏测试总清单](docs/MANUAL_GAME_TESTS.md)，阶段 2 至 7 的开发实现及开发端验证完成后由用户统一导入游戏执行；不作为各开发阶段继续工作的前置条件。

机器生产入口已接通。开工按整批扣输入、LP 和过滤前钻井液一次，完工按快照交付；暂停保留进度，正常保存/卸载和控制器掉落保留本机状态，跳电丢弃未完成产物且不退款。普通本地输入总线/单液输入仓及核实的原生输出受支持，ME/双输入等远程输入不支持。

真实成型、GUI、完整 Forge 批处理生命周期、Soul Network、ME 网络、存档与 TPS/MSPT 尚未游戏验收。独立 Java 8 控制器测试使用与 GT 开发 JAR 匹配的原样 2.0.1 API 支持，项目依赖未改；实际整包的动态运行库须在 A-01 核对，不能以测试支持替代正式实例类加载。

输出会保留普通堵塞后的余量。ME 按原生缓存/检查模式接收：未检查模式有正余量时可接管超出默认 1600 门槛的整批，满后阻挡后续预检；接管到总线缓存不等于送达磁盘。pending 每 Tick 至多交付一次、64 种，不设搬运件数预算；保留 2^40 总容量及必要的 int 接口分片。总线内 void/overflow 配置拒绝作为安全接收端；正常存档无效或提交结果不确定时保留记录并封锁自动发货。详见[ME 输出调整](docs/ME_OUTPUT_IMPLEMENTATION.md)。

开工仅复用纯计算与不可变身份序列化，不缓存库存容量，扣费前仍实时复核。WAILA 显示结构/运行状态、进度与 EU/t、半径与塔需求、当前批次和有限产物预览、pending 与故障；空闲不显示上批产物。真实 HUD 布局、ME 网络和游戏性能仍待用户验收。

空间塔门槛为 `(2r+1) <= n/3-1`，等价于 `n >= 6(r+1)`；半径 1/32 分别需要 12/198 个塔。GUI 支持半径与 WAILA 需求已同步，实际配方仍限已审核的标准半径。

书配方不支持目标 MUI2 的示例 InventoryCraftingWrapper 合成容器。复制余量未增加持久化暂存，外部取消满包掉落可能损失余量，详见阶段 5 报告的兼容边界。

开发目标采用理想环境标准配方、无试剂、直接方块物品输出、整批累计期望后向下取整、最终物品过滤和过滤前钻井液计费。使用原生 128 Tick 批处理及普通/ME 输出，不自研 Long 输出。

当前规则见[技术设计](docs/STAGE1_FINAL_DESIGN.md)，阶段安排见[开发计划](docs/DEVELOPMENT_PLAN.md)，决定依据见[技术决策](docs/TECHNICAL_DECISIONS.md)。旧调查方案作为来源证据保留，实施以最新反馈和 AGENTS.md 为准。
