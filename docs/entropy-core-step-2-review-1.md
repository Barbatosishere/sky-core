# 第②步，第 1 次审查修复

本轮只修改 Sky Core，未安装到原实例。原审查为 reject；这里记录责任成员修复及测试，仍须独立复审。

## 1. 默认周期维度饥饿

原调度按每个服务端 tick 推进维度游标。三个维度存在时，默认周期 1200 与维度数整除，同步到期总从同一维度起步；全局预算只有 1，后者每次机会被丢弃。此为实现缺陷，不能通过调整默认周期或增加预算规避。

现将时钟与公平调度放入 `EntropyExplosionSchedule`，生产后端直接使用它。游标只在到期维度取得尝试预算时移动到该维度之后；预算耗尽的维度依旧清掉本轮机会，不修改游标，不补发。搜索耗尽候选但无成功爆炸也会让出下轮，避免无效搜索持续压住其他维度。

新增 3 项 JUnit：默认 1200、三维度两启用、7200 tick 同步到期，精确断言 3/3；拒绝机会在下一 tick 不补发；失败搜索耗尽 64 候选后也轮换。新增真实 GameTest 在三个原版维度存在时，对两个启用维度每游戏 tick 驱动同一生产后端，实际经过六个默认周期，精确断言每次到期合计一次、闲时零次、最终 3/3。新用例的 7300 tick 上限用于六个 1200 tick 周期，不放宽旧 100 tick 用例或预算。

## 2. 副手持物的真实交互

核对本机 NeoForge 21.1.265 目标源码，`ServerPlayerGameMode.useItemOn` 在潜行且任一手持物时可跳过方块路径。现通过双端 `RightClickBlock` 对“机械熵增机、空主手、潜行、主手交互”显式允许方块交互并禁用此次物品交互，尊重已有方块拒绝状态。事件本身不执行开关；原方块路径只在逻辑服务端调用后端，权限仍由当前 FTB 队伍决定。

扩展原权限 GameTest，调用真实 `player.gameMode.useItemOn`：双手空开启一次、副手盾牌关闭一次、副手石头开启一次且不消耗/放置石头、跨队副手持盾牌拒绝、显式副手调用不再次切换。保留真实投料、容量和逆熵优先断言。

## 3. 当前失败及受控候选

审查发现的失败原样复制到 `dist/entropy-core-step-2/gametest-review-1-failed.log`：`twodimensionssharebudgetswithoutcatchuporchunkloads` / `Both dimensions receive opportunities`，完整套件 1 required test failed。未再用历史 19/19 判断交付。

原 100 tick、20 tick 周期夹具混用全局加载队列、全高度随机点和临时加载。它没有保证检查预算内能抽到本维度有效点，也没有保证爆炸完整影响范围持续加载。该日志缺少每个候选和拒绝原因，无法恢复那次随机路径，不能把它单独当成默认周期饥饿的复现。

改用未安装到全局 runtime、未注册到存档存储的内存 QA 账本，仍调用原生产后端及未替换的随机采样器、预算和真实爆炸实现。候选队列仅含两维度各一个中心区块；其全高度/全坐标均为无庇护自然危险，周围 3×3 区块由 QA ticket 保持加载。每 tick 检查四角的完整影响区，因而采样坐标随机不再意味着有效性随机。自然与开启机械熵增同时存在，仍共用本维度机会。

旧用例保持 100 tick 和 20 tick 周期，强化为精确 5 次、3/2 平衡、到期外零爆炸；另外显式引爆已加载空气中的固定中心，检查真实 Detonate 事件恰一次及玻璃确实破坏。默认六周期回归与真实世界破坏断言分别观察公平性和爆炸行为，未提高 1/64/128/256 预算。

## 本轮追加失败与最终验证

第一次修复后 seed 完整 20/20；接着 verify 时旧爆炸 B 用例在主动引爆前生命值为 17/20，导致整套失败。原样保留 `gametest-review-1-blast-fixture-failed.log` 与 `gradle-review-1-blast-fixture-failed.log`。日志未记录伤害类型，不能由此断言是熵增爆炸造成。

该用例此前将玩家放在随机 GameTest 外的区块边界，只清了爆炸源，未保证玩家整段站位无阻挡。现在将整组边界夹具放到固定独立 QA 坐标，明确清理站位，起始断言无碰撞及位于保护外，失败时打印伤害来源；仍保留 85 tick 血量、预警内爆炸无伤、期满爆炸有伤、普通块破坏、庇护内保留和抗熵石耐爆的全部断言。

最终版本依次执行：

```powershell
.\gradlew.bat test build runGameTestServer --no-daemon -Pentropy_restart_phase=seed
.\gradlew.bat runGameTestServer --no-daemon -Pentropy_restart_phase=verify
python tools/verify_entropy_step_2.py
```

当前结果为 JUnit 12/12、seed GameTest 20/20、正常保存退出后新 JVM verify GameTest 20/20，跨 JVM 探针通过。原始输出分别为 `gradle-review-1-seed.log`、`gametest-review-1-seed.log`、`gradle-review-1-verify.log`、`gametest-review-1-verify.log`。JUnit 的实际执行时间见 XML；后续源码未变的 `UP-TO-DATE` 不另算执行。制品、全部源码与证据 SHA-256 以更新后的 `dist/entropy-core-step-2/verification.json` 为准。

原正式 JAR、整合包配置与玩家存档未改动。原实例全模组专服、图形客户端、真实任务同步及多人负载仍留待对应步骤；不以核心 QA 通过代替它们。
