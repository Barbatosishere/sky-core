# 隔离专服基线：Minecraft 1.21.1 / NeoForge 21.1.265

## 输入与复现

- 整合包根为 `D:/MC/PrismLauncher_minecraft/1.21.1(1)`。协议来自该目录的 `docs/pack/performance/README.md`。专服使用验收 JAR `9c982b2dc5a0184e1efddff9bc558c49c14fc5fcd72ae21432b6334ba3ff793a`，不构建或替换产品 JAR。
- 专服模板从锁定的 265 个 JAR、726 个交付文件和专服归档 `libraries` 恢复。每轮重算模板与 1549 个 QA 世界文件，匹配后才启动；发现日志回查实际 JAR。玩家侧从步骤②的客户端模板复制 286 个 JAR，使用相同的图形选项及隔离 Prism 缓存，以已联验的离线 Quick Play 参数连接回环地址。
- `server_baseline.py` 的 `build`、`pilot`、`series` 子命令分别建立模板、未计时试跑、串行正式采样。以 `--root` 指向整合包根，`--output` 指向全新的同盘隔离目录。`run --name server-… --sequence N --attempt N [--player]` 可单独复测，无效轮次保留原目录；`csv` 生成含客户端与专服行的共享字段副本。测试运行使用 `127.0.0.1`，游戏端口 `25970`、RCON `25971`，不复制旧凭据。
- 原版采样脚本的 `net.minecraft.Util` 被这一版 KubeJS 类过滤器拒绝。隔离脚本 `98_perfticks.js` 改用 Guava `Ticker.systemTicker().read()`，仍沿用同一 `getTickTimesNanos()` 环、120 s 预热、300 s 窗口及逐刻规则；未修改类过滤器、基线发布文件或 Core。另加 `perfticksprobe` 供 2 s + 5 s 试跑；正式轮次只用 `perfticks`。候选需使用同一隔离采样脚本。
- 证据根：`D:/MC/PrismLauncher_minecraft/1.21.1(1)/perf-qa-20261125/server-baseline-v2`。`runs/<run_id>/` 保存时间与 RCON 回执、原始逐刻、JFR、GC、启动参数、错误日志、合法冷重启证据及 SHA-256。无正式数据前，不用未计时试跑汇总基线。

## 试跑与归属

- 原采样脚本失败日志：`perf-qa-20261125/server-baseline/runs/server-pilot-empty/initial/kubejs-server.log`。这是 QA 夹具不兼容，不是专服产品故障。更换时钟后，`server-baseline-v2/runs/server-pilot-empty` 取得连续 100 刻、范围 65–164，符合 5 秒探针窗口。
- 两次启动均观察到 `Done` 后首条 `list uuids` 返回延迟约 16 秒。`AlloyFurnaceRecipeCatalog` 自带计时报告，在 `Server thread` 分别花 10,521 / 10,498 ms 构建 33,181 条、36 个来源的合金炉目录。已定位的这部分归 `useless_mod` 配方目录构建；不能据此把剩余延迟、稳态 MSPT 或客户端退出卡住归因给该模组或 Sky Core。原始证据分别位于两个证据根的 `runs/server-pilot-empty/initial/latest.log`。
- 第三次启动附加独立 75 秒 JFR；在其覆盖期间，`Server thread` 的 545 次 `jdk.ExecutionSample` 中有 484 次栈包含 `AlloyFurnaceRecipeCatalog`。这是启动侧采集，不能进入稳态汇总。录制位于 `server-baseline-v2/runs/server-pilot-stone/initial/startup-pilot.jfr`。

## 单玩家工况修正

- `server-stone-b1`、`server-stone-fixed-b1`、`server-stone-tile-b1` 分别因为角色偏离精确坐标或离开固定方块而失效。不汇总这些窗口；保留原始命令、位置和日志。无法从现有证据区分游戏内碰撞、输入或其他位移，未归因给 Sky Core。
- 最终三轮都从原始快照恢复，再在槽 0 维度的工作台正上方建立相同的 QA 玻璃站位：`fill 9 103 7 11 106 9 minecraft:glass keep` 必须放置 36 块，随后 `fill 10 104 8 10 105 8 minecraft:air replace minecraft:glass` 清空 2 块。`SkyQaAlpha` 传到 `(10.5, 104, 8.5)`，监控同一方块 `(10, 104, 8)`、同一高度和 UUID。该设施只存在于隔离运行副本，未覆盖快照方块；工作台仍是 `(10, 100, 8)`。`server-pilot-enclosure-v2` 先验证站位、投料、产出和正常保存，再开始正式轮次。
- 首次投料在预热前，其后每 30 秒一次。每轮共 14 次投料和 14 次产出核验，产出为各 1 个石英、红石，无剩余输入。和候选对照时使用完全相同的站位夹具、客户端及命令序列；不要与三个无效站位样本比值。

