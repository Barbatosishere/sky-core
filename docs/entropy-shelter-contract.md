# 熵增与庇护共用契约 v1

第①步固定接口、注册入口、服务器配置及骨架；第②步已补齐运行后端、真实燃料与证据、危险执行、初始化和版本化存档。运行层、队伍或配置不可用时，仍返回 false/null/NOT_READY；可用时提供真实状态。核心验证见 [第②步记录](entropy-core-step-2-verification.md)。自然熵增默认关闭，正式发布及原实例全模组客户端/专服验收仍在第⑥～⑧步，不能将核心测试视为整合包上线验收。

机器可读清单及三条完整配方示例以 [entropy-shelter.v1.json](../contracts/entropy-shelter.v1.json) 为准。本契约补充 [首章 v1](chapter-1-contract.md)，不替换其 ID、六个里程碑、recipe codec 或快照。Minecraft 1.21.1、NeoForge 21.1.265、Java 21 不变。

## 注册、配方与文件归属

| ID | 用途 |
| --- | --- |
| `skycore:mechanical_anti_entropy_field` | 机械逆熵力场，方块、同名物品与方块实体 |
| `skycore:mechanical_entropy_field` | 机械熵增力场，方块、同名物品与方块实体 |
| `skycore:world_fuel` | 独立物品；原初燃料仍仅承担既有加工热源用途 |
| `skycore:anti_entropy_stone` | 抗熵石方块及同名物品；世界燃料加工的必有副产物，抵抗熵增爆炸 |

自然熵增是环境，不注册第五个物品。新增注册在 `EntropyContent`，由现有 `CoreContent.register` 唯一接入；整合包不得重复注册。当前原型模型复用原版纹理，生成源与资源一同维护。

正式配方由整合包发布。固定 recipe ID 为 `skycore:entropy/world_fuel`、`skycore:entropy/mechanical_anti_entropy_field`、`skycore:entropy/mechanical_entropy_field`。复用 `skycore:stone_processing`，计数 Ingredient、结果 `id/count`、两个输出槽与原子提交语义不变；任一产物堵塞时整批暂停，不扣输入/热源、不产生证据。world_fuel 配方必须同时产出正数量世界燃料及抗熵石；不能拆成两条概率配方。核心第②步须验证配方 ID 与实际提交的双产物，不能只认配方名称。

清单的数量/时长为可调默认值。逆熵机用 2×2 背包配方，碎石×2、残渣×1、原初燃料×1；这同时是重建路线，不另设免费恢复。它不依赖木、水、电、凝露或既有生命。世界燃料经石质工作台使用岩核可再生物及首章矿物；抗熵石不作为重建逆熵机的前置。首章七条旧配方及里程碑门槛保持兼容。

| 责任成员 | 可写文件（各自根目录下） |
| --- | --- |
| Sky Core，第②/⑥步 | `E:\mods\Sky Core` 下源码、资源、公共契约、核心 QA fixtures、构建工具、测试、发布 JAR 与证据 |
| 整合包目录，第③/⑦步 | 原实例 `D:\MC\PrismLauncher_minecraft\1.21.1(1)` 下 KubeJS、FTB Quests、配置/defaultconfigs、`docs/pack`、生成源与工具、备份安装记录 |
| 代码审查，第④/⑤/⑧步 | 只读；向原责任成员交付问题，责任成员修复后复审 |

整合包侧从本清单读取/复制配方到自己的生成源，核心侧只读取实例。第③步不得修改核心的 `tools/check_pack_contract.py` 或 fixture；相关核心兼容检查由第②/⑥步维护。第③步不等待行为实现即可制作内容，但需等第⑥步发布 JAR 后安装验证。此目录无 `.git`，本步“提交”指文件及频道契约交付，不虚构 Git commit。

## 场、危险与有效时间

