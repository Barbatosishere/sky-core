# 第⑥步：熵增与庇护核心正式发布验证

本步已完成 Sky Core 0.1.1 核心正式发布，未修改整合包目录、未安装至原实例、未使用玩家正式存档。第④步核心只读复审与第⑤步整合包只读复审均已通过；本轮以已复审源码为基线更新版本并重新构建，JUnit 12/12、独立 seed/verify JVM 真实 GameTest 各 20/20，正常保存重启及发布核验全部通过。原实例全模组联调仍须第⑦～⑧步完成。

## 制品与兼容

目标环境仍为 Minecraft 1.21.1、NeoForge 21.1.265、Java 21。正式制品为 `dist/entropy-core-step-6/skycore-0.1.1-neoforge-1.21.1.jar`，交付清单为同目录 `verification.json` 与 `SHA256SUMS`。正式 JAR 为 142232 字节，SHA-256 为 `db8a9ea155ca458e20aa2be99abf7c1d16d65b7b3dc9ee5bba03d88dc9e1da69`，元数据版本为 0.1.1。`SHA256SUMS` 覆盖发布目录中除其自身以外的全部交付文件，包含 `verification.json`；JSON 内的证据摘要排除自身及 `SHA256SUMS`，避免循环引用。

注册 ID、公共 API、配方格式及存档 schema 均不改变。共用契约 `contracts/entropy-shelter.v1.json` 保持原字节，SHA-256 为 `a6e4740169a24c1232fed04e17ab8f3ad9eb66b5d412f6113a18505df5d22076`；发布目录交付相同内容的契约副本。旧 `skycore_teams.dat`、新 `skycore_entropy.dat` 及只读快照仍使用各自 schema 1，旧永久里程碑不迁移为新生产或供养证据。

原正式 JAR `dist/skycore-0.1.0-neoforge-1.21.1.jar` 和第②步审查预览包均保留。原正式包 SHA-256 为 `4f1813259d32b5f9919a100a4a50e7091a58586a64944e1c4c29889befe7b67d`；预览包为 `67b53ff6f5e4f8fd55f083ae8f73d5012cee6752f9bdc463dc39104a5b8d3b51`。本目录没有 Git 元数据，交付以源码及证据文件 SHA-256 清单为准，不提供虚构提交记录。

## 审查问题与本轮验证

第④步确认三项修复闭环：生产调度按到期尝试推进公平游标；双端交互放行约定动作并经真实 `gameMode.useItemOn` 验证单次执行和跨队拒绝；跨维度夹具保证完整加载范围及有效候选，保留真实爆炸、玻璃破坏断言与原工作预算。详情和历史失败证据保留在[第②步修复记录](entropy-core-step-2-review-1.md)，本轮不删除历史失败记录。

QA 仍使用 `D:\MC\skycore-build\gametest` 下的独立核心测试存档，构建输出位于 `D:\MC\skycore-build\build`，均不是新增 Prism 实例。实际命令如下：

```powershell
.\gradlew.bat test compileGameTestJava build --no-daemon --rerun-tasks
.\gradlew.bat runGameTestServer --no-daemon -Pentropy_restart_phase=seed
.\gradlew.bat runGameTestServer --no-daemon -Pentropy_restart_phase=verify
python tools/verify_entropy_step_6.py
```

| 检查 | 本轮结果与证据（发布目录内） |
| --- | --- |
| 重新编译、单元测试及发布构建 | 通过，退出码 0，7 项任务实际执行；JUnit 12/12，失败/错误/跳过均 0，XML 时间为 2026-11-17T14:22:14Z；`gradle-test-build.log`、`TEST-*.xml` |
| seed 独立 JVM，完整真实服务端 GameTest | 20/20 必测通过，正常保存退出，Gradle 退出码 0；`gradle-seed.log`、`gametest-seed.log` |
| 正常保存退出后，新 JVM 同档 verify 完整套件 | 20/20 必测通过，正常保存退出，Gradle 退出码 0；`gradle-verify.log`、`gametest-verify.log` |
| 跨 JVM 恢复机器、燃料、开关、初始化、证据及加载前保护索引 | 通过，seed/final 的 owner、inverse、entropy、pos 一致，`verified_after_jvm_restart=true`；`restart-proof.json` |
| 新旧契约及实际 QA 配置默认值 | 全部通过；`contract-checks.log`、`skycore-server.toml` |
| 资源生成一致性 | 隔离临时目录生成的 54 个资源逐项一致，未重写源码资源；`resource-regeneration.log`、`verification.json` |
| JAR 结构、版本、JSON/TOML、Java major 65、源码及构建输出一致性 | 全部通过；56 个 class 与本轮编译及已审查预览逐项一致，资源与源码一致，无重复 ZIP 项、QA 内容、配方或嵌入依赖泄漏；`verification.json` |
| 原正式包、预览包与共用契约原样保留 | 全部通过；`verification.json`、`SHA256SUMS` |
| 原实例相关文件未改动 | 通过；`pack-files-before.json`、`pack-files-after.json` 的 1346 个相关文件 SHA-256 完全一致 |

