# 第②步：熵增与庇护核心验证

本步实现服务端运行后端，供第④步独立只读审查；正式发布属于第⑥步。仅修改 Sky Core，未安装至原实例，未使用玩家正式存档。机器/配方 ID 与 schema 保持第①步契约；补充了用户确认 B 的玩家爆炸规则及具体持久化行为。

第 1 次审查打回的三项问题及本轮修复、失败记录见 [修复说明](entropy-core-step-2-review-1.md)。下表记录修复后的当前结果，早期 19/19 日志仅为历史证据。

## 实现

- 自然熵增按维度显式启用，默认列表为空。两种机器使用服务端权威 UUID 账本与区块索引；无燃料逆熵机保护本区块完整高度，供燃料后默认扩大为 3×3，重叠取并集并优先于所有熵增。
- 世界燃料实际扣 1 件才登记供养证据，每份增加 1200 个所属队伍在线 tick；默认容量 76800。全队离线暂停并保留覆盖，机器卸载仍按在线队伍扣时，耗尽只收缩本机扩展。
- 机械熵增机默认关闭，本队成员空主手潜行右键切换；本队离线暂停危险。逆熵机不能正常采掘、爆炸或活塞移除；强制替换按 UUID、位置、归属撤回本机贡献。
- 危险区被检查到的 Mob 与标签植物立即死亡/移除，水生植物保留原水。生存/冒险玩家先预警 100 tick，再每 20 tick 受 1 点伤害，撤离清除计时。按 B，预警内免受熵增爆炸直接伤害，期满同时承受爆炸和持续伤害。庇护过滤所有爆炸对保护内方块、实体的影响；抗熵石使用真实耐爆规则。
- 管理员初始化持久化唯一 pending 标记后才放机，已加载区块可续办同一 UUID；已提交标记保留，毁机后须走恢复配方。账本 schema 1 严格拒绝未知/冲突数据；未知 BE 格式保留原 NBT 并停用后端，不将异常当作空档重写。
- 石质工作台双产物原子提交成功才记生产证据；双产物任一堵塞不扣输入/热源，不记证据。生产、供养与旧首章里程碑相互独立，旧代理任务不补成真实供养。
- 危险只处理已加载对象；各维度自然/机械合计默认每 1200 tick 最多一次爆炸，共享每 tick 1 次爆炸、64 候选、128 生物、256 植物检查的预算。执行前验证爆炸影响区域已加载，不补发积压、不强制加载。

## 环境与结果

Windows 11；Microsoft OpenJDK 21.0.10；Minecraft 1.21.1；NeoForge 21.1.265；FTB Teams 2101.1.12、FTB Library 2101.1.39、Architectury 13.0.12。沿用工程 Gradle wrapper 9.3 与 moddev 2.0.142，未新增依赖。

QA 运行目录为 `D:\MC\skycore-build\gametest`，存档为该目录下 `world`。GameTest 实际配置在运行目录 `config/skycore-server.toml`，与契约默认值逐项核对一致。此目录是独立核心 QA 输出，不是新 Prism 实例。

| 检查 | 结果及证据 |
| --- | --- |
| `gradlew.bat test build runGameTestServer --no-daemon -Pentropy_restart_phase=seed` | 成功；`dist/entropy-core-step-2/gradle-review-1-seed.log` |
| JUnit | 12/12，失败/错误/跳过均 0；4 项旧测试 + 5 项熵增规则 + 3 项调度回归；复制的 `TEST-*.xml` |
| 真实 NeoForge GameTest | 20/20 必测通过；`gametest-review-1-seed.log` |
| 新 JVM 同档恢复及完整套件 | `gradlew.bat runGameTestServer --no-daemon -Pentropy_restart_phase=verify` 再次 20/20；`gradle-review-1-verify.log`、`gametest-review-1-verify.log` |
| 同一 QA 存档正常退出 JVM 后恢复 | seed 与 verify 分开启动；`restart-proof.json`，`verified_after_jvm_restart=true` |
| 新旧契约检查 | `tools/check_entropy_contract.py`、`tools/check_pack_contract.py` 均通过；`contract-checks.log` |
| 资源生成一致性 | 重跑 `tools/generate_resources.py` 前后全部资源 SHA-256 相同 |
| JAR 检查 | JSON/TOML 可解析，Java class major 65，无重复 ZIP 项，无 QA 类、结构、正式配方或嵌入依赖；`verification.json` |