- 坐标键为维度 ID 与 `floorDiv(x,16)/floorDiv(z,16)`；覆盖区块完整建造高度。半径按区块切比雪夫距离计，包含边界；无燃料固定半径 0，即机器所在 16×16 区块；有燃料默认半径 1，即以机器**所在区块**为中心的 3×3 区块，不以方块位置计算 48×48 浮动区域。
- 所有队伍逆熵保护取空间并集，保护优先于自然/机械熵增。`hazardous=(natural_entropy || mechanical_entropy) && !protected`。机器归属限制交互，不限制覆盖区内谁受到保护。耗尽只收缩本机扩展，强制移除只删除本机 UUID 的贡献，不动重叠保护；重建后重新建索引。逆熵机生存采掘、普通爆炸、活塞不能移除；命令/创造等强制替换也须及时撤场。
- 机械熵增默认关闭、范围本区块；本队当前成员即授权成员，空主手潜行右键开启/关闭，副手可持物。逻辑双端在 RightClickBlock 阶段允许该动作进入方块路径，后端只在逻辑服务端执行一次并核对当前队伍。不转移旧队资产；跨队使用、投料和切换均拒绝。不因红石信号或客户端数据启用。逆熵机不需要开关、FE 或额外热源。
- 本队成员主手持世界燃料右键逆熵机，每次成功扣 1 个并增加配置的燃料 tick；容量不足则整次拒绝。背包物品、剩余 tick、持久化脏标记及真实证据在同一服务端提交后更新；模拟、拒绝、跨队、物品检测或任务奖励都不计。默认存量上限 76800 tick（64 份）；改低容量不删除已接受的时间，存量降至可接收前拒绝新投料。
- 燃料时间以所属队伍任一成员在线的服务端 tick 累计，每队多人不加倍；机器区块卸载仍按持久记录扣时，不强制加载。全队离线冻结并保留覆盖；服务器停机不补算现实时间。重启后先恢复索引，再处理已加载区域危险。
- 未庇护危险区动植物即死；实体默认覆盖 `Mob`（包括村民、水生物），玩家单独处理，物品实体/盔甲架不按生物即死。植物按可扩展方块标签 `skycore:entropy_vulnerable_plants` 识别，核心第②步填充树木、树苗、作物、花草等默认条目，整合包可补充模组植物。即时指对象被已加载区域有界处理器检查时立即执行，无危险计时宽限；不能为此扫描整个世界。
- 生存/冒险玩家持续处于危险区先预警 100 tick，第 100 tick 开始每 20 tick 受到 1 点生命伤害；离开危险区立即清除预警/伤害计时，重新进入重新预警，创造/旁观不受此伤害。伤害、预警由服务端判定。爆炸中心虽在保护外，也不得通过波及列表破坏保护内方块、杀伤保护内生命；需要在爆炸影响阶段重新过滤。抗熵石不被熵增爆炸的专用破坏路径绕过耐爆规则。
- 用户追加确认 B：预警期内免受自然/机械熵增爆炸的直接伤害；预警结束后同时承受熵增爆炸与持续伤害。不额外豁免普通爆炸。逆熵覆盖仍过滤所有爆炸对保护内方块和实体的影响。机械熵增开关持续保存，所属队伍离线时暂停执行，重新在线不补发危险。
- 保护内沿用已确认的断供在线 10/30/60 分钟规则，保留建筑、已有生命和永久里程碑。**现有核心尚无生态衰退/气候设施执行器**，本步只固定兼容约束和阈值 12000/36000/72000 tick；不得报告为已实现。后续接入不能借危险执行器绕过归属队伍的离线暂停，也不能用全服有人在线代替本队在线。没有归属的自然荒野危险只在该维度有在线玩家时调度；所有全队离线相关状态不追算。

## 配置与管理员初始化

