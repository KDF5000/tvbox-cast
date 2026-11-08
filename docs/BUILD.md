# 构建与架构

## 源码位置

[`cast-launcher/src/local/tvbox/castlauncher`](../cast-launcher/src/local/tvbox/castlauncher) 是 Android 应用源码。
[`docs/site`](site) 是官网静态源码。源码公开快照标签为 `source-v2.0.1-beta.1`。

本次代码基于公开 `2.0.1-beta.1`，不包含旧华为 SenderService 兼容入口。
为了避免暴露设备资料，测试和离线预览的示例 IP 已替换；实际运行仍读取本机网络地址。
公开构建脚本不读取维护者发布私钥，也不会连接、安装或配置电视。

## 环境

APK 构建支持 macOS / Linux：

- Node.js 18 或更新版本，不需要 npm 依赖。
- JDK 17，`java`、`javac`、`jar`、`keytool` 在 PATH 中，或设置 `JAVA_HOME`。
- Android SDK：`platforms;android-34` 和 `build-tools;34.0.0`。
- `zip` 命令。
- 将 `ANDROID_SDK_ROOT` 设置为自己的 SDK 目录；也接受 `ANDROID_HOME`。

使用已安装并接受许可的 Android SDK，不将 SDK、android.jar 或工具二进制提交到仓库。
最低运行版本为 API 19，Manifest 的 targetSdkVersion 保持 28；编译 API 34 不代表跨设备兼容承诺。

```sh
git clone https://github.com/KDF5000/tvbox-cast.git
cd tvbox-cast
node cast-launcher/build.mjs
```

输出：`cast-launcher/build/tvbox-cast-2.0.1-beta.1-debug.apk`。
首次构建会在被 Git 忽略的 `cast-launcher/build/debug.keystore` 生成本机调试密钥。
这是开发签名，不是官方发布证书；不要把它当成生产密钥。构建不会安装 APK。

也可以只生成未签名、已对齐的 APK：

```sh
node cast-launcher/build.mjs --unsigned
```

输出文件名以 `-unsigned.apk` 结尾，需用自己的签名工具签名后才能安装。
官方发行 APK 的签名和构建时间信息不会随源码提供，不承诺字节级复现官方 APK。
不要用不同内容覆盖同一个已发布版本；修改后分发请递增 Manifest 的版本名和版本号。

## 测试

纯 Java 协议测试仅需 JDK 17：

```sh
sh cast-launcher/test.sh
```

涵盖 SOAP 控制、设备描述、子网限制、SSDP 请求响应、HTTP 端点与 GENA 事件订阅，共 152 项检查。
网络测试使用回环地址和临时端口，不向家庭局域网宣告设备，不调用 ADB。
它们不能替代真机的解码、字幕、遥控器、开机、待机和持续运行验收。

官网的静态内容和动效检查：`node --test test/site.test.mjs test/motion.test.mjs`。
GitHub Actions 会运行协议、离线预览、脚本语法和官网检查，不访问或操作电视。

## 离线界面预览

```sh
sh cast-launcher/preview.sh
```

输出在 `cast-launcher/preview-output`。预览复用 TvArtwork，通过本项目的 Java2D 适配层绘制。
`preview/android/graphics` 只用于桌面预览，不会编入 Android APK，不是从 Android SDK 拷贝的实现。
山湖背景及生成说明在 `cast-launcher/res` 和 `cast-launcher/background-generation.md`。
图标和横幅由 `cast-launcher/tools/ReleaseArtwork.java` 原创绘制，已提供 PNG 资源。

## 主要组件

| 文件 | 职责 |
| --- | --- |
| MainActivity / TvArtwork | 投屏首页、网络显示、设备名称、默认桌面引导与绘图 |
| BootReceiver / ReceiverService | 开机初始化、接收生命周期、网络变化与前台服务 |
| SsdpDiscovery | 标准 SSDP 发现、宣告与同子网响应限制 |
| DlnaHttpServer / DlnaDescription / DlnaProtocol | HTTP、设备描述、SOAP 控制与 GENA 事件订阅 |
| PlaybackState | 播放状态和会话代次管理 |
| PlayerActivity / PlayerOverlay | 系统播放器、遥控器跳转、字幕与播放条 |

默认 HOME 由用户和厂商系统决定；BOOT_COMPLETED 只启动接收服务，不强制后台弹出页面。
不需要 Root、华为系统接口或停用其他应用。接收就绪时持有 CPU 唤醒锁与多播锁，关闭服务或断网时释放。
使用 Android 原生 MediaPlayer / VideoView 解码，因此编码、TLS 和流媒体兼容性仍受固件限制。

## 安全与签名

当前没有 PIN 或配对授权，仅适用于可信家庭局域网。
同一子网控制端可以更换视频和控制播放；不要向公网映射端口。
公开反馈前移除媒体鉴权参数、个人网络信息和日志中的敏感内容。

发布签名私钥、设备备份、调试 APK 和系统维护脚本都不在本仓库。
自行构建的 APK 与官方包签名不同，不能直接覆盖安装；卸载可能丢失配置。
无需为了开发公开 ADB / Telnet，也不要把这些端口开放到公网。
