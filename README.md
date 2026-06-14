# 米家设备状态测试 App

这是一个最小 Android 测试 App，用来读取一个 Home Assistant 实例里暴露出来的全部实体状态，适合用来测试“米家账号下设备状态读取”。

## 工作流程

```text
米家账号 / 米家设备
        ↓
Home Assistant Xiaomi Home 集成
        ↓
本 App 读取 Home Assistant REST API：GET /api/states
```

这个项目不直接在 Android App 里登录米家账号。原因是直接登录米家云涉及账号安全、区域、验证码、登录风控、接口变动等问题，维护成本高；测试阶段更适合让 Home Assistant 负责接入米家账号，本 App 只读取 HA 已经整理好的设备状态。

## App 功能

- 填写 Home Assistant 地址，例如：`http://192.168.1.20:8123`
- 填写 Home Assistant Long-Lived Access Token
- 一次读取 `/api/states` 的全部实体状态
- 支持关键词过滤，例如：`客厅`、`温度`、`湿度`、`lumi`、`xiaomi`、`switch`
- 支持“仅显示疑似米家实体”过滤
- 支持每 10 秒自动刷新
- 支持保存配置到本机 SharedPreferences
- 支持局域网 HTTP 明文访问
- 纯原生 Java Android，无第三方库，无 Compose

## Home Assistant 准备

1. 在 Home Assistant 中安装/启用 Xiaomi Home 或 Xiaomi Miot 等米家集成。
2. 登录你的米家账号，并确认设备已经出现在 HA 实体列表里。
3. 在 HA 左下角个人资料页创建 Long-Lived Access Token。
4. 打开 App，填入 HA 地址和 Token。
5. 点击“读取全部状态”。

如果你的 HA 主要只接入了米家设备，那么 App 显示的基本就是该米家账号下的设备状态。

如果 HA 里还接入了其他品牌设备，可以：

- 勾选“仅显示疑似米家实体”；
- 或使用关键词过滤，例如设备名、房间名、实体类型。

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

## 常见问题

### 1. 返回 401 / 403

通常是 Long-Lived Access Token 不对，或者没有复制完整。

### 2. 读取成功，但没有米家设备

先去 Home Assistant 后台确认 Xiaomi Home 集成已经成功导入设备。

### 3. 勾选“仅显示疑似米家实体”后为空

取消勾选再读一次。Home Assistant 的 `/api/states` 不一定会把实体来源直接写在状态里，所以这个过滤只能靠实体名、设备名、型号、厂商等关键词猜测。

### 4. HA 地址是 HTTPS 自签名证书无法连接

测试阶段建议先使用局域网 HTTP，例如：

```text
http://192.168.1.20:8123
```

### 5. 蓝牙温湿度计看不到

如果你的米家温湿度计是纯蓝牙设备，需要先确保它能同步到 Home Assistant。否则 Android App 通过 HA 也读取不到。
