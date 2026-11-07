# AudioKey-Android-KGM-NCM-Decrypt
一个面向 Android 9（API 28）及以上版本的本地加密音频解密工具(仅供学习研究)。聚焦两条可独立运行的解密链路：  - 网易云 NCM：解密内嵌音频流，输出 MP3、FLAC、M4A、AAC、OGG、WAV 等可识别的原始音频数据。 - 酷狗经典 KGM / KGMA：支持对应经典容器版本，解密后按音频文件头识别输出格式。  所有文件读写与解密都在 Android 设备本地完成。应用没有 `INTERNET` 权限，不包含 WebView、远程 API、遥测、账号登录、在线取钥或上传功能。
