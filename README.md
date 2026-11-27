# Sky Core

Sky Core 是 Minecraft 1.21.1 / NeoForge 21.1.265 / Java 21 的空岛整合包核心模组，mod ID 为 `skycore`。正式配方和 FTB Quests 任务由整合包提供。

当前版本为 `0.2.2`，提供 64 组队伍世界槽、六章主线进度、分队门户路由、世界燃料与熵增庇护。该版本改为连续计时：世界燃料材料的转化进度记在掉落物上，条件中断、被拾起或落入虚空都会清零，掉落物按原版规则 5 分钟后消失；白天还要求天空光照为 15 且太阳在地平线以上。世界活性收集器去掉了界面，改由 Jade 显示进度，并把产物推入下方容器。熵增爆炸不再破坏 `skycore:entropy_blastproof` 标签中的方块。升级前请备份整组存档。[下载 JAR 和校验文件](https://github.com/Barbatosishere/sky-core/releases/tag/v0.2.2)

## 使用

- 先确定 FTB Teams 队伍。岩核、工作台和矿质土均在放置时绑定当前队伍，换队不迁移旧队资产或合并里程碑。
- 通过 `/skycore world enter` 进入本队的空岛。岩核、石质工作台和矿质土用于推进首章。
- 空主手右键本队岩核；每 20 tick 可取 2 碎石、1 残渣。背包必须能放下整份产物。
- 石质工作台左侧四槽放材料，中间下槽放配方热源，右侧两槽取结果。按整合包配方自动处理，本队无人在线时暂停。
- 主手持生命胚种、副手持凝露，右键本队矿质土。上方居中留出 3×5×3 空间，在线累计 1200 tick 后生成真实橡树。受阻会保留暂存材料，清理后可继续；拆除未完成的矿质土返还暂存材料一次。
- `/skycore progress` 查询本队快照；管理员使用 `/skycore progress <player>` 或 `/skycore milestone test <player> <完整里程碑ID>`。不存在授予权限的任务奖励命令。

完整 ID、字段与阶段语义见 [接口契约](docs/chapter-1-contract.md)，配方实例见 [JSON 清单](contracts/chapter-1.v1.json)。核心 JAR 不包含正式配方；需使用已经写入现有实例的整合包脚本。

熵增与庇护核心已按[共用契约](docs/entropy-shelter-contract.md)实现自然熵增、两种力场机器、世界燃料与抗熵石。自然熵增默认不启用任何维度；先在独立 QA 存档验证，再由管理员为各队初始化逆熵机。无燃料保护本区块，真实供燃料后默认扩大到 3×3 区块，全队离线暂停耗料。危险区玩家先预警 5 秒，随后每秒受到 1 点伤害，并可受到熵增爆炸伤害；预警期间免受熵增爆炸直接伤害。原首章 API 和里程碑不变，新生产/供养证据独立保存。

## 构建与测试

使用 JDK 21。在 `run/mods` 放入以下依赖，或通过 `-Ppack_minecraft_dir=<游戏目录>` 指向现有实例中包含 `mods` 的目录。前三个依赖在客户端与服务端均需安装。

- `ftb-teams-neoforge-2101.1.12.jar`
- `ftb-library-neoforge-2101.1.40.jar`
- `architectury-13.0.12-neoforge.jar`
- `Jade-1.21.1-NeoForge-15.10.6.jar`（构建时使用。联机时客户端与服务端均装有 Jade 才会同步收集进度。）

```powershell
.\gradlew.bat test build --no-daemon
.\gradlew.bat runGameTestServer --no-daemon
```

可用 `-Pskycore_build_dir`、`-Pskycore_nfrt_dir`、`-Pskycore_gametest_dir` 和 `-Pskycore_journeytest_dir` 指定构建、NeoForm 缓存与两种游戏测试目录。完整专服冒烟使用 `python tools/run_fullpack_smoke.py --pack <游戏目录> --output <测试目录>`。`tools/check_pack_contract.py --instance <Prism实例根目录>` 校验首章配方、快照接口和六章任务的核心里程碑映射。

测试源码、配方和结构独立放在 `src/gameTest`，不会进入发布 JAR。GameTest 读取与 pack 相同的 JSON 配方，使用真实 NeoForge 服务端、FTB Teams 登录和自然服务端 tick。`tools/generate_resources.py` 可重建原型模型、翻译、植物/伤害资源及十条契约测试配方；`tools/check_pack_contract.py --write-test-fixtures` 可将现有 pack 的五条补充配方复制到本项目测试资源，不会改动实例。熵增跨 JVM 恢复测试在同一 QA 存档依次运行 `gradlew.bat runGameTestServer --no-daemon -Pentropy_restart_phase=seed` 和 `gradlew.bat runGameTestServer --no-daemon -Pentropy_restart_phase=verify`；不要将该测试用于正式存档。

`run_fullpack_smoke.py` 默认调用当前实例维护的 `tools/run_server_integration.py`，验证实际 Rhino 脚本、配方管理器、FTB custom 任务与双队同步。其服务端 QA 类路径按文件摘要排除不支持服务端的客户端组件；原实例的 JAR 保持原样。完整原始 JAR 集合的启动诊断可显式使用 `--raw-startup`，该模式不执行行为断言。

自动化测试不代表现有整合包的图形界面、人工首树操作或 20–40 人负载已验收。核心制品与独立测试见 [v1 验证](docs/verification-v1.md)，原实例服务端联调见 [联调记录](docs/fullpack-smoke-verified.md)。
