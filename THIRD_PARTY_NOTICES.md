# 第三方与素材说明

项目自有代码和文档按根目录 [MIT 许可证](LICENSE) 提供。
MIT 不覆盖第三方组件本身的许可，也不授权他人的商标、视频内容或 Android SDK。

## Android 应用

应用使用 Android / Java 平台 API，没有打包第三方播放器、广告、分析或账号 SDK。
Android 系统和开发 SDK 按各自条款使用，不随仓库分发。
`cast-launcher/preview/android/graphics` 是本项目为离线绘图编写的 Java2D 适配层，不编入 APK。

投屏图标和 TV 横幅由本项目代码绘制，未使用平台或厂商商标。
山湖背景是项目生成的虚构风景，未引用第三方摄影作品；生成说明见
[`cast-launcher/background-generation.md`](cast-launcher/background-generation.md)。
项目自有素材随项目开放使用；AI 生成不等于保证独占著作权或不存在任何第三方权利争议。

## 官网

- Geist / Geist Mono 来自 `geist` 1.5.1，按随附的 [SIL Open Font License](docs/site/assets/GEIST-LICENSE.txt) 使用。
- Phosphor regular 图标来自 `@phosphor-icons/core` 2.1.1，按随附的 [MIT License](docs/site/assets/PHOSPHOR-LICENSE.txt) 使用。
- `docs/site/assets/standby-preview.png` 为离线界面示意图，不包含真实设备或网络资料。

字体和图标本地托管。中文使用设备本地字体，仓库不打包这些系统字体。
