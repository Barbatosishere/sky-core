# Sky Core 0.2.0 六章主线

本轮保留0.1.4世界燃料、露天加工、流动水洗涤与旧首章数据。增加独立 `JourneyMilestone` 和 `JourneyMilestones` 保存字段，旧 schema 1、六个首章里程碑及配方ID保留。64组队伍维度与供养状态保存于 `skycore_journey_worlds`，由玩家显式进入，不自动搬迁原存档。

后续记录来自飞行宝石实际升空、复苏仪世界效果或真实商人交易。世界复苏仪输入核心/燃料，输出堵塞、露天不满足或前置缺失时暂停；完成后提交产物与里程碑。寒区写入真实群系并发送更新。玩家跨队传送被拒绝，队伍切换关闭容器并在玩家刻转移至新队世界。

逐项结果、最终制品摘要和原始日志以整合包的 `docs/pack/mainline-verification.json` 和 `D:/MC/skycore-build/pack-mainline-0.2.0` 为准。复现入口：

```powershell
.\gradlew.bat check jar --offline --no-daemon
.\gradlew.bat runGameTestServer --offline --no-daemon --init-script D:/MC/skycore-build/pack-mainline-0.2.0/gametest.init.gradle
.\gradlew.bat runJourneyTestServer --offline --no-daemon
```

标准GameTestServer只创建原版三个维度，因此额外测试在独立的 `journeyTest` 专服源集中运行。此源集、测试用配方/结构和重启探针均不进入发布JAR。`run_journey_tests.py` 通过本地RCON逐项运行四项新增测试，并读取测试通过监听器写入的结果；完整流程通过原生商人菜单支付与取货。首章前置、仪式材料和职业由夹具提供，不将其报告为从零生存通关。

重启测试正常停止原JVM后启动相同QA世界，检查世界槽、文明里程碑、实际寒区与未消耗凝霜核心。早期读未加载寒区时，`LevelReader.getNoiseBiome` 返回生成器默认群系；检查现已显式加载目标区块。原始失败日志和崩溃报告保留。模拟玩家补齐原生心跳回复，天气断言等待下一次世界刻，冷启动自动化检查等待区块实际开始tick；没有放宽产品数量、时长或原测试预算。

完整整合包专服使用官方NeoForge 21.1.265生产入口和265个服务端JAR，客户端使用286个顶层JAR。所有QA存档均位于原实例外。真实客户端测试使用提供的测试材料，实际完成首章处理、种树、升空与雨雪；补齐普通收集任务和说明任务后核对FTB投影。未声称全部模组配方、跨模组物流/传送、长期多人负载或一小时完整生态计时已验收。独立频道复审仍待接单，不沿用0.1.1的历史pass。
