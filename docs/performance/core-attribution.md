# Sky Core 性能与稳定性归因

## 步骤⑤结论

2026-11-25 完成原始证据复核，选择沿用已验收的 `skycore-0.2.1-neoforge-1.21.1.jar`。SHA-256 为 `9c982b2dc5a0184e1efddff9bc558c49c14fc5fcd72ae21432b6334ba3ff793a`。

本轮新增归因报告、审计数据、复算脚本与交付文件哈希清单。产品源码与构建配置在审计前后逐文件一致，记录覆盖 157 个文件。当前工作树已有的功能修改保留在原处，审计 JSON 记录其文件哈希与已有差异摘要。该工作树与已验收 JAR 之间的构建对应关系仍需独立验证。

基线证据支持合金炉目录构建和自动备份故障的组件定位。Core 天气及熵逻辑出现在稳态抽样中，目前缺少满足本轮验收门槛的改动收益证据。集成服退出卡住的触发组件仍待定位。因此本步交付归因记录与基线制品引用。候选 JAR、产品源码补丁、针对候选的 GameTest 和 B/C 运行均未生成或执行。

## 实物与证据核验

整合包根为 `D:/MC/PrismLauncher_minecraft/1.21.1(1)`，下文证据路径均相对此目录。Minecraft 1.21.1、NeoForge 21.1.265、Microsoft OpenJDK 21.0.7+6-LTS 沿用步骤①锁定输入。此次工作读取既有记录，游戏启动及计时采样仍以步骤②和③的独立 JVM 为准。

- 从 `six-chapter-release-preflight-20261124/mods-by-side.csv` 读取锁定清单，重新计算隔离客户端模板 286 个 JAR、专服模板 265 个 JAR 的 SHA-256，两端集合、数量和每个摘要全部匹配。
- 两端模板中的 Core 实物均匹配上述完整摘要。模板路径分别为 `perf-qa-20261125/client-template/minecraft/mods` 和 `perf-qa-20261125/server-baseline-v2/template/mods`。
- 客户端 `client-b2`、`client-b4`、`client-b6` 每轮的 23 项证据重新计算哈希，并核验实际发现的 286 个 JAR 清单。
- 调用既有 `server_results.summarize`，重核逐轮证据清单、逐刻 CSV 与原始取刻日志、保存退出、独立 JVM 冷重启和任务持久化记录。复算结果与 `server-audit.json` 全同，保留原来的无效轮次。六轮有效记录共 36,000 个连续刻，初测和冷重启发现的 265 个 JAR 均与锁定清单全同。
- 使用锁定 QA JDK 中的 `jfr.exe` 重解析六份稳态 JFR。各轮只计入原 300 秒窗口重叠部分的服务端主线程 `jdk.ExecutionSample`，栈深固定 64。重算总样本和包含 Core 的样本数与原热点报告逐项一致。独立启动 JFR 另计。

复核过程成功结束。结构化结果见同目录 `core-attribution-audit.json`，其中保存实际路径、输入摘要、Core 方法分布、超时线程信息、原基线指标和源码状态。

## 稳态 Core 调用分布

| 有效轮次 | 主线程执行样本 | 含 Core 的样本 | Core 栈顶样本 | 含 JourneyWorlds.weather 的样本 |
| --- | ---: | ---: | ---: | ---: |
| server-empty-b1 | 1792 | 158 | 11 | 108 |
| server-empty-b2 | 1770 | 147 | 6 | 94 |
| server-empty-b3 | 1708 | 168 | 7 | 104 |
| server-stone-enclosure-b1 | 1992 | 184 | 9 | 100 |
| server-stone-enclosure-b2 | 2342 | 207 | 6 | 100 |
| server-stone-enclosure-b3 | 2285 | 177 | 5 | 101 |

含 Core 的样本占各轮约 7.75%–9.84%。天气路径下可见 `java.util.regex.Pattern` 编译、匹配与 owner 查找。熵 tick 路径下可见爆炸调度相关的映射操作，单玩家轮次还出现植物及区块查询。当前源码的 `JourneyWorlds.slot` 使用 `String.matches`，与重复正则编译这一待研究方向相符。这里记录的方法分布直接来自基线 JAR 的运行栈。

