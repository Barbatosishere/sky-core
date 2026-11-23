# 原实例服务端联调复测

本轮已通过真实 NeoForge 服务端的两项整合 GameTest，退出码 **0**。核心旧入口现在默认调用原实例维护的行为测试，不再只执行一个没有断言的全量启动诊断。首次阻塞仍保留在 [历史报告](fullpack-smoke-2026-11-15.md)。

## 修复与范围

整合包成员已完成原实例的兼容处理：三个不兼容附属模组原样归档至 `minecraft/mods-disabled`；Mekanism 的 16 条单子项 intersection 配方改为等价普通标签；任务桥接修复 Rhino 的 `player.level` 属性调用、线性前置、旧 1/1 进度以及父任务/章节完成标记残留。本轮已核对归档 SHA256 和当前四个脚本，并保留这些修复。

本轮核心侧修改 `tools/run_fullpack_smoke.py`：默认复用 `D:\MC\PrismLauncher_minecraft\1.21.1(1)\tools\run_server_integration.py`，保留 `--raw-startup` 进行没有行为断言的原始全量诊断；`--prepare-only` 仍可生成校验快照。README 与旧报告同步指向当前入口。

未修改原实例的 JAR、脚本或配置，也未重建/替换已发布的 Sky Core 0.1.0 JAR。原实例当前启用 **286** 个顶层 JAR，服务端 QA 从原路径读取其中 **265** 个。仅 QA 类路径按官方文件摘要和服务端支持元数据排除 21 个客户端组件；它们仍留在原实例。这是服务端内容验证，不代表完整图形客户端启动通过。

## 实际结果

证据目录：`D:\MC\skycore-build\pack-integration\20261116T002316399566Z`。

| 检查 | 本轮结果 |
| --- | --- |
| 原生 Rhino/KubeJS 服务端脚本 | **4/4**，0 errors、0 warnings |
| 配方管理器 | **64 条**：48 主线 + 16 兼容替换；添加失败 0 |
| 原生 FTB/SNBT | 六章、30 个任务；里程碑均为 `CustomTask` |
| 实际 FTB party UUID 与只读快照、wrapper 目标队 UUID | 相同 |
| 双队隔离、线性前置及旧待完成值恢复 | 通过 |
| 原生 FTB `mergeData` 引入外队进度后，核心快照不变时重新清理 | 通过；关键子任务、父任务、章节标记均清理，普通任务和原队合法进度保留 |
| 展示层不能反向授予核心权限 | 通过 |
| GameTest | **All 2 required tests passed** |
| 启动器退出码 / 行为结果 | **0 / passed=true** |
| 原 `mods/config/kubejs` 前后文件清单差异 | **0** |

日志中的必要证据：

```text
Loaded 4/4 KubeJS server scripts ... with 0 errors and 0 warnings
Added 64 recipes, removed 42 recipes, modified 0 recipes, with 0 failed recipes
SKYPACK_INTEGRATION_DATA_PASS recipes=64
SKYPACK_INTEGRATION_TEAM_PASS
All 2 required tests passed :)
BUILD SUCCESSFUL
Exit 0; behavior pass=True; original files changed=[]
```

双队测试中实际快照的 `team_id`、FTB 目标队和 wrapper 队 UUID 均为 `59ba6811-6860-4a3d-85eb-069eff5db857`。成功条件同时要求两个行为成功标记、退出码 0 和原实例文件无变化。

这些测试使用 QA 控制的核心里程碑验证任务展示桥接，不把它当作真实首树操作证据。图形菜单、人工首章流程、正常专服跨 JVM 重启以及多人负载仍未验收。日志保留了若干上游非阻断错误，包括 AnvilCraft 缺少可选 KubeJS 插件类、空标签/可选资源诊断；本报告不声称全部模组功能或日志均已修复。

## 复现

```powershell
Set-Location -LiteralPath 'E:\mods\Sky Core'
python .\tools\run_fullpack_smoke.py
```

每轮在现有构建目录保存精确 `launch.json`、原始 `console.log`、源文件前后 SHA256 清单及 `result.json`。本轮完整输出另存于 `dist/fullpack-smoke-latest.console.txt`，摘要为 `dist/fullpack-smoke-latest.json`。旧的 `dist/fullpack-smoke-v1.*` 保持首次失败现场。
