<div align="center">

# MeiloB

<img src="./app/src/main/res/drawable/logo.png" width="120px" style="border-radius:12px"/>

### 一个 Apple Music 风格的哔哩哔哩音乐播放器 Android 客户端

![Android](https://img.shields.io/badge/Platform-Android-green?logo=android)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-blue)
![License](https://img.shields.io/badge/License-GPLv3-orange)

</div>

---

> **项目来源**：MeiloB 分叉自 [NEORUAA/MeiloX](https://github.com/NEORUAA/MeiloX)（其上游为 [Mei](https://github.com/ljyh223/Mei)），在其基础上改造为纯 **哔哩哔哩音乐播放器**：
> - 首页读取 B 站音乐区（rid=3）排行榜与最新投稿；
> - 支持哔哩哔哩扫码登录与手机号短信登录（极验验证）；
> - 「我的账号」页可浏览并**同步个人收藏夹/合集**到本地歌单（离线可浏览、可播放）；
> - 底部搜索栏为 B 站全站视频搜索，结果可直接播放其音频流；
> - 网易云音乐相关界面与入口已全部移除，播放内容为 B 站视频的 DASH 音频流。

---

## 项目简介

MeiloB 使用 **Jetpack Compose** 构建，尝试把 iOS 27 的层次感、液态玻璃和沉浸式播放器体验带到 Android 上。

项目重点放在三个方向：

- 用 iOS 风格的页面结构重新组织 B 站音乐内容；
- 用 Liquid Glass、动态背景和专辑封面让播放过程更有沉浸感；
- 保留搜索、歌单、歌词和个人资料等熟悉的使用习惯。

> MeiloB 是非官方的个人开源项目，不隶属于哔哩哔哩或 Apple；项目本身不提供音乐资源，音乐内容及相关版权归其权利人所有。

## 功能概览

- 哔哩哔哩扫码 / 手机号短信登录与账号信息展示；
- B 站全站视频搜索，结果直接播放音频流；
- 音乐区排行榜、最新投稿与每日推荐；
- 收藏夹 / 合集浏览，可同步为本地歌单；
- 播放队列、随机播放、循环播放和定时停止；
- 播放历史、本地歌单管理；
- Apple Music 风格全屏播放器与迷你播放器；
- 状态栏歌词、悬浮歌词与桌面歌词共享；
- 浅色/深色主题、动态专辑背景和 Liquid Glass 交互效果。

功能仍在持续调整中，实际可用范围会随版本和哔哩哔哩接口状态变化。

## 歌词共享

已接入 [Lyricon Provider](https://tomakino.github.io/lyricon/zh-cn/developer/provider/)、[SuperLyricApi](https://github.com/HChenX/SuperLyricApi)、[ColorOS Live Lyrics Bridge](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Bridge) 与 Flyme 状态栏歌词接口，用于共享歌词、逐字时间、翻译及播放状态。

「设置 → 歌词设置 → 歌词共享」中的「启用歌词共享」默认开启。

## 构建

```
git clone https://github.com/Alittlejelly/MeiloB
cd MeiloB
./gradlew :app:assembleRelease
```

要求 JDK 21+ 与 Android SDK（minSdk 33）。release 构建启用 R8 混淆与资源压缩。

## 开源致谢

MeiloB 的界面和底层能力受以下项目启发或直接使用其开源组件，感谢所有贡献者：

- [MeiloX](https://github.com/NEORUAA/MeiloX)：本项目的直接分叉来源（Android 版）；
- [Mei](https://github.com/ljyh223/Mei)：上游 Android 项目与基础能力；
- [MeloX](https://github.com/youshen2/MeloX)：iOS 版参考实现与产品方向；
- [amll-ttml-db](https://github.com/Steve-xmh/amll-ttml-db)：高质量逐字歌词数据；
- [accompanist-lyrics-ui](https://github.com/6xingyv/accompanist-lyrics-ui)：歌词模型与渲染组件；
- [Lyricon](https://github.com/tomakino/lyricon)：词幕 Provider SDK；
- [SuperLyricApi](https://github.com/HChenX/SuperLyricApi)：歌词共享 SDK；
- [ColorOS Live Lyrics Bridge](https://github.com/Andrea-lyz/ColorOS-Live-Lyrics-Bridge)：ColorOS 锁屏歌词接口；
- [AndroidLiquidGlass / Backdrop](https://github.com/Kyant0/AndroidLiquidGlass)：Compose Multiplatform 流体玻璃效果。

## 许可证与第三方声明

MeiloB 主体代码以 [GNU General Public License v3](LICENSE) 发布。MeiloB 是 MeiloX / Mei 的衍生项目，相关上游代码和参考实现仍受其原始版权与许可证约束；第三方库、模型、脚本、字体和视觉素材不因 MeiloB 使用 GPLv3 就自动转为 GPLv3。

重点声明如下：

- [Mei](https://github.com/ljyh223/Mei) 的上游代码遵循 Apache License 2.0；
- [MeiloX](https://github.com/NEORUAA/MeiloX) 的相关代码遵循 GPLv3；
- [MeloX](https://github.com/youshen2/MeloX) 的相关参考代码遵循其项目的 GPLv3 条款；
- [AndroidLiquidGlass / Backdrop](https://github.com/Kyant0/AndroidLiquidGlass) 相关依赖遵循 Apache License 2.0；
- [amll-ttml-db](https://github.com/Steve-xmh/amll-ttml-db) 与 [accompanist-lyrics-ui](https://github.com/6xingyv/accompanist-lyrics-ui) 遵循各自仓库公布的许可证和署名要求；
- BeatNet 模型、音频指纹运行时、ONNX Runtime、PV Tool 衍生内容及其它随项目分发的组件，遵循其各自的许可证、版权声明和再分发限制。

完整的来源、版权归属、许可证链接和特殊再分发要求请阅读 [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES)。其中 `res/font/sf_pro.ttf` 仅作为个人项目资源使用；请勿从构建产物中单独提取或再分发，公开发布构建版本前请重新核对 Apple 的字体许可条款。

## 免责声明

本项目仅用于学习、研究和个人使用。使用本项目访问哔哩哔哩服务时，请遵守当地法律法规、哔哩哔哩用户协议及相关版权要求。项目维护者不对第三方服务的可用性、账号状态或音乐内容承担责任。

如果你发现侵权内容、许可证遗漏或不准确的第三方声明，欢迎通过 [Issues](https://github.com/Alittlejelly/MeiloB/issues) 联系维护者。

<div align="center">

Made with Jetpack Compose · MeiloB

</div>
