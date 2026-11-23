# Sky Core 首章接口契约 v1

v1 首章接口现已实现，提供源码、只读 API 和 `skycore-0.1.0-neoforge-1.21.1.jar`。本文件规定整合包接入基线；当前制品、验证证据及尚未验收的边界见 `verification-v1.md`。沿用原核心目录，没有新建第二个核心项目或 Prism 实例。

## 1. 项目与职责

| 项目 | 约定 |
| --- | --- |
| 核心开发路径 | `E:\mods\Sky Core`；核对时为空，沿用此目录 |
| 技术名称 / modid | `Sky Core` / `skycore`；整合包展示名称独立决定 |
| 目标 | Minecraft `1.21.1`、NeoForge `21.1.265`、Java `21` |
| 整合包侧路径 | `D:\MC\PrismLauncher_minecraft\1.21.1(1)\minecraft\`（用户后续要求覆盖原拟建 `pack/` 方案） |
| 已核对实例依赖 | KubeJS `2101.7.2-build.381`；FTB Teams `2101.1.12`；FTB Quests `2101.1.38`；FTB Library `2101.1.39` |

- 核心负责注册物品、方块及 BlockItem、方块实体与菜单、自定义配方类型/序列化器、服务端执行、队伍归属、进度持久化及查询。三种方块均在放置时绑定当前队伍，不能通过搬运继承别队操作权限。核心要求 FTB Teams；KubeJS、FTB Quests、JEI 为可选接入。
- 整合包侧负责配方实例、数量/时长平衡、标签、FTB Quests 内容与映射、提示及配置，不在 `startup_scripts` 重复注册本契约的物品或方块。
- 核心不内置下表的正式配方实例；整合包用约定的固定 recipe ID 发布它们。更改数量、时长或 Ingredient 不改 recipe ID。独立验收使用隔离测试数据包，不把测试配方混入正式 JAR。
- 用户已授权在现有实例接入；本轮核心侧只读取整合包文件并交付 JAR，不代改脚本或现有存档。自动化测试在核心工程的 GameTest 运行目录执行，不新建 Prism 实例；整合包成员负责在原实例安装及联调。
- 机器可访问性、队伍阶段、物料扣除由服务端最终校验，不能靠任务依赖、JEI 隐藏或客户端脚本实施权限。

## 2. 注册 ID 与无外部资源启动链

| ID | 名称 | 注册范围 |
| --- | --- | --- |
| `skycore:rock_core` | 岩核 | 方块及同名 BlockItem |
| `skycore:stone_fragment` | 碎石 | 物品 |
| `skycore:mineral_residue` | 矿质残渣 | 物品 |
| `skycore:primordial_fuel` | 原初燃料 | 物品；首章按份消耗的热源 |
| `skycore:stone_workbench` | 石质工作台 | 方块及同名 BlockItem、菜单、方块实体 |
| `skycore:condensate` | 凝露 | 可堆叠物品，不是流体或装水容器 |
| `skycore:mineral_soil` | 矿质土 | 方块及同名 BlockItem、萌发状态方块实体 |
| `skycore:life_embryo` | 生命胚种 | 物品 |

石英、红石分别使用 `minecraft:quartz`、`minecraft:redstone`。第一棵树生成真实的 `minecraft:oak_log`、`minecraft:oak_leaves`；不新增对应材料 ID，也不预先赠送树苗。

岩核后续由队伍初始平台机制提供。首个联调 JAR 尚无平台生成器，管理员须在现有实例选定的起点为每个已组好的队伍提供一块岩核和无木、无水的平台。岩核放置时绑定放置者当前队伍。该初始化不计入生产合成链，核心不自动新建或改写世界。

徒手采集操作固定为**空主手右键本队岩核**，服务端按岩核冷却，每 `20` tick 最多成功一次；每次产出 `2` 碎石与 `1` 残渣。背包放不下则整次失败，不产生地面掉落。生存模式不能破坏岩核；可持续采集，不消耗一次性的起始方块。首版这些采集值由核心固定，不伪装成第三种配方类型。

以下为首章联调默认值，整合包可调整数值但须维持依赖闭环：

| recipe ID（均以 `skycore:ch01/` 开头） | 类型 | 输入 | 热源 | 结果 / 时长 |
| --- | --- | --- | --- | --- |
| `primordial_fuel` | `minecraft:crafting_shapeless` | 残渣 ×4 | 无 | 原初燃料 ×1；背包合成 |
| `stone_workbench` | `minecraft:crafting_shaped` | 碎石 ×4，2×2 | 无 | 石质工作台 ×1；背包合成 |
| `minerals` | `skycore:stone_processing` | 碎石 ×4 | 原初燃料 ×1 | 石英 ×1、红石 ×1；200 tick |
| `condensate` | `skycore:stone_processing` | 石英 ×1、红石 ×1 | 原初燃料 ×1 | 凝露 ×2；200 tick |
| `mineral_soil` | `skycore:stone_processing` | 碎石 ×2、残渣 ×2、凝露 ×1 | 无 | 矿质土 ×1；100 tick |
| `life_embryo` | `skycore:stone_processing` | 矿质土 ×1、凝露 ×1、石英 ×1、红石 ×1 | 原初燃料 ×1 | 生命胚种 ×1；400 tick |
| `first_tree` | `skycore:germination` | 已放置矿质土；生命胚种 ×1、凝露 ×1 | 无 | 原地长出橡树；1200 tick |

生命胚种制作消耗一份矿质土；萌发还需另制并放置一份矿质土。所有材料都能从可再生岩核重复获得；凝露不需要玻璃瓶、水桶、既有水源、木材、FE、骨粉或先前树木。额外世界光照和自然随机生长不是首棵树的前置条件。

## 3. 配方格式与执行语义

机器可读 ID、字段和完整示例见 `../contracts/chapter-1.v1.json`。该文件是契约清单，不是自动加载的数据包；脚本须按各记录的 `id` 与 `json` 发布配方。KubeJS 可使用 `event.custom(record.json).id(record.id)`，无需自定义 KubeJS 配方 builder。

公共计数 Ingredient 格式为 `{"ingredient":{"item":"skycore:stone_fragment"},"count":4}`；`ingredient` 使用 1.21.1 原版 Ingredient 格式，也可用 `tag`；`count` 是 1–64 的整数。结果使用 1.21.1 ItemStack 的 `id`、`count` 字段，不使用旧版 `item` 结果字段；v1 契约只承诺这些结果字段。

### `skycore:stone_processing`

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| `type` | string | 固定 `skycore:stone_processing`，同名 RecipeType / RecipeSerializer |
| `inputs` | 计数 Ingredient 数组 | 1–4 项，无序、合并匹配；每个非空输入槽必须属于至少一个声明的 Ingredient；同种材料可多份堆叠，不能重复占用同一份输入 |
| `fuel` | 计数 Ingredient | 可省略；省略表示不需要热源，不表示使用 FE 或原版熔炉 |
| `results` | ItemStack 数组 | 1–2 项；`count` 不超过该物品最大堆叠数 |
| `duration_ticks` | 正整数 | 仅本队至少一人在线且工作台所在区块已加载时推进 |
| `required_milestone` | string | 可省略；完整命名空间 ID，开始和提交时均检查所属队伍 |

石质工作台有 4 个输入槽、1 个热源槽、2 个只出不进的结果槽。首版由本队玩家操作菜单，不暴露管道自动化接口。方块放置时绑定队伍，其他队伍不能使用、拆除或通过菜单包访问。

在输入、热源、阶段、输出容量均满足后计时；材料保留原槽。输入/热源槽内容、配方 reload 或阶段资格变化使本次计时归零；输出堵塞则暂停。完成时重新校验并一次性扣料、入库、记进度，输出堵塞不扣料。区块卸载或全队离线仅暂停，恢复后重新校验。拆除只掉落实际库存，不返还已提交的材料，也不重复生成产物。

### `skycore:germination`

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| `type` | string | 固定 `skycore:germination`，同名 RecipeType / RecipeSerializer |
| `soil` | block ID | 已放置基底；v1 支持 `skycore:mineral_soil` |
| `seed` | 计数 Ingredient | v1 `count` 固定为 1 |
| `nourishment` | 计数 Ingredient | v1 `count` 固定为 1；默认凝露 |
| `tree` | string | v1 仅支持核心自带的 `minecraft:oak` 小树预设；不是任意 configured feature ID |
| `duration_ticks` | 正整数 | 区块已加载且本队至少一人在线时累计 |
| `required_milestone` | string | 可省略；开始和生成提交时均检查 |

主手生命胚种、副手凝露，右键本队矿质土开始。种子与凝露移入矿质土暂存，重复交互不重复收取。破坏尚未完成的矿质土时返还暂存材料一次。

小树空间为矿质土正上方居中的 3×5×3 可替换空间。预设为中心 4 格橡木原木、顶部两层 3×3 树冠（下层去掉中央原木位置，共 17 格橡树树叶）。空间受阻则停在完成前并保留材料，清理后重试，不需要额外投料。树木方块必须全部成功提交后才消耗暂存材料并登记首树里程碑；失败不得留下部分树木或产生物品退款副本。叶片按正常树叶处理，支持后续获取树苗。

所有自定义配方只在服务端执行。匹配同一机器输入的多条配方按完整 recipe ID 字典序选择第一条，整合包应消除不必要的重叠。无效 ID、非法计数、未知里程碑/树预设须让配方加载报错，不能静默生成空配方。

原版 shaped/shapeless 配方不接受本模组的 `required_milestone` 字段；不能在其中添加该字段并误认为已限制合成。首章两条背包配方无需额外阶段门槛。

## 4. 队伍阶段、里程碑与脚本 API

权威状态按 **FTB Teams 实际队伍 UUID** 持久化，队名与玩家 UUID 不作队伍主键。未组队玩家使用 FTB Teams 的个人队伍；查询通过实际版本的 `getTeamForPlayer(ServerPlayer)` 解析当前队伍，不总是查询个人队伍。

进度单调增加、同一里程碑幂等登记；生态衰退不删除已取得里程碑。加入队伍后读取目标队伍现有进度，离队/换队不合并、不复制原队进度或资产。首个联调 JAR 不提供跨队数据迁移；测试时先完成组队再初始化。无已存在核心存档需要此次迁移。

| 里程碑 | 核心认可的触发证据 | 前置 |
| --- | --- | --- |
| `skycore:ch01/core_extracted` | 本队岩核成功徒手采集并交付产物 | 无 |
| `skycore:ch01/workbench_placed` | 本队成员放置并绑定石质工作台 | `core_extracted` |
| `skycore:ch01/minerals_refined` | 本队工作台成功提交 `skycore:ch01/minerals` | `workbench_placed` |
| `skycore:ch01/condensate_obtained` | 成功提交 `skycore:ch01/condensate` | `minerals_refined` |
| `skycore:ch01/life_embryo_created` | 成功提交 `skycore:ch01/life_embryo` | `condensate_obtained` |
| `skycore:ch01/first_tree` | `skycore:ch01/first_tree` 在本队矿质土生成活树成功 | `life_embryo_created` |

上述 recipe ID 与触发证据由核心固定映射；JSON 不提供任意 `grant_milestone` 字段。只持有产物、交易获取材料、领取任务奖励或 FTB Quests 手动完成任务都不会授予里程碑。登记时验证前置与归属。

初始 `unlocked_chapter=1`；取得 `skycore:ch01/first_tree` 后为 `2`。初始材料与首次树木不能来自任务奖励。未来预留 `skycore:ch03/storm_restored`（下界）与 `skycore:ch05/cognition_substrate`（末地）；首章 JAR 只能查询到它们为未完成，不提供完成逻辑。

### Java / KubeJS 只读接口（v1 已实现）

类名：`dev.skycore.api.SkyCoreApi`，静态方法：

```java
boolean hasMilestone(ServerPlayer player, String milestoneId);
String snapshotJson(ServerPlayer player); // 队伍管理器/身份未就绪时返回 null
```

只能在逻辑服务端主线程使用；`hasMilestone` 对未知 ID 或未就绪身份返回 `false`，不得创建或写入队伍进度。`snapshotJson` 返回分离的只读快照，`null` 代表应延后查询，不能据此重置任务。

```json
{
  "schema_version": 1,
  "team_id": "00000000-0000-0000-0000-000000000001",
  "unlocked_chapter": 1,
  "milestones": ["skycore:ch01/core_extracted"]
}
```

KubeJS 通过 `Java.loadClass('dev.skycore.api.SkyCoreApi')` 取得类并读取；无需安装 GameStages，也不依赖 KubeJS 的个人 stages 作为权威。

核心在 NeoForge 游戏事件总线上发布不可取消的 `dev.skycore.api.event.TeamMilestoneGrantedEvent`，提供 `String getTeamId()`、`String getMilestoneId()`、`int getUnlockedChapter()`。事件在服务端提交后触发；每个新里程碑仅触发一次，脚本异常不撤回材料提交。KubeJS 可用已核实存在的 `NativeEvents.onEvent(Java.loadClass('dev.skycore.api.event.TeamMilestoneGrantedEvent'), callback)` 订阅。

FTB Quests 的具体任务 ID 和同步实现由整合包侧持有。事件只通知重新同步本队任务；登录、切队、脚本 reload 后须从快照补同步，不能只依赖一次性事件，也不能把个人物品检测任务当成核心门槛。核心不暴露生产用的任意授予/清除阶段接口。

### 命令接口

- `/skycore progress`：玩家读取本队快照，权限 0；管理器未就绪时报错。
- `/skycore progress <player>`：控制台或权限 2，读取该玩家当前队伍快照。
- `/skycore milestone test <player> <milestone_id>`：控制台或权限 2；已完成返回命令结果 `1`，未完成、未知 ID 或未就绪返回 `0`。`player` 必须解析为单个在线玩家。
- 不向任务奖励开放 `grant` 命令。整合包若只需要布尔判断，可使用服务器命令结果；可靠的整队快照仍以 Java API 为准。

## 5. 首个可联调 JAR 的验收范围

首个 JAR 的交付条件是：8 个注册 ID（其中 3 个方块带同名物品）、岩核再生采集、石质工作台菜单与处理、矿质土萌发、两个自定义配方 codec/网络同步、FTB Teams 归属与权限、6 个首章里程碑、查询 API/事件/命令、存档与重启恢复。加载整合包首章配方后，应能从空背包、本队岩核和无木无水平台走到第一棵树。

该 JAR 不包含正式岛屿生成/传送、64 组维度生命周期、供养与生态衰退、第三/五章门户、跨队交易执法或性能容量承诺；不得作为这些机制已就绪的上线依据。首章联调 JAR 已构建，路径及校验值见验证报告。

实现验收至少包含：

1. 在真实 NeoForge 服务端加载核心、FTB Teams 与整合包配方数据，日志无注册/配方/专服客户端类加载错误；随后在现有实例验证 KubeJS/FTB Quests 桥接与客户端工作台菜单。
2. 从空背包完成全链，检查上述 6 个核心里程碑及 FTB Quests 同步；过程中不使用木材、电力、既有水、骨粉或管理员补材料。
3. 两队的采集冷却、方块使用和阶段互不串联；获得他队产物或人工完成任务不能替代本队处理证据；新队员读取目标队伍进度。
4. 输出堵塞、重复点击、输入改动、配方 reload、萌发空间受阻、机器/矿质土拆除、区块卸载和全队离线不吞料、不复制；重新加载正确恢复或暂停。
5. 专服重启后队伍归属、库存、萌发暂存与进度保留；已完成里程碑不重复授予；关闭脚本后重启再补快照仍能修复任务展示。

后续实现继续遵守已确认的约束：独立重度专家包；每队独立天空/下界/末地；预注册 64 组世界槽，按需活跃及休眠，扩槽维护重启；供养与生态衰退全队离线暂停，本队任一成员在线时只累计一份时间，断供达在线 600/1800/3600 秒逐级衰退并可恢复；第三章恢复雷暴后开下界，第五章认知基底后开末地；跨队交易仅允许双方已解锁的低阶建筑材料。20–40 人、约 10 活跃队是后续压测目标，本次文档核对不构成性能验证。

## 6. 本次验证与兼容边界

已读取实例 `mmc-pack.json` 确认目标版本；已对实际 FTB Teams/KubeJS JAR 用 `javap` 核对团队解析、`Java.loadClass`、`NativeEvents.onEvent` 入口；本机 `java` / `javac` 均为 21.0.10。

已实现 8 个内容 ID、7 条外置契约配方的执行支持及 6 个队伍里程碑。4 项 JUnit、7 项真实服务端 GameTest 和发布 JAR 内容检查通过。完整链测试从 28 次实际岩核采集开始，制作 6 份燃料、1 个工作台，执行 3 批矿物、2 批凝露、2 批矿质土及 1 批胚种配方，再放置矿质土并生成真实橡树；未补充外部原料。

GameTest 也验证了恢复、离线暂停、输出堵塞、越权、暂存材料返还及网络 codec。现有整合包的图形客户端、实际 KubeJS/FTB Quests 运行桥接、正常专服进程重启与负载测试仍待联调，不能用当前结果替代。v1 后续允许增加可选字段；若修改现有 ID、字段含义、阶段语义或方法签名，须先同步新版契约给整合包侧，避免双方分别更名。
