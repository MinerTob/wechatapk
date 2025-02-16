# WeChat APK Installer 🔄

[![GitHub license](https://img.shields.io/github/license/MinerTob/wechatapk)](https://github.com/MinerTob/wechatapk/blob/main/LICENSE)
[![Android CI](https://github.com/MinerTob/wechatapk/actions/workflows/android.yml/badge.svg)](https://github.com/MinerTob/wechatapk/actions/workflows/android.yml)

专为处理微信分享APK文件设计的智能安装工具，解决.apk文件被重命名导致的安装问题。

![应用截图](screenshots/main_activity.png) <!-- 需要添加实际截图 -->

## 功能特性 ✨

- 🔍 智能识别微信修改的APK文件（如.apk.1）
- ✅ 通过ZIP文件头验证APK有效性
- 📥 自动修复文件扩展名
- 🔒 安全权限管理（仅请求必要权限）
- 🌐 内置GitHub仓库直达入口

## 使用说明 📖

1. 通过微信接收APK文件
2. 选择"用其他应用打开"
3. 选择本应用
4. 授予必要权限
5. 自动完成安装

**注意**：首次使用需要开启"未知来源应用"安装权限

## 技术实现 🛠️

- 最低支持Android 7.0 (API 24)
- 使用FileProvider处理文件URI
- ZIP头验证算法：
  ```java
  byte[] header = new byte[4];
  return header[0] == 0x50 && header[1] == 0x4B && header[2] == 0x03 && header[3] == 0x04;
  ```
- 智能重命名策略：
  - 保留原始文件名
  - 自动追加.apk扩展名
  - 使用应用私有目录存储临时文件

## 安装方法 📲

### 从Release下载
1. 访问 [GitHub Releases](https://github.com/MinerTob/wechatapk/releases)
2. 下载最新版APK
3. 按照系统提示安装

### 从源码构建
