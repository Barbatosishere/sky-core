# 原实例全模组无界面启动证据（2026-11-15）

> 本文保留首次启动失败的历史现场。后续兼容修复及最新原生联调结果见 [当前联调记录](fullpack-smoke-verified.md)。`run_fullpack_smoke.py` 现在默认调用实例的原生行为测试入口；本文的无过滤诊断模式需加 `--raw-startup`。

结论：**启动失败，未进入脚本/任务行为验收。** 本轮读取了原实例全部 289 个安装 JAR，不是之前仅核心与 FTB Teams 的七项 GameTest。

## 运行范围和原实例保护

- 原实例：`D:\MC\PrismLauncher_minecraft\1.21.1(1)\minecraft`。
- Java Microsoft 21.0.10；Minecraft 1.21.1；NeoForge 21.1.265；Gradle 9.3.0 / ModDev 2.0.142。
- 入口：`tools/run_fullpack_smoke.py`，按需加载 `gradle/fullpack-smoke.gradle`。正常核心构建不启用此配置。
- 单独的 `packSmoke` source set 不继承开发核心或旧 QA 的产物；`loadedMods=[]`。运行时 classpath 直接引用原 `mods/*.jar`，不筛选、降级或禁用任何 JAR，也不向原 `mods` 复制 QA JAR。
- 为避免首次启动回写原配置，工作目录使用现有构建区中的 `D:\MC\skycore-build\fullpack-smoke\20261115T175853Z\game`。原 `config` 与 `kubejs` 逐文件复制并校验 SHA256，其中包括真实的三个 server scripts 和十个 FTB Quests SNBT 文件。没有创建或注册另一个 Prism 实例。
- 使用真实 Minecraft/NeoForge 服务端启动链 `forgeserverdev`，非 Rhino mock、Node 模拟或静态配方计数。开发启动环境与正式 Prism 客户端运行条件不同。
- 完整运行前后，原 `mods/config/kubejs` 所有记录的文件内容变化数 **0**；原 `saves` 仍为空；本次运行进程已退出。
- 原发布核心 JAR 大小 78,494 字节，SHA256 `4f1813259d32b5f9919a100a4a50e7091a58586a64944e1c4c29889befe7b67d`。

## 首个强制依赖阻塞

NeoForge `ModSorter` 原始日志：

```text
Missing or unsupported mandatory dependencies:
Mod ID: 'anvilcraft', Requested by: 'anvilcraft_kubejs',
Expected range: '[1.5.0,1.5.9999)', Actual version: '1.6.0+snapshot.2906'
```

已直接读取这两个原始 JAR 的 `META-INF/neoforge.mods.toml`：

| 原文件 | 记录 |
| --- | --- |
| `anvilcraft_kubejs-neoforge-1.21.1-1.0.0+build.8.jar` | `anvilcraft` 是 `required` / `BOTH`，范围 `[1.5.0, 1.5.9999)` |
| `anvilcraft-neoforge-1.21.1-1.6.0-snapshot.2906.jar` | mod 版本 `1.6.0+snapshot.2906` |

同次启动最终抛出 `NoClassDefFoundError: de/melanx/skyblockbuilder/util/CoreUtil`，栈从 `WorldPreset.<clinit>` 经 `Bootstrap.validate` 到 `Main.main`，Java 与 Gradle 均返回 **1**。没有把这个后续异常直接归因到某一个上游修复；完整日志一并保留。更早的伴随日志还有 Fabric JAR 跳过及 `irisflw` 缺少 access transformer，不能因此声称这些模组已经通过服务端加载。

依照本次交接要求，停在原生加载阻塞，未修改其他模组或放宽依赖声明。

## 验证状态

| 检查 | 本轮结果 |
| --- | --- |
| 读取原目录全部 289 个安装 JAR，扫描包含 Sky Core、KubeJS、Rhino 和 FTB 系列 | 已执行；存在强制依赖错误，不等于全部成功加载 |
| 现场脚本与 FTB 配置快照和源文件内容一致 | 通过；15 个 `config/kubejs` 文件逐一校验 |
| 原 `mods/config/kubejs` 保持原内容 | 通过；前后清单差异 0 |
| 完整服务端启动 | **失败**；退出码 1 |
| Rhino 执行实际 pack 脚本 | **未执行**；服务端未完成引导 |
| 48 条 pack 配方实际进入 RecipeManager | **未执行** |
| FTB `custom` 任务解析及 wrapper 的十六进制 ID 调用 | **未执行** |
| `snapshotJson` 与实际 FTB team_id 一致、同队同步/跨队隔离/复制污染复位 | **未执行** |
| 图形菜单、人工游玩 | **未执行** |

没有把旧版核心的 4 条 JUnit / 7 条 GameTest 或 pack 的 Node 测试算作本轮接口证据。入口当前只负责全模组启动门槛；必须先排除启动阻塞，才能接入并执行服务端配方与队伍行为断言。

## 复现和证据

在 PowerShell 中执行：

```powershell
Set-Location -LiteralPath 'E:\mods\Sky Core'
python .\tools\run_fullpack_smoke.py --raw-startup
```

该命令会重新读取原 JAR，生成一个新的带 UTC 时间的构建证据目录，不修改原 Prism 文件。只准备/校验现场快照可加 `--prepare-only`。Gradle 实际启动参数记录于本次证据目录的 `launch.json`。

完整退出证据以 **`20261115T175853Z`** 这次为准：

- `D:\MC\skycore-build\fullpack-smoke\20261115T175853Z\console.log`：完整标准输出和最终 cause 链。
- 同目录 `game\logs\latest.log`、`debug.log`：模组扫描和依赖诊断。
- 同目录 `original-before.json`、`original-after.json`：原文件大小和 SHA256 清单。
- 同目录 `launch.json`、`process.json`、`result.json`：路径、精确命令、启动 PID 和退出码。
- 工程内便携证据：`dist/fullpack-smoke-v1.console.txt`、`dist/fullpack-smoke-v1.json`。

之前两个构建证据目录分别保留了 source set 的 Minecraft 依赖设置问题、控制台 GBK 编码中断；它们不是行为验收记录。修正的仅是测试入口，未改变原模组集合。最终这次完整收集到退出码和未修改原文件的证据。