JUnit 的实际执行时间见 XML；其后源码未变的最终构建显示 `UP-TO-DATE`，不计作新增执行。GameTest 在服务端加速 tick 下运行，日志中的墙钟秒数不表示正常服务器的相同秒数，更不代表负载性能验收。

20 项 GameTest 包括 7 项首章回归、2 项契约/实际后端用例及 11 项新增机制用例。新增机制覆盖完整高度和区块边界、重叠/强制移除/旧 UUID、双队权限与容量、离线/耗尽、真实双产物各自堵塞、动植物/玩家撤离重入、爆炸 B/外部爆炸/抗熵石、schema/查询只读、pending/配置纠错、实际区块卸载和双维度共享预算。调度回归在三个真实维度、两维度同步到期、全范围有效候选下经过六个默认 1200 tick 周期，断言各爆炸 3 次且没有补发；真实交互测试经 `gameMode.useItemOn` 验证副手持物开关及跨队拒绝。首章用例在自然熵增启用的 QA 维度用管理员入口交付一台无燃料逆熵机，完成原岩核至首树断言，再真实加工世界燃料和抗熵石、投料扩大保护并查询独立证据。

## 跨 JVM 与中断边界

先运行 `gradlew.bat runGameTestServer --no-daemon -Pentropy_restart_phase=seed`，服务端正常保存、退出；再对同一 QA 存档运行 `-Pentropy_restart_phase=verify`。探针在显式加载机器区块之前核对离线燃料 1200、两台机器 UUID/归属、机械熵增开启状态、初始化标记、两种独立证据与扩展索引，再加载 QA 区块核对 BE 链接。探针的燃料和证据为持久化夹具直接设置；真实取得证据由工作台和投料行为测试另行验证。该探针仅编入 GameTest，未进入交付 JAR。

pending 用例另外实际读回放机前的压缩 SavedData，核对未提交身份已在磁盘，并验证已加载区块续办复用 UUID。初始化 checkpoint 等待旧异步 SavedData IO 后采用 NeoForge 原子压缩写入；写入错误停止运行。正常退出重启已验证，未模拟操作系统断电，也不宣称玩家背包、账本与区块三个文件具有跨文件事务。

## 失败记录与夹具修正

早期失败日志保留在同一证据目录。修正包括：GameTest 默认 mock 为创造模式，改用真实生存 ServerPlayer 并接入普通网络 tick；固定饥饿值以避免回血干扰；结束时清理测试机器；爆炸站位避开玻璃和结构屏障，并用 QA ticket 保持影响区域加载；同区块伴随机器改为垂直放置，避免 `east()` 在边界跨区块。

首次交付前最后一次失败为预警 90 tick 断言生命值 16/20，保留 `gametest-warning-fixture-failed.log`、`gradle-warning-fixture-failed.log`。该日志未记录具体伤害类型，不能单凭它将全部扣血归因于熵增。玩家和生物明确清理站位空气、断言玩家无碰撞，并保证庇护植物在同一区块；仍保留 90/105/125 tick 原生命值和撤离重入断言，增加失败时的伤害来源输出。此前两个 JVM 的 19/19 后仍在审查中暴露调度问题，本轮已经补充默认周期与受控候选回归，不能用历史通过替代当前验收。

## 制品与后续

审查预览 JAR：`dist/entropy-core-step-2/skycore-0.1.0-neoforge-1.21.1.jar`。其 SHA-256、源码/证据哈希与完整改动清单在同目录 `verification.json`。同名版本号沿用当前开发版本，不能据此视作第⑥步正式发布。原 `dist/skycore-0.1.0-neoforge-1.21.1.jar` SHA-256 仍为 `4f1813259d32b5f9919a100a4a50e7091a58586a64944e1c4c29889befe7b67d`。

第③步须发布正式配方和 FTB 真实任务映射；第④步独立只读审查、第⑥步修复与正式发布尚未执行。第⑦～⑧步原实例全模组专服、图形客户端、重启及任务同步验收均未由本步执行。仅验证有界预算，未进行 20–40 人负载验收。

生态衰退执行器和气候设施仍未实现，快照明确 `ecology_implemented=false`；10/30/60 分钟阈值与旧永久里程碑保持兼容约束。独立队伍世界、64 槽、初始平台、门户和跨队贸易仍属后续。

启用须按共用契约：备份 → 正式发布及校验安装 → 原实例独立 QA 专服/客户端验收 → 明确开局维度并为每队初始化 → 启用自然维度。回滚恢复完整备份及对应旧 JAR/脚本/配置，不只删除新增 JAR。
