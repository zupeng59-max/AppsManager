# AppsManager

AppsManager 是一个本地优先的 Android 应用管理/启动工具。它会读取手机里可启动的 App，按分类展示，并允许你通过搜索、常用、隐藏等方式更快找到目标 App。

这个项目的目标很简单：当手机 App 太多时，不再反复翻桌面、抽屉或系统搜索，而是打开 AppsManager 后直接点对应 App。

## 功能

- 自动读取手机里可启动的 App
- 点击 App 卡片直接打开对应 App
- 按 App 名称或包名搜索
- 分类筛选：全部、常用、AI、学习、娱乐、社交、支付、购物、出行、工具、其他、隐藏
- 长按 App 管理：设为常用、修改分类、隐藏、打开系统应用信息
- 根据常见关键词自动推断初始分类
- 误隐藏后可在“隐藏”分类里恢复
- 分类、常用、隐藏状态保存在手机本地

## 隐私

AppsManager 没有账号、后端、统计 SDK 或主动网络请求。它只读取 Android 系统公开的可启动应用信息，包括 App 名称、图标、包名和启动 Activity，用于本地展示和跳转。

用户的分类、常用和隐藏设置保存在当前手机的本地应用数据中。卸载应用或清除应用数据会删除这些设置。

## 技术栈

- Android 原生 Java
- Android 系统控件
- Gradle / Android Gradle Plugin
- 最低支持 Android 8.0，API 26
- 目标 SDK：Android 16，API 36

项目没有引入第三方 UI 库。Android 11 以后系统限制应用查看其他已安装应用，因此 Manifest 只声明了查询可启动应用的 intent，不使用 `QUERY_ALL_PACKAGES`。

## 目录结构

```text
.
├── app/
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/local/applauncher/MainActivity.java
│       └── res/
├── gradle/
├── build.gradle
├── gradle.properties
├── settings.gradle
└── README.md
```

## 构建

先安装 Android Studio，或安装 Android CLI / SDK。需要 Android SDK Platform 36 和 Build Tools 36.0.0。

在项目根目录执行：

```powershell
.\gradlew.bat assembleDebug
```

生成的调试 APK：

```text
app/build/outputs/apk/debug/app-debug.apk
```

也可以用 Android Studio 打开项目，等待 Gradle Sync 完成后，连接 Android 手机并点击 Run。

## 使用

1. 安装 APK 到 Android 手机。
2. 打开 AppsManager。
3. 点击 App 卡片直接启动 App。
4. 使用顶部搜索框快速查找 App。
5. 点击分类标签筛选 App。
6. 长按 App 修改分类、设为常用、隐藏或打开系统应用信息。

## 当前状态

这是个人自用方向的 MVP 版本，重点是稳定读取、搜索、分类和启动 App。后续可以继续加入拖拽排序、自定义分类、图标大小设置、导入导出配置等功能。