验收门槛为 JUnit 12/12 且失败、错误、跳过均为 0，两次真实 GameTest 各 20/20 且正常保存退出。每轮均保留原始日志；`UP-TO-DATE` 不另计作单元测试实际执行。GameTest 的服务端加速 tick 和日志墙钟时长不作为正常服务器负载性能结果。

行为覆盖沿用已复审的完整套件：保护边界与完整高度、重叠与移除、燃料耗尽与离线/卸载时钟、双产物各自堵塞及原子提交、动植物与玩家危险、爆炸 B 和抗熵石、双队权限、schema 及只读查询、幂等初始化、首章至实际供燃料、跨维度共享预算及默认周期公平性。恢复探针在机器区块加载前核验持久化索引，随后核对方块实体链接；探针直接设置的燃料与证据仅用于持久化夹具，真实获取另由加工及投料测试验证。探针、QA 配方和结构不得进入正式 JAR。

## 默认规则与后续交接

- `naturalDimensions=[]`，自然熵增默认不启用任何维度。无燃料逆熵机覆盖本区块完整建造高度（半径 0），真实供燃料后默认覆盖以机器所在区块为中心的 3×3 区块（半径 1）；重叠取并集，逆熵保护优先。
- 世界燃料每份维持 1200 个所属队伍至少一人在线的有效服务端 tick；全队离线暂停耗料并保留覆盖，停机不补算，耗尽只收缩该机器的扩展范围。机械熵增机默认关闭、影响本区块，由本队成员开启，所属队伍离线暂停危险执行。
- 每个启用维度自然与机械熵增合计每 1200 tick 最多一次爆炸，强度默认 4；全维度共享每 tick 1 次爆炸、64 候选、128 生物、256 植物检查预算，不强制加载或全世界扫描，不补发积压。
- 玩家危险预警 100 tick（5 秒），之后每 20 tick 受到 1 点生命伤害，离开危险区清除计时。用户确认 B：预警内免受熵增爆炸直接伤害，预警结束后同时承受熵增爆炸及持续伤害。未庇护区动植物在有界处理器检查到时立即死亡或移除。

第⑦步须按交付摘要备份并安装新 JAR：将旧 Sky Core JAR 移出 `mods` 并保留在备份中，`mods` 内只保留一个提供 `skycore` 注册的 JAR，不得同时保留新旧版本；安装后逐份核对客户端和专服的新 JAR SHA-256 与本交付一致。随后使用原实例全模组与独立 QA 存档实际验证专服、图形客户端、JEI、真实 FTB 生产/供养任务同步、双队操作、首章至扩大庇护及重启联调；第⑧步只读复审最终安装内容和验收证据。本步的核心 GameTest 不能替代这些验收。

启用顺序为：备份原实例和正式存档 → 按摘要安装发布包 → 独立 QA 专服与客户端验收 → 明确开局维度并为各队幂等初始化庇护 → 配置该维度启用自然熵增。不得先对现有维度普遍开启。回滚须恢复匹配的完整存档、JAR、脚本与配置备份，不只删除新 JAR。

生态衰退执行器与气候设施仍未实现，快照保持 `ecology_implemented=false`；10/30/60 分钟在线衰退规则是保留约束，不作为已完成功能。20–40 人负载、操作系统断电和跨文件事务未验收。独立队伍世界、64 槽、初始平台生成、门户与跨队贸易仍属后续范围。
