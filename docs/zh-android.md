# Bitwarden 验证器版（Android）

这是官方 Bitwarden Android 客户端的一个并存分支。应用名是「Bitwarden 验证器版」，包名是 `com.minxiangnan.bitwarden.zh`。它可以和官方客户端装在同一台手机上。登录条目和验证器条目都放在同一台自建服务器上，和浏览器扩展用同一套接口、同一套用户密钥加密，所以手机和扩展看到的是同一份验证码。

对应的扩展是 `v-zh-authenticator-1.1.1`，服务器是 Vaultwarden `v-zh-authenticator-1.2.2`。

## 安装

1. 卸载旧的测试包（如果有）。本分支不会覆盖官方 Bitwarden。
2. 安装 `arm64-v8a` 的发布包。系统会提示未知来源，这是预期的。如果手机上还装着 1.0.0 的调试签名包，要先卸载。
3. 打开应用。服务器地址留空时会显示占位符。填入你的服务器，必须是 `https://` 开头。只填域名时会自动补上 `https://`。明文 `http://` 会被拒绝，并提示需要 HTTPS，自己安装的 CA 也可以。
4. 填邮箱，点「继续」，再填主密码。没有公开注册、地区选择、单点登录和通行密钥入口。管理员邀请过的邮箱，用登录页下方的「收到邀请？创建账户」。
5. 首次解锁后，保险箱超时默认是「从不」，超时动作是「锁定」。不会自动上锁，除非你自己改。URI 匹配默认是「主机完全一致」。这些设置会写进本地，重启和更新后还在。
6. 系统设置里把「Bitwarden 验证器版」设为自动填充服务。名称和官方客户端不同。深链是 `bwzh://`，不会抢官方的 `bitwarden://`。

调试包的包名是 `com.minxiangnan.bitwarden.zh.dev`，Beta 包是 `com.minxiangnan.bitwarden.zh.beta`。

### 签名

1.0.0 用调试密钥签过。1.0.1 起用单独的发布密钥。Android 不允许用新密钥覆盖旧签名，所以从 1.0.0 升级必须先卸载再安装。卸载会清掉本机缓存；服务器上的条目还在。

密钥和口令不进仓库，也不进 GitHub Release。构建时从环境变量或 `user.properties` / `gradle.properties` 读取：

| 名字 | 含义 |
| --- | --- |
| `ZH_KEYSTORE_PATH` | keystore 文件的绝对路径 |
| `ZH_KEYSTORE_PASSWORD` | 库口令 |
| `ZH_KEY_ALIAS` | 密钥别名 |
| `ZH_KEY_PASSWORD` | 密钥口令 |

本地重编：

```bash
export ZH_KEYSTORE_PATH=/安全的位置/zh-authenticator-release.keystore
export ZH_KEYSTORE_PASSWORD='库口令'
export ZH_KEY_ALIAS=zhauthenticator
export ZH_KEY_PASSWORD='密钥口令'
./gradlew :app:assembleStandardRelease
```

把 keystore 和口令放在仓库外面，单独备份。丢了就不能给已安装的用户发更新。

GitHub Actions 在推送 `v-zh-authenticator-android-*` 标签时编发布包。仓库密钥要先加好，否则工作流会失败：

| Secret | 内容 |
| --- | --- |
| `ZH_KEYSTORE_BASE64` | `base64 -w0 zh-authenticator-release.keystore` 的整段文本 |
| `ZH_KEYSTORE_PASSWORD` | 库口令 |
| `ZH_KEY_ALIAS` | `zhauthenticator` |
| `ZH_KEY_PASSWORD` | 密钥口令 |
| `BW_PACKAGES_TOKEN` | 能读取 `bitwarden/sdk` GitHub Packages 的个人访问令牌（`read:packages`）。Fork 自带的 `GITHUB_TOKEN` 读不了那个仓库。 |

工作流文件是 `.github/workflows/zh-authenticator-release.yml`。产物是按 CPU 拆开的 APK，手机用 `arm64-v8a`。另有一个包含全部架构的 universal 包。Release 打开了 R8 和资源压缩，原生库在包里是压缩存放的。

### 收到邀请后创建账户

公开注册保持关闭。管理员在 `/admin` 邀请一个邮箱之后，登录页下方有「收到邀请？创建账户」。只填这个邮箱、名字和主密码。服务器拒绝其他邮箱时，应用显示「这个邮箱没有收到邀请，或已经注册过了」。

## 能做什么

- 底部有「验证器」页。可以扫二维码、从图片读二维码、粘贴 `otpauth://`，或手填密钥。支持 TOTP、HOTP 和 Steam。
- 点一下复制当前验证码。卡片上有倒计时和下一个验证码。可以搜索、按发行方分组、按名称或发行方排序、只看收藏。
- 编辑、移入回收站、从回收站恢复、彻底删除。删除和彻底删除都会先确认，标题带条目名称，确认按钮是危险色，取消是默认按钮。
- 导入 Google Authenticator 迁移码、未加密的 Aegis、2FAS 和 Bitwarden JSON/CSV，以及本分支自己的未加密导出。加密的 Aegis / 2FAS 文件会拒绝导入。导出文件含明文密钥，只适合迁移。
- 每条验证码可以打开「允许 MCP 读取」并填写别名。HOTP 不能交给 MCP。设置里有「MCP 访问」：创建令牌、只显示一次、复制、轮换、吊销。令牌明文不落盘，服务器上存的是用户密钥加密后的副本。轮换后会用新密钥重新包装已放行的条目。
- 自动填充填完密码后，如果登录条目自己有 TOTP，或验证器里有和该主机匹配的条目，会把验证码复制到剪贴板。Android 没有通用的验证码填充字段，所以这里是复制，不是写进输入框。
- 保存提示：同一主机下的新用户名会新建登录；同一用户名但密码变了，只更新密码，备注和验证码保留；完全相同则不提示。点「此网站永不」会再确认一次。
- 界面默认简体中文。配色是奶油底、陶土色强调（`#D97757`），深色模式用暖棕色，不用纯黑。
- 生物识别解锁沿用原客户端的能力。要在账户安全里打开。默认不自动锁定，所以平时不会先看到锁屏。
- 默认禁止截图和在最近任务里露出验证码（`FLAG_SECURE`）。需要截图时到「设置 → 其他」打开允许截图。

