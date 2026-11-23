# Sky Core 0.1.0 验证与交付

v1 已实现首章功能，交付真实 JAR。核心工程沿用 `E:\mods\Sky Core`，没有另建 Prism 实例，未编辑整合包侧脚本、配置、模组目录或现有存档。

## 制品

- 交付：`E:\mods\Sky Core\dist\skycore-0.1.0-neoforge-1.21.1.jar`
- 构建原件：`D:\MC\skycore-build\build\libs\skycore-0.1.0-neoforge-1.21.1.jar`
- 大小：78,494 字节；34 个 Java 类；class major version 全部为 65（Java 21）。
- SHA-256：`4f1813259d32b5f9919a100a4a50e7091a58586a64944e1c4c29889befe7b67d`
- 目标：Minecraft 1.21.1、NeoForge 21.1.265、Java 21；FTB Teams 2101.1.12、FTB Library 2101.1.39、Architectury 13.0.12。

JAR 包含 8 个物品模型、3 个方块模型/状态及方块实体、菜单/客户端界面、两种配方序列化器、队伍进度 API/事件/命令和中英文翻译。不含正式配方、QA 类/结构/配方或嵌套依赖。正式配方继续由原实例 KubeJS 发布。

## 实际通过

| 验证 | 结果与范围 |
| --- | --- |
| `gradlew.bat build` | 通过，完成编译、测试检查及 JAR 构建 |
| `gradlew.bat test` | 4 项通过，0 失败/错误/跳过；最后一次 build 实际重跑，记录为 `test-results/test/TEST-dev.skycore.logic.IngredientAllocatorTest.xml` |
| `gradlew.bat runGameTestServer` | 最终 7 项必需测试全部通过，日志为本机 2026-11-16 01:23:59；运行于实际 NeoForge 服务端，GameTest 加速推进真实服务端 tick |
| `python tools/check_pack_contract.py` | 原实例 7 条核心 JSON 配方及 ID、快照类/版本、里程碑映射完全匹配，5 条补充配方被核对并复制到本工程 QA 数据集 |
| JAR 检查 | JSON/TOML 可解析、目标元数据正确、ZIP 无重复条目、Java 版本正确、API 类及全部模型存在，且未携带测试资源或第三方 JAR |

JUnit 验证重叠 Ingredient 需要重新分配材料、跨堆叠满足数量、同一份材料不能重复使用，并对 500 组固定种子的输入与独立穷举分配算法对照。500 次对照属于一个测试，未当成 500 个独立测试报告。

7 项 GameTest：

1. `firstTreeFromOnlyRenewableCoreMaterials`：28 次岩核采集及重复点击冷却、背包真实配方、完整工作台链、矿质土萌发、6 个里程碑、最终章节值和精确余料；再将队伍进度写入压缩 NBT 文件并重新读取。
2. `outputBackpressurePauseAndWorkbenchDiskRestore`：输出满时不扣料/热源、不授予里程碑；部分加工进度与库存写盘并重建方块实体后完成一批，产物各一份。
3. `blockedGerminationKeepsEscrowAcrossDiskRestore`：空间受阻不产生部分树木/虚假里程碑；暂存材料与计时写盘重建后无额外投料完成；拆除另一块已投料矿质土只返还一次胚种、凝露及土块，重复拆除不重复返还。
4. `realPartyIdentityAndCrossTeamPermissions`：通过真实 FTB Teams API 创建 party，确认使用队伍 UUID 而非玩家 UUID；拒绝他队采集/破坏/菜单访问，背包只有一个空槽时整次采集失败且无部分交付；验证完整命名空间命令的 1/0 返回及未来/未知里程碑保持锁定。
5. `offlineOwnedMachineDoesNotAdvance`：真实注销所属队伍唯一在线玩家，之后 220 tick 无加工、无扣料。
6. `recipeNetworkCodecsAndValidation`：全部已加载工作台配方及萌发配方在真实注册表上下文完成网络 codec 往返；拒绝未知里程碑、零时长、无关输入；验证满输出插入不修改原始堆叠。数据集中包含 pack 的熔岩桶及食物补充配方。
7. `inputEditsResetImmediatelyAndCannotForgeTreeEvidence`：正在加工时修改输入立即重置进度，工作台提交冒用首树 recipe ID 不能替代真实树木生成证据。

所有测试类和测试配方均在独立 source set，未进入交付 JAR。完整通过日志复制在 `D:\MC\skycore-build\build\reports\qa\gametest-v1-passed.log`；JUnit HTML 报告在同一 build 目录的 `reports/tests/test/index.html`。

## 已发现并修复

- 首轮服务端验收为 5/6 通过，完整链卡在胚种。原因是配方匹配允许忽略无关输入槽，凝露配方抢先消费了胚种配方中的石英/红石。现在每个非空输入槽必须属于配方声明的 Ingredient，仍支持同种材料多份堆叠及重叠 Ingredient 的正确分配；增加了明确回归断言，保留原有产量与时限。失败原始日志保存在 `reports/qa/gametest-initial-failed.log`。
- 自查修复输入编辑后立刻注销/保存可能保留旧加工进度的问题；队伍首树记录也改为只能由萌发提交路径登记。
- 命令参数使用 ResourceLocation 解析完整 ID。补充命令测试最初假定原版单人玩家参数接受裸 UUID，后来改为标准 `@s` 单人选择器，保留正反结果断言。
- 构建初期失败于共享 NeoForm 缓存失效链接及 E 盘不足。只将本项目本次生成的缓存/产物移到 `D:\MC\skycore-build` 并改用本项目缓存，未删除共享缓存或其他项目文件。

## 尚未验收与下一步

- 当前自动测试加载核心、FTB Teams 及其依赖，使用从 pack 导入的真实配方 JSON；没有启动原实例全部模组、KubeJS 引擎或 FTB Quests。Java API/快照及配方契约已测，实际 advancement/任务桥接仍需在原实例安装 JAR 后核对。
- 图形客户端工作台显示、点击与网络界面尚未人工验证；客户端代码已编译，专服实际启动未加载客户端专有类。
- 状态恢复验证是压缩 NBT 的真实磁盘读写及方块实体重建，未冒充完整正常专服 JVM 退出再启动测试，也未测异常断电恢复。
- 64 组维度、生态供养/衰退、门户与跨队交易执法尚未实现，20–40 人/约 10 活跃队性能未测。本版不应被当成完整隔离/生态系统上线。
- 已向频道只读审查成员交接，但其任务状态失败且没有反馈，未取得独立审查通过结论。

整合包侧安装交付 JAR 到**原有** `minecraft/mods` 后，沿用现有脚本与只读轮询，不需要新增 advancement 或改名。首版管理员先完成组队，在选定现有起点给予每队岩核，再走首章确认 `world_fuel` 和 `first_tree` 展示；核心不会从任务完成状态反向授予权限。