`ModConfig.Type.SERVER`，文件 `skycore-server.toml`，表 `[entropy]`；普通专服配置位于存档的 `serverconfig`，GameTest 的测试配置由加载器放在运行目录的配置位置；整合包可在 `defaultconfigs` 提供模板。完整键名与默认值在清单 `config.defaults`，源代码 `EntropyConfig` 给出范围。注册后端不会自动启用自然维度。

默认自然维度列表为空，绝不枚举并自动启用现有维度。扩展/机械半径 1/0；燃料每份 1200 tick；玩家预警 100 tick、伤害间隔 20 tick、伤害 1；爆炸强度 4。用户确认**每个启用维度 1200 tick（60 秒）最多一次**，自然和机械共用该维度机会，所有维度共用每服务端 tick 最多 1 次爆炸、64 次候选检查。实体/植物每服务端 tick 分别最多检查 128/256 个；轮转处理已加载对象，不补跑超时积压或强制加载。爆炸频率、强度、半径、耗料均可配。配置更新须在服务端线程重算索引，保留已接受燃料及证据；`maxStoredFuelTicks < fuelTicksPerItem` 时拒绝运行并诊断，不静默丢燃料。

维度公平游标只在到期且取得预算尝试时推进到该维度之后，空闲 tick 不推进，预算拒绝不覆盖游标；搜索耗尽但未找到有效候选也让出下一轮。同步到期的其他维度照常消耗本轮机会，不在下一 tick 补发。三维度存在、两维度启用且持续有有效候选时，默认六个 1200 tick 周期合计各服务三次。

管理员入口固定：

```text
/skycore shelter initialize <player> <dimension> <x> <y> <z>
/skycore shelter status
/skycore shelter status <player>
```

initialize 需要权限 2，解析一个在线玩家当前 FTB 队伍，在目标维度已加载、建造高度内、可替换位置放置并绑定一台逆熵机。自然维度尚未启用时也允许初始化；不发物品、不创建平台、不开启维度。成功及同队同位置重试返回 1；未就绪/未加载/被占/越界/不同位置冲突返回 0 并说明原因，不加载目标区块。status 自查权限 0、查询他人权限 2。

初始化按队伍保存唯一标记（机器 UUID、维度、位置），同队同位置重试不得重复发放；不同位置冲突不能移动既有庇护。机器毁坏后标记仍保留，通过恢复配方重建，不把初始化变成免费刷机。放置前先等待旧 SavedData 写入队列完成，再用 NeoForge 原子写入保存 pending 标记；写入失败则停用后端，不放置机器。加载区块时自动续办 pending，复用同一 UUID；已提交标记不重新发机。该协议不提供整个世界、玩家背包和区块文件的断电事务保证。NOT_READY 只用于运行层/队伍不可用。

## 查询、证据与存档兼容

原 `hasMilestone`、`snapshotJson`、`TeamMilestoneGrantedEvent`、`/skycore progress` 全部不变，旧快照仍为 schema 1。新入口也在 `dev.skycore.api.SkyCoreApi`，只许逻辑服务端主线程读取：

```java
boolean entropyRuntimeReady(ServerPlayer player);
String entropySnapshotJson(ServerPlayer player); // 可为 null
String fieldSnapshotJson(ServerLevel level, BlockPos pos); // 可为 null；不加载区块
boolean hasEntropyEvidence(ServerPlayer player, String evidenceId);
```

新快照分别独立 schema 1，完整字段见 JSON 示例；team 快照只含当前队伍的机器，kind 固定 `anti_entropy`/`entropy`，位置为整数，remaining_fuel_ticks 非负且表示已接受存量，loaded 为当前区块加载状态，逆熵机 enabled 恒为 true。field 快照给出指定位置两种危险、保护和保护机器 UUID；可查到自然熵增 true 而 protected true、hazardous false。运行层/队伍未就绪或目标区块未加载时返回 null；脚本保留已有任务状态并稍后重查，不清任务、不将 null 当作安全。未知 evidence 返回 false，未就绪也返回 false，故同步前先检查 readiness/非空快照。