## 做不到的事

- 不能在应用里注册账户，也不能选 Bitwarden 云地区、单点登录或通行密钥登录。
- Send 入口已隐藏。
- 引导页、红点和首次提示已去掉。
- 加密的 Aegis、2FAS 备份不能导入。
- 验证码自动填充只复制到剪贴板。有的网页不会弹出剪贴板提示。
- 服务器必须开着验证器接口。旧版 Vaultwarden 会显示不支持，已缓存的条目在解锁后仍可离线查看，但不能改。
- 这个包没有上架，推送和崩溃上报沿用了原工程的 Firebase 配置，只改了包名以便能编过。不要依赖推送。

## 和扩展对齐的存储

验证器条目走 `GET/POST /api/authenticator`、`PUT/DELETE /api/authenticator/{id}`。名称、发行方、密钥和备注是用户密钥下的 EncString，和扩展一样用文件夹加密接口产生。备注末尾有 `[[bwmeta:1]]` 和一段 JSON，里面是类型、主机、分组、HOTP 计数器和删除时间。本地只缓存密文。

MCP 会话接口是 `/api/mcp/status`、`/api/mcp/token`、`/api/mcp/token/enc`、`/api/mcp/token/rotate`、`/api/mcp/entries`。包装密钥是 HKDF-SHA256，info 为 `vaultwarden-mcp-totp-v1`，字段用 AES-256-GCM，附加数据是 `v1|{field}|{userId}|authenticator|{id}|{alias}`。

## 在模拟器上核对过的事

对照 Vaultwarden `v-zh-authenticator-1.2.2`（`SIGNUPS_ALLOWED=false`，`MCP_ENABLED=true`），HTTPS，证书由测试 CA 签发。Android 14 的应用信任库在 Conscrypt 的 apex 目录，只把 CA 放进 `/system/etc/security/cacerts` 不够。用户安装的 CA 仍然受应用的网络安全配置信任。核对用的是 R8 压缩后的 x86_64 发布包。

- 首次打开的无障碍说明是简体中文（「无障碍服务说明」「我知道了」「关闭应用」）。
- 未受邀请的邮箱走「收到邀请？创建账户」，服务器拒绝后显示「这个邮箱没有收到邀请，或已经注册过了」。受邀请的邮箱可以在应用里设主密码并登录。
- 从图片识别 `otpauth://` 二维码，导入 1 条，列表上的验证码和密钥一致。
- 新建登录（名称、用户名、密码、网站）后，密码库里能看到「登录已保存」。
- 导出打开系统分享，内容是带明文警告的 JSON。把同一文件再导入，提示「已导入 1 条」。
- 删除先确认「会先放进回收站，之后还能恢复」，回收站里可以恢复，恢复后回收站是空的，条目回到列表。
- 会话超时默认是「从不」。改成「立即」后，切到后台再打开会要求主密码。改回「从不」时会先警告，确认后不再自动锁定。
- 浅色是奶油底。深色背景取样是 `#2C211C`，画面里没有纯黑。
- 把「Bitwarden 验证器版」设为自动填充服务后，在 Chrome 里打开本机登录页，用快捷设置里的自动填充磁贴选出刚保存的登录，用户名和密码写进了网页。登录条目本身没有验证器密钥（编辑页的密钥框在自动化点击下没有获得焦点），所以这次填充没有把验证码复制到剪贴板。验证器卡片上点验证码会拉起系统剪贴板条。
- 「此网站永不」在自动填充的保存页上。这台模拟器里的 Chrome 用自己的密码库，没有把保存请求交给应用的自动填充服务，所以没有弹出这一页。
- MCP：创建、轮换都会显示一次 `vw_mcp_` 令牌。给条目打开「允许 MCP 读取」、别名 `example` 之后，`POST /mcp` 的 `get_totp_code` 返回的验证码和列表上的一致（当时都是 `993572`）。撤销后同一令牌返回 401，正文是「MCP 访问令牌无效或已吊销」。

发布包的 SHA-256 也写在对应的 GitHub Release 里。手机安装 `arm64-v8a`（约 44 MB）。universal 包包含全部架构，大约 82 MB，不要拿它当手机安装包。

| APK | SHA-256 |
| --- | --- |
| `com.minxiangnan.bitwarden.zh-arm64-v8a.apk` | `7f1a322a4946ae6ff44074caaddcf7c7a6a5fd5b318ba8a8c619bf677dec75a1` |
| `com.minxiangnan.bitwarden.zh-armeabi-v7a.apk` | `057f0f65179fb6c2be840daf9e0887a423ba3c7b0a0d8bbe527a6f850d37cbd3` |
| `com.minxiangnan.bitwarden.zh-x86_64.apk` | `578ed2da36688ab23d767f6387ac6f5af5f27108bc3a91978612334c57cfe95e` |
| `com.minxiangnan.bitwarden.zh-x86.apk` | `229b5b74a6c44379a5c72d9f85fcf32058e2711132a70a63f54034e3cea0caa4` |
| `com.minxiangnan.bitwarden.zh.apk`（universal） | `7a66941b4f31f8943023819db8f7688e5a079d97a75b521e6417391d0903e41f` |
