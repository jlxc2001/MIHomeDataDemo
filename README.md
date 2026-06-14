# 米家温湿度测试 App

这是一个最小 Android 测试 App，用来读取 Home Assistant 中的米家温度/湿度实体。

## 工作流程

```text
米家温湿度设备 → Home Assistant Xiaomi Home 集成 → 本 App 读取 HA REST API
```

这个项目不直接登录米家账号，也不直接调用米家云接口。原因是米家登录/云接口在第三方 App 里维护成本较高，而 Home Assistant 已经可以作为稳定中间层。

## App 功能

- 填写 Home Assistant 地址，例如：`http://192.168.1.20:8123`
- 填写 Home Assistant Long-Lived Access Token
- 填写温度实体 ID，例如：`sensor.living_room_temperature`
- 填写湿度实体 ID，例如：`sensor.living_room_humidity`
- 支持读取一次
- 支持每 10 秒自动刷新
- 支持保存配置到本机 SharedPreferences
- 支持局域网 HTTP 明文访问

## Home Assistant 准备

1. 在 Home Assistant 中安装/启用 Xiaomi Home 或 Xiaomi Miot 等米家集成。
2. 确认温湿度设备已经出现在 HA 实体列表里。
3. 在 HA 左下角个人资料页创建 Long-Lived Access Token。
4. 复制温度/湿度实体 ID。
5. 打开 App 填入地址、Token、实体 ID 后点击读取。

## GitHub Actions 在线编译

把本项目完整上传到 GitHub 仓库根目录，然后进入：

```text
Actions → Build Android APK → Run workflow
```

编译完成后，在 workflow 的 Artifacts 里下载：

```text
MiHomeClimateTest-debug-apk
```

里面的 `app-debug.apk` 就是测试安装包。

## 手动编译命令

如果你本地有 Android SDK 和 Gradle，也可以执行：

```bash
gradle :app:assembleDebug --stacktrace
```

APK 路径：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## 注意

- 如果 HA 地址是 `https://` 并且使用自签名证书，Android 默认可能会拒绝连接。测试阶段建议先用局域网 `http://HA_IP:8123`。
- 如果返回 401/403，通常是 Token 不对或没有复制完整。
- 如果返回 404，通常是实体 ID 写错了。
- 如果你的米家温湿度计是纯蓝牙设备，需要先确保它能同步到 Home Assistant；否则 App 本身无法直接读取。
