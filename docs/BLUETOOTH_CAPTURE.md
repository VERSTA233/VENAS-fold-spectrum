# 蓝牙采样停顿与显示同步 · 2026-11-20

## 现象与证据

Find N6（OPPO PLP110，Android 17/API 37）在网易云音乐、A2DP、48kHz输出时，Visualizer回调正常到达，原始FFT和原始波形却周期性重复约307–310ms。用户当场指出停顿，30秒录屏中WAVEFORM约每1.37秒出现一次近静止片段。绘制统计4762帧、当前口径0帧超时，99分位14ms，不支持主线程整体冻结的解释。

1.3653秒恰好对应65536/48000。对照开源实现，AIDL版Visualizer在带延迟回读的环形缓冲跨界分支，存在把读取起点固定到缓冲尾部的代码；经典实现使用原始负偏移。该机制与本机周期、持续时间匹配，但没有读取或反编译本机系统二进制，不能声称已经证明厂商源码完全相同。

- [复现录屏](evidence/nov20-visible-stall.mp4)、[采样日志](evidence/nov20-visible-stall.log)、[绘制统计](evidence/nov20-visible-gfx-after.txt)。
- 开源对照：[AIDL VisualizerContext.cpp](https://github.com/LineageOS/android_frameworks_av/blob/lineage-23.0/media/libeffects/visualizer/aidl/VisualizerContext.cpp)、[经典 EffectVisualizer.cpp](https://github.com/LineageOS/android_frameworks_av/blob/lineage-23.0/media/libeffects/visualizer/EffectVisualizer.cpp)。两个实现的缓冲大小也可能不同，不将源码常量当作本机已验证参数。

## 两步修正

1. 只改变Visualizer分析效果的`VISUALIZER_PARAM_LATENCY`（参数2），让它读取最新真实样本。实验返回status=0；约40秒持续播放的原始FFT/波形重复时长均为0。25秒录屏不再出现原来周期性的波形平台。用户确认“不卡了，但画面明显抢拍”。这证明仅取消补偿还不足以完成修复。
2. 应用读取`AudioManager.getOutputLatency(STREAM_MUSIC)`，本机本轮报告369ms。在采集线程中，把波形、FFT、LEVEL/DYNAMIC的同周期快照按时间顺序延后发布。保持采样间隔、幅度和原有绘制响应，不增加平滑阻尼。用户在安装同步补偿版后确认“连续，节奏也基本对齐”。

缓冲只保存短时分析快照，不保存PCM音频。迟到的消费者丢弃已经过期的显示帧，避免越播越晚；新曲、重新采集、释放/失败时清空待显示帧。暂停状态仍立即显示PAUSED并回落。LEVEL/DYNAMIC内部测量不降频，显示数值继续每约500ms更新。

证据：[无延迟实验](evidence/nov20-zero-latency.log)、[录屏比较](evidence/nov20-latency-comparison.json)、[同步版日志](evidence/nov20-sync-capture.log)、[同步版录屏](evidence/nov20-synchronized.mp4)。同步版录屏期间用户进行了切歌，静音/缓冲区间不能按停顿计数；日志分别统计全零重复与非零重复。

## 范围与退路

- 仅OPPO PLP110 / API 37，且系统媒体路由全部指向A2DP时启用。通过公开`getAudioDevicesForAttributes(USAGE_MEDIA)`查询媒体输出，不把“耳机已连接”当作正在向耳机播放。扬声器、混合输出、未知路由保持标准Visualizer时序，不应用这段应用侧延迟。
- 设备连接回调触发重建；采集存续期间每秒核对媒体路由，覆盖耳机保持连接但声音切到手机扬声器的情况。重建时重新获取延迟并清空显示队列。未主动更改用户路由做这条实机切换测试。
- `getOutputLatency`、AudioEffect构造器及参数写入使用当前ROM仍可访问的非SDK兼容接口，不修改隐藏API策略，不用ADB权限代替产品权限。查询失败、延迟不在1–3000ms内或参数不可写时，记录诊断并保持标准采集；其他品牌/API也使用标准路径。ROM升级后需要重新验证。
- 系统延迟是估计值，不保证所有耳机/编码器下达到严格声画同步。369ms是本轮稳定播放读取值，不是写死的通用常量；另一次恢复初期读到129ms，后续变为369ms。首个非零采样和之后每秒重新核对估计值；变化达到20ms时整体平移待显示帧时间，避免长期保留启动临时值，小于20ms的估计抖动忽略。听感确认来自网易云+A2DP；收尾已补齐Poweramp+A2DP短时连续性及快速暂停恢复回归（详见VALIDATION），两个扬声器有声组合和Poweramp严格听感同步仍待补齐。
- 未改变音乐播放链路、音量、蓝牙设置、FFT分组、频谱100/200ms响应或WAVEFORM35/90ms响应。没有合成音频波动。

## 自动验证

FrameDelay的5项测试覆盖：瞬态幅度与51ms采样间隔保持、不到时不发布、迟到不积压、新曲/恢复清空旧帧、零延迟和相同时间戳、输出延迟变化后的重新定时。连同原有27项、物理姿态解析3项及新增封面动效/透视9项，共44项JVM测试；构建/lint与最终安装校验值见[build-result.json](evidence/build-result.json)。

## 收尾清理

早期保留暂停Visualizer 1500ms的尝试未解决原故障，最终已删除。暂停直接释放，恢复重新建立；11月20日17:52最终实测每次重建均启用369ms兼容，稳定播放未见非零重复帧。后台1500ms折叠恢复宽限期用途不同，继续保留。