## 正式结果：2026-11-25

各轮独立 JVM，120 秒预热、300 秒采样，最终各 6,000 个连续刻。表中单位为 ms/刻；P95 使用最近秩，末行分别取三轮中位数和全距。

| 轮次 | 空载中位数 | 空载 P95 | 单玩家中位数 | 单玩家 P95 |
| --- | ---: | ---: | ---: | ---: |
| B1 | 3.00330 | 10.87610 | 4.36550 | 11.58600 |
| B2 | 2.85175 | 9.33680 | 4.59620 | 12.17700 |
| B3 | 2.89755 | 9.23420 | 4.59235 | 11.98070 |
| 三轮中位数（全距） | 2.89755（0.15155） | 9.33680（1.64190） | 4.59235（0.23070） | 11.98070（0.59100） |

- 六轮 `save-all flush`、专服正常退出和同一世界的独立 JVM 冷重启均通过；两份 FTB Quests 数据在冷重启前后逐字节相同。单玩家三轮均在冷重启后用同一 QA UUID 重连、保留站位和两类产出；初测及冷重启的客户端均正常退出。客户端单人集成服的「保存并退出到标题」不是同一复现路径，不能据此认定已修复。
- 稳态 JFR 在采样起点后约 0.53–0.63 秒开始；样本统计只取与 300 秒逐刻窗口重叠的事件。空载重复出现 `ChunkMap` 和 XyCraft `LevelSystemBase.tick`；单玩家重复出现 XyCraft 和 `DistanceManager.runAllUpdates`。Sky Core 在各轮服务器主线程执行样本栈中占约 7.7%–9.8%，含团队天气逻辑；这是包含调用栈的抽样比例，不是独占耗时或可节省比例，未证实 Sky Core 是该工况的瓶颈。
- 空载每轮有 3–7 个刻超过 50 ms，单玩家有 18–22 个。窗口内单次 GC 暂停最大约 36.6 ms，不足以单独解释 262–433 ms 的最慢刻。空载第 6,000 刻三次均为 254–269 ms，与周期保存边界吻合，但仍需用尖峰时段的调用栈确认。手动 flush 后的 `Can't keep up!` 与稳态窗口分开记录。
- 六轮首条 `list uuids` 的往返为 15.8–17.5 秒；同段 `useless_mod` 合金炉目录构建日志为 9.824–11.272 秒。建议整合包侧先复核此启动瓶颈，任何候选不得破坏合金炉配方加载；它不解释所有启动延迟，也不证明稳态能改善 10%。
- 单玩家 B1、B3 的正式窗口内，自动备份分别于北京时间 16:30 和 17:00 失败。`initial/latest.log` 均记录 FTB Backups 打包时对 `.neoforge-tmp` 的 `NoSuchFileException`；路径分别为 AnvilCraft 维度的红石信号数据、一个团队维度的 Ender IO 管道数据。最可能是备份枚举与临时文件替换竞争，直接故障点在 FTB Backups；并非日志中的磁盘阈值拒绝，也未证实是 Sky Core 写盘缺陷。两轮 tick 流、单玩家负载和重启完整，保留整段作为包含日常备份调度的基线。若要单独比较稳态性能，候选和复测基线应匹配调度边界；若验证备份修复，需在相同调度场景连续三次验证可读备份产物，不可通过关掉备份过关。

## 交付与回退

- `server-baseline-v2/runs.csv` 合并步骤②的 6 条记录、本步骤 6 条有效记录及 3 条无效记录；原共享 CSV 不覆写。`server-audit.json` 与 `server_results.py` 可从原始逐刻和每轮证据 SHA-256 重算；`initial/` 与 `reopen/` 分别保留启动、采样和复测日志。
- 模板清单 SHA-256：`66878de974cff8fb16c96b3f95da728bce2313ff4c38f3c75d5d30481594b74f`；隔离取刻脚本：`71ebd87455036600d30663e52f9713ab3847f2d786f13ec1f6859d1d7871f6fe`；合并 CSV：`4f0cc8f4d886c755dc9f49a27fa0aab78566d368f2b06e5e2c92e63d43c590e9`；汇总审计：`b4d2b40759c3bef4167cf803112a3fb95d49cb1d9509623f3e18c49d67fe8470`。
- 本步骤无产品变更或候选。保留原快照和基线 JAR；复测从锁定输入重建隔离副本即可，不把取刻脚本或玻璃站位带回正式世界。复测前确认磁盘空间，结束时 D 盘只余约 4.4 GB；勿清理或覆盖原始证据。
