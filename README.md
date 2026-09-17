# 一加侧键功能

包名：`com.slimenull.customsidebuttonfunctions`

这是一个 Compose 配置界面 + Xposed/LSPosed 模块。默认监听按键码 `735`，设备路径记录为 `/dev/input/event0`。在 Oplus/一加系统中优先拦截 `com.android.server.policy.StrategyActionButtonKeyLaunchApp.actionInterceptKeyBeforeQueueing(...)`，这是系统快捷键策略实际使用的入口；非 Oplus 框架再回退到 `PhoneWindowManager.interceptKeyBeforeQueueing`，最后才尝试解析 Linux `input_event`。

## 构建

```powershell
gradlew.bat :app:assembleDebug
gradlew.bat :app:assembleRelease
```

Debug APK 输出在 `app/build/outputs/apk/debug/app-debug.apk`，Release APK 使用仓库内 `keystore/custom-side-button.jks` 签名。该密钥是本项目新生成的随机密钥，密码只用于本仓库的 Gradle 配置。

## 使用

1. 安装 APK，在应用中设置单击、双击、长按动作和判定时间后点击“保存设置”。
2. 在 LSPosed/Vector 中启用本模块，作用域选择 Android 系统框架（`android`）。
3. 重启系统或重新启动系统界面进程。
4. 若使用免打扰动作，先在应用内打开“免打扰权限”并授予访问权限。

双击动作选择“禁用”时，单击在松开后立即执行；选择任意双击动作后，单击会等待配置的双击窗口。长按动作在按住达到设定毫秒数时执行，不需要松开。

## 权限与系统限制

- 响铃、振动、手电筒、相机和系统截屏动作由系统框架进程执行，模块必须作用域到 Android 系统框架。
- 某些一加版本会限制后台启动相机或自定义 Toast；遇到这种情况请确认系统框架作用域和电池优化设置。
- 截屏优先调用系统 `StatusBarManager` 隐藏接口，同时向 SystemUI 发送兼容广播。具体可用接口由 ROM 版本决定。
