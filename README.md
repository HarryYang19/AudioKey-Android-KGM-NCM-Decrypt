# AudioKey Android

一个面向 Android 9（API 28）及以上版本的本地加密音频解密工具。首版聚焦两条可独立运行的解密链路：

- **网易云 NCM**：解密内嵌音频流，输出 MP3、FLAC、M4A、AAC、OGG、WAV 等可识别的原始音频数据。
- **酷狗经典 KGM / KGMA**：支持对应经典容器版本，解密后按音频文件头识别输出格式。

所有文件读写与解密都在 Android 设备本地完成。应用没有 `INTERNET` 权限，不包含 WebView、远程 API、遥测、账号登录、在线取钥或上传功能。

## 功能范围

- Android 9+，自动跟随系统深色 / 浅色主题。
- Material 3 风格界面；Android 12+ 尝试使用系统动态配色。
- 系统文件选择器支持多选；输出目录由用户通过系统目录选择器指定。
- 流式处理，避免把整首歌曲一次性载入内存。
- 按解密后音频头识别扩展名；同名文件自动追加序号，不覆盖已有文件。
- GitHub Actions 自动运行单元测试并构建可安装的 **已签名 Debug APK**。

## 首版边界

1. KGM 支持范围为经典 KGM/KGMA 容器。较新的 KGG 或依赖客户端私有数据库、账号密钥的格式不在本版本支持范围内。
2. NCM 当前只还原原始音频流，不写回 NCM 元数据和封面，不负责转码。
3. 应用不把 FLAC 转成 MP3，也不重新压缩音频；输出是解密后容器中原有的音频数据。
4. 个别客户端变体可能调整容器布局。遇到文件损坏、字段长度不合理或加密版本不匹配时，应用会报告失败，不会输出伪造的音频文件。

## GitHub 打包 APK

1. 在 GitHub 新建一个空仓库。
2. 解压本 ZIP，把其中所有文件和隐藏目录（尤其是 `.github`）上传到仓库根目录。
3. 推送到 `main` 或 `master`，或在仓库的 **Actions → Build AudioKey APK → Run workflow** 手动触发。
4. 工作流会运行 `testDebugUnitTest` 和 `assembleDebug`。
5. 打开该次 Action 运行页面，在 **Artifacts** 中下载 `AudioKey-debug-apk`，解压后安装 `app-debug.apk`。

`app-debug.apk` 是 Android Gradle Plugin 自动用 Debug keystore 签名的 APK，可以直接安装。它不是面向公开商店分发的正式 Release 签名；正式发布时应使用你自己的 keystore 与私有仓库 Secrets。

## 本地构建

需要 JDK 17、Android SDK Platform 35、Build Tools 35.0.0 和 Gradle 8.9：

```bash
gradle --no-daemon clean testDebugUnitTest assembleDebug
```

APK 输出位置：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 工程结构

```text
app/src/main/java/com/harryyang/audiokey/
  MainActivity.kt       # 文件选择、输出目录、批量任务与 UI
  NcmDecoder.kt         # NCM 流式解密
  KgmDecoder.kt         # KGM/KGMA 流式解密
  AudioSniffer.kt       # 输出音频格式识别
app/src/test/           # 解密器单元测试
.github/workflows/      # GitHub Actions APK 构建
```

## 隐私与安全

- Manifest 未声明网络权限。
- 通过 Storage Access Framework 获取用户明确选择的文件和输出目录权限。
- 解密过程使用应用私有缓存目录的临时文件；任务结束后删除临时文件。
- 不删除或修改原始加密文件。

## 法律提示

仅对自己合法取得且有权处理的音频文件使用。请遵守所在地法律及相关平台服务条款。项目不提供任何平台账号、会员或密钥获取功能。

## 开源致谢

- KGM/KGMA 核心掩码表与解密变换参考并移植自 [huangbao/MyKgmWasm](https://github.com/huangbao/MyKgmWasm)，MIT License，Copyright (c) 2025 huangbao。具体许可见 `THIRD_PARTY_NOTICES.md`。
- NCM 容器布局与解密算法依据公开格式研究及 [taurusxin/ncmdump](https://github.com/taurusxin/ncmdump) 等开源实现所记录的格式资料独立实现；本工程没有复制或依赖其可执行文件或运行时库。