永久证据固定 `skycore:entropy/world_fuel_produced` 与 `skycore:entropy/world_fuel_supplied`。前者只由本队工作台提交双产物触发，后者只由本队机器实际扣除物品并接收燃料 tick 触发；二者独立，不强制先有 produced 才认可合法投入。新增不可取消 `TeamEntropyEvidenceGrantedEvent`（`getTeamId/getEvidenceId`）在提交后首次登记时通知，监听器异常不能撤回提交。登录、换队、脚本重载、轮询以快照补同步。

实例旧 `world_fuel -> minerals_refined` 是代理任务，**保留旧任务及其已获进度**。第③步新增独立真实生产与供养任务，使用清单 `pack_bridge` 的 `entropy_evidence_mapping` 字段及映射键 `entropy_world_fuel_produced`/`entropy_world_fuel_supplied`，设置 `entropy_snapshot_method=entropySnapshotJson`、`entropy_snapshot_schema_version=1`，读取新 evidence；旧 `mapping` 仍只读旧 milestones。两个通道独立同步，新快照未就绪时保留新任务状态且不阻断旧首章同步。不把代理记录迁移成真实供养，也不替换旧任务 ID 导致已完成首章清空。FTB 十六进制任务 ID 由整合包侧保管。

新增全世界单份 `data/skycore_entropy.dat`（放在 overworld data storage），SchemaVersion=1；根字段 Teams/Machines，具体记录字段在清单 `persistence`。它是机器、燃料、初始化和证据的权威；BE 仅保留 OwnerTeam、EntropySchemaVersion 与 MachineId 链接，空间索引为可重建派生数据。删除/替换按机器 UUID+位置核验，旧记录不能撤掉同位置新机器。加载区块时核对真实 BE 链接，缺失/替换只清相应贡献，区块卸载不等于移除。未知更高版本或冲突记录报错并保留源数据，不能清空后重存。加载时绕开会吞掉解析异常并新建空记录的 `computeIfAbsent` 路径；未知 BE 格式保留原始字段并停用危险后端，避免原版吞异常后丢弃 BE。旧壳方块首次加载登记为零燃料，机械熵增仍关闭。

旧 `skycore_teams.dat` 维持 SchemaVersion=1 和原有里程碑，不从旧记录回填新证据。没有新数据表示未初始化且无证据，不自动发机/启用。新增格式没有历史存档：空数据按新档，非空而缺版本则拒绝并诊断，不猜测迁移，也不把损坏当作新世界。降级不能保证新增注册/存档可读，回滚须恢复安装前完整备份及旧 JAR/脚本/配置。

## 验证与启用门槛

第①步骨架验证只证明注册及未就绪边界。第②步改为验证真实后端、危险与保护、真实投料和证据，并保留旧首章回归；已增加实际卸载、跨维度预算、异常配置、pending 初始化及正常退出 JVM 后重启同一 QA 存档的验证。具体结果与未测范围以第②步证据为准。

第②～⑧步必测：正负区块边界及完整高度；重叠/多队/强制替换按 UUID 撤场；燃料耗尽、离线及机器卸载的统一时钟；双产物各自堵塞；动植物死亡/玩家渐伤/撤离；外部爆炸波及保护与抗熵石；双队投料切换权限；存档兼容和停止 JVM 后恢复；首章启动至真实供养任务同步；跨维度共享预算及已加载区域性能。生态设施未实现项目需明确列出，不能填通过。

启用顺序：备份原实例和正式存档 → 第⑥步发布、整合包侧校验安装 → 原实例全模组独立 QA 存档实际专服和图形客户端验证 → 明确开局维度、为每队幂等初始化庇护 → 再设置 naturalDimensions 启用。不得用玩家正式存档测试，不改其他模组，不新建核心工程或 Prism 实例。独立队伍世界、64 槽、初始平台生成、门户、气候设施、跨队贸易仍在后续范围。