抽样范围包含下游调用，方法间也有重叠，收益评估需要独立的 B/C 实测。此表不足以推算可以节省的 MSPT，也不足以为任何天气、熵或存档语义修改提供 10% 改善结论。

重核的基线仍为以下结果，每格分别列出三轮中位结果及全距，单位 ms/刻。

| 工况 | MSPT 中位数 | MSPT P95 |
| --- | ---: | ---: |
| 空载 | 2.89755 / 0.15155 | 9.33680 / 1.64190 |
| 单玩家石质工作台 | 4.59235 / 0.23070 | 11.98070 / 0.59100 |

## 故障及启动归属

- 合金炉启动热点在 `perf-qa-20261125/server-baseline-v2/runs/server-pilot-stone/initial/startup-pilot.jfr` 中复核为 545 个主线程执行样本，其中 484 个包含 `AlloyFurnaceRecipeCatalog`，含 Core 的样本为 0。同段日志归属 `useless_mod`。六轮有效专服启动日志的目录构建耗时为 9.824–11.272 秒。该证据覆盖目录构建阶段，其余启动时间继续按原日志分段研究。
- 自动备份错误可回查 `server-stone-enclosure-b1/initial/latest.log` 第 3639 行和 `server-stone-enclosure-b3/initial/latest.log` 第 3685 行，均位于上述 `server-baseline-v2/runs` 下。故障为 FTB Backups 读取已消失的 `.neoforge-tmp`。相关数据文件分别属于 AnvilCraft 红石信号与 Ender IO 管道。第二份文件位于 Core 团队维度，路径中的维度命名仅表明存储位置。现有调用栈尚未建立 Core 写盘缺陷的因果链。
- 三轮正式客户端各有两份 `save-threads-*.txt`。六份完整线程转储中的 Core 栈帧数均为 0。渲染线程均在 `Minecraft.disconnect` 内等待 GLFW 事件，集成服务端线程均处于 `TIMED_WAITING`，栈包含 `MinecraftServer.waitUntilNextTick`。三轮退出均由采集器超时强制结束，退出码 124。这些离散快照保留了卡住时的执行位置，Core 因果关系仍待更细时间线或隔离复现确认。

原始客户端超时证据位于 `perf-qa-20261125/runs/client-b2`、`client-b4`、`client-b6`。专服正常保存及冷重启的通过范围沿用步骤③。集成服保存退出和自动备份故障继续作为步骤⑥的待验证项。

## 步骤⑥输入与回退

两端 Core 继续使用上述模板中的锁定 JAR，整包侧候选可在此 Core 上进行独立对照。每轮启动前核验 Core 的完整 SHA-256 和声明的模组差异，并保留实际发现清单。单玩家专服沿用原隔离玻璃站位 `(10.5, 104, 8.5)`、相同投料序列及取刻脚本，备份调度边界按共享协议匹配。

回退点为锁定模板、原始 QA 快照和上述 Core 摘要。本步产品文件改动数为 0。后续运行副本替换过 Core 时，从任一已复核模板恢复此 JAR，再核验摘要和加载日志。世界恢复使用步骤①锁定的快照及其 1549 项清单。相关回退仅作用于隔离副本。

当前待验证范围包括集成服退出的触发组件、自动备份产物的可读性、Core 在更高机器负载下的表现，以及任何未来候选的关联玩法回归。本步结果用于固定核心侧输入，整包候选收益与全局验收由步骤⑥实测判定。

## 复算入口

在本仓库运行以下命令可重核原始证据并生成新的审计文件。脚本会读取模板 JAR、完整证据与 JFR，运行期间保持相关输入和产品源码固定。

```powershell
python docs/performance/core_attribution.py --pack 'D:/MC/PrismLauncher_minecraft/1.21.1(1)' --output "$env:TEMP/core-attribution-audit.json"
```

`core-attribution.sha256` 记录本报告、复算脚本和本次审计 JSON 的 SHA-256。审计 JSON 的生成时间字段随复算更新，交付核对以本次清单中的文件字节为准。
