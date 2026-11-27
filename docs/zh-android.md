# Bitwarden 验证器版（Android）

这是官方 Bitwarden Android 客户端的一个并存分支。应用名是「Bitwarden 验证器版」，包名是 `com.minxiangnan.bitwarden.zh`。它可以和官方客户端装在同一台手机上。登录条目和验证器条目都放在同一台自建服务器上，和浏览器扩展用同一套接口、同一套用户密钥加密，所以手机和扩展看到的是同一份验证码。

对应的扩展是 `v-zh-authenticator-1.1.7`，服务器是 Vaultwarden `v-zh-authenticator-1.2.3`。1.1.6 和 1.2.2 仍能同步验证码条目。1.2.3 会拒绝过期的写入，并在别名变化时发通知。

## 安装

1. 卸载旧的测试包（如果有）。本分支不会覆盖官方 Bitwarden。
2. 安装 `arm64-v8a` 的发布包。系统会提示未知来源，这是预期的。1.0.1 及以后是同一把发布密钥，可以直接覆盖安装。如果手机上还装着 1.0.0 的调试签名包，要先卸载。1.0.5 起可以在设置里点「检查更新」，或在每天第一次打开时收到新版本提示。点「立即更新」才会下载对应 ABI 的安装包，校验 `SHA256SUMS` 后交给系统安装界面。
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
- 点一下复制当前验证码，数字按「123 456」分组，底部会提示「已复制」，并有轻微震动。倒计时是一圈陶土色进度，剩下不到 5 秒时圈和数字变成暖红色。卡片上还能看到下一个验证码。下拉可以同步，回到应用前台也会同步。别的设备改了验证码时，服务器的通知会让列表自己更新。列表上方写着「同步于 M月d日 HH:mm」。可以搜索、按发行方分组、按名称或发行方排序、只看收藏。长按卡片可以复制、编辑、收藏或删除，删除仍会再确认一次。没有条目时会说明下一步，并给出「扫码」和「添加」。
- 登录页会记住上次的服务器地址和邮箱。服务器地址的键盘是「下一步」，邮箱和主密码的键盘是「完成」，可以直接提交。
- 密码库里每个登录下面有「复制用户名」「复制密码」，有验证器密钥时还有「复制验证码」。
- 编辑、移入回收站、从回收站恢复、彻底删除。删除和彻底删除都会先确认，标题带条目名称，确认按钮是危险色，取消是默认按钮。
- 导入 Google Authenticator 迁移码、未加密的 Aegis、2FAS 和 Bitwarden JSON/CSV，以及本分支自己的未加密导出。加密的 Aegis / 2FAS 文件会拒绝导入。导出文件含明文密钥，只适合迁移。
- 每条验证码可以打开「允许 MCP 读取」并填写别名。HOTP 不能交给 MCP。设置里有「MCP 访问」：创建令牌、只显示一次、复制、轮换、吊销。令牌明文不落盘，服务器上存的是用户密钥加密后的副本。轮换后会用新密钥重新包装已放行的条目。
- 自动填充填完密码后，如果登录条目自己有 TOTP，或验证器里有和该主机匹配的条目，会把验证码复制到剪贴板。Android 没有通用的验证码填充字段，所以这里是复制，不是写进输入框。
- 保存提示：同一主机下的新用户名会新建登录；同一用户名但密码变了，只更新密码，备注和验证码保留；完全相同则不提示。点「此网站永不」会再确认一次。
- 界面默认简体中文。配色是奶油底、陶土色强调（`#D97757`），深色模式用暖棕色，不用纯黑。
- 生物识别解锁沿用原客户端的能力。设备支持指纹但还没打开时，锁屏上会提示去「设置 → 账户安全」打开，并说明默认不会自动上锁。要在账户安全里打开之后，才会出现「使用生物识别解锁」。默认不自动锁定。
- 默认禁止截图和在最近任务里露出验证码（`FLAG_SECURE`）。需要截图时到「设置 → 其他」打开允许截图。

## 做不到的事

- 不能在应用里注册账户，也不能选 Bitwarden 云地区、单点登录或通行密钥登录。
- Send 入口已隐藏。
- 引导页、红点和首次提示已去掉。
- 加密的 Aegis、2FAS 备份不能导入。
- 验证码自动填充只复制到剪贴板。有的网页不会弹出剪贴板提示。
- 服务器必须开着验证器接口。旧版 Vaultwarden 会显示不支持，已缓存的条目在解锁后仍可离线查看，但不能改。
- 这个包没有上架。Firebase 推送和崩溃上报沿用了原工程的配置，只改了包名以便能编过。验证码同步不走 Firebase，走服务器的 `/notifications/hub`。部署里要开着 `ENABLE_WEBSOCKET`（自带的 compose 已经是 `true`）。

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
- 1.0.2 在同一台模拟器上又走了一遍。登录页重新打开时，服务器地址和邮箱都还在。验证器卡片是大号分组数字、倒计时圈，点一下出现「已复制 224 204」。长按有复制、编辑、收藏、删除。搜索没有结果时是「还没有验证码」，下面有「扫码」和「添加」。删除确认写着「会先放进回收站，之后还能恢复」。深色背景仍是暖棕，不是纯黑。密码库每一行登录下面有复制用户名、复制密码；给登录加上验证器密钥之后，还会多出「复制验证码」。
- 给 ExampleLogin 用「手动输入密钥」写进 `JBSWY3DPEHPK3PXP` 并保存后，在 Chrome 的登录页用自动填充磁贴选出这一条。用户名 `demo` 和密码写进了网页。回到 Chrome 时系统拉起了剪贴板条，并排入了清除剪贴板的任务。Android 14 不允许别的应用从 shell 读剪贴板，所以核对的是这两条系统记录，不是把验证码数字再读出来。
- 「此网站永不」：带用户名和密码框的测试小应用提交后，系统弹出「Save password to Bitwarden 验证器版?」。表单关掉时已经没有焦点，旧的解析会把这次保存当成无法填充，所以第一次点 Save 没有打开应用。1.0.2 在没有焦点时仍按用户名和密码生成保存项。修好之后，同一种系统保存条（这次来自应用自己的登录表单）点 Save 会进入「添加登录」，右上角是「此网站永不」。点开后确认「以后不再询问是否保存这个网站的登录」。这次点了取消，没有把主密码存进密码库，网站显示为 `androidapp://com.minxiangnan.bitwarden.zh`。Chrome 自己的密码库仍然不会把网页保存请求交给这个应用。
- MCP：创建、轮换都会显示一次 `vw_mcp_` 令牌。给条目打开「允许 MCP 读取」、别名 `example` 之后，`POST /mcp` 的 `get_totp_code` 返回的验证码和列表上的一致（当时都是 `993572`）。撤销后同一令牌返回 401，正文是「MCP 访问令牌无效或已吊销」。

发布包的 SHA-256 也写在对应的 GitHub Release 里。手机安装 `arm64-v8a`（约 44 MB）。universal 包包含全部架构，大约 82 MB，不要拿它当手机安装包。

1.0.2：

| APK | 大小 | SHA-256 |
| --- | --- | --- |
| `com.minxiangnan.bitwarden.zh-arm64-v8a.apk` | 46,247,873 | `731a7c6418746186cd563fc1ba5e51b68c4fbe24ef07d392d7195aff842715df` |
| `com.minxiangnan.bitwarden.zh-armeabi-v7a.apk` | 43,126,811 | `ac53054f34f9e76ae3a0c0374167ff28fb96136989c6cd79c1cd264dce245a61` |
| `com.minxiangnan.bitwarden.zh-x86_64.apk` | 45,649,028 | `15514c5959c1d1d4cf4d22f714ae083210160943e3ec35c2fd36c3f91006a554` |
| `com.minxiangnan.bitwarden.zh-x86.apk` | 46,048,791 | `1620e53fa079e7142e69b472fbb5bc483b4ce91300d39289fa3dba68d6b88b2e` |
| `com.minxiangnan.bitwarden.zh.apk`（universal） | 85,950,663 | `095cb4aaf0ea7b5bb3f43ea6d46f34f9ee446e5f9170563ef56b19e1777e2b5f` |

1.0.3 在同一台模拟器上对着 Vaultwarden 1.2.2 测了双向同步。电脑端写入后，服务器日志里安卓立刻来拉列表：一次是响应后 8 毫秒（23:41:25.914 到 23:41:25.922），一次是 15 毫秒（23:47:43.403 到 23:47:43.418）。列表不必下拉。搜「ZZTop」能看到刚写入的卡片，验证码是 `866 101`，下一个是 `838 098`，和用同一密钥独立算出来的一致。更早一次屏幕上的 `338 360` 也对得上。安卓用「从图片识别」导入 `otpauth://` 之后，另一端的通知通道在点选后 603 毫秒收到创建消息（类型 30）。服务器上这条是 `authenticatorEntry`，名称 `phone@example.com`，发行方 `ImageSync`，密钥 `JBSWY3DPEHPK3PXP`。扩展 1.1.6 收到类型 30/31/32 会做一次完整同步，同步体里的 `authenticatorEntries` 能解回同一条。探针当时通知和 HTTP 响应在同一毫秒（整次写入 6 毫秒）。

相机取景能打开。授权之后模拟器给出的是自带的虚拟场景，镜头停在默认房屋上，没有把二维码放进取景范围，所以这一次没有靠镜头解出码。保存走的是「从图片识别」，和解码成功后的写入是同一条接口。

别名存在 MCP 授权里，不在验证器备注里。1.2.2 不会为只改别名发通知。安卓在写完别名后再保存一次条目，这样别的设备会收到更新。扩展 1.1.6 仍是条目变化才推送；只在电脑上改别名时，安卓要回到前台或下拉才会看到。服务器和扩展都不用为条目同步再发一版。部署保持 `ENABLE_WEBSOCKET=true`。

1.0.3：

| APK | 大小 | SHA-256 |
| --- | --- | --- |
| `com.minxiangnan.bitwarden.zh-arm64-v8a.apk` | 46,271,673 | `74c26b324aa5d74fb95722bbdbd0ad7bf894e6fb4eddfa3f210741acb8e5e759` |
| `com.minxiangnan.bitwarden.zh-armeabi-v7a.apk` | 43,150,611 | `d629e7b74dd54e3e50e4080fb073f5ad63ee2e146f4b5ac765fc82c05c8d400b` |
| `com.minxiangnan.bitwarden.zh-x86_64.apk` | 45,672,828 | `9beced0d7953ff94b7e690390bf37bc2251a14d8e420cd9db65ac2bb1c64f58f` |
| `com.minxiangnan.bitwarden.zh-x86.apk` | 46,072,591 | `e617184e919c6df828970e0540ea2aa9afefceb0c70f38574e66910431c271d2` |
| `com.minxiangnan.bitwarden.zh.apk`（universal） | 85,974,463 | `24f7703441ec4a23a032dbeff9618704f8b2d9b2e59c6ea19cc7909cf267071b` |

1.0.4 把同一条通道补完整。生产 compose 里 `ENABLE_WEBSOCKET=true`，Caddy 对 `/notifications/hub` 做 WebSocket 升级并且不缓冲。在本机用同样的转发测过：创建 10 毫秒、修改 17 毫秒、放进回收站 15 毫秒、彻底删除 11 毫秒、写入别名 15 毫秒，对端就收到通知。带着旧的 `revisionDate` 再保存会得到 409，服务器上的名称保持为后写入的那一版。用同一个客户端 id 再 POST 一次不会多出一条。安卓在套接字断开时每 3 秒拉一次，回到前台也会拉。离线时保存会被拒绝，提示「当前离线，没有保存。」电脑把一条改名之后，安卓列表在同一分钟里出现新名称，服务器日志里安卓在响应后 6 毫秒来拉列表。

1.0.4：

| APK | 大小 | SHA-256 |
| --- | --- | --- |
| `com.minxiangnan.bitwarden.zh-arm64-v8a.apk` | 46,269,245 | `cd7598923f658411f00e8aab6f47889d92c7d54d9ac742fcca32d5df1093bc81` |
| `com.minxiangnan.bitwarden.zh-armeabi-v7a.apk` | 43,148,183 | `02ba4e9047b2addf59c27fa7c3fc5f112a63a37237d639d0f636cb27ecdff320` |
| `com.minxiangnan.bitwarden.zh-x86_64.apk` | 45,670,400 | `7b8019ef5994ab987986aef36588c96cf8b28b280976acb48210d48b866240f9` |
| `com.minxiangnan.bitwarden.zh-x86.apk` | 46,070,163 | `12f5dd3eed775123e95bb58d1c331fe0c590dcd6438818d7b3ffd03a3ea75647` |
| `com.minxiangnan.bitwarden.zh.apk`（universal） | 85,972,035 | `1c6b60af26869193a8c340e269280d386d1676365e3b3f2354e5a4b81f5151a4` |

1.0.5 在设置里增加「检查更新」，每天第一次打开也会向 GitHub Releases 查询 `v-zh-authenticator-android-` 的最新稳定版。点「立即更新」后按设备 ABI 下载安装包，对照 `SHA256SUMS` 校验，再交给系统安装界面。签名仍是 1.0.1 起的那把发布密钥。

1.0.5：

| APK | 大小 | SHA-256 |
| --- | --- | --- |
| `com.minxiangnan.bitwarden.zh-arm64-v8a.apk` | 46,320,245 | `3c7701b370eeb9b96159ed7a8c7a6ffdba1d92e7a4cb70d1fd191eea490f3181` |
| `com.minxiangnan.bitwarden.zh-armeabi-v7a.apk` | 43,199,183 | `ef46ce10d9d8c608a536bb739151caedeaa99dc532a8553f2e03b55b779262fc` |
| `com.minxiangnan.bitwarden.zh-x86_64.apk` | 45,721,400 | `76692bdf6b475e1f4b1c628d3170f4894f875704f5c38d785cba2cfd0023ab0c` |
| `com.minxiangnan.bitwarden.zh-x86.apk` | 46,121,163 | `baf97631306857d7fe844e90c39a72727df077cfe775784da03ef7b6746669ec` |
| `com.minxiangnan.bitwarden.zh.apk`（universal） | 86,023,035 | `364e93b795e2981e03e44d319fde226f7e2c48eba6211e0768d24f629034afa3` |

1.0.6 修复验证器页扫码打不开摄像头。点「扫码」会先申请相机权限；拒绝时给出中文说明和「去设置」。允许之后打开取景，识别 `otpauth://` 或 `otpauth-migration://` 后直接保存并回到列表。手动输入密钥仍然可用。二维码解码是打进安装包的 ZXing，不依赖 Google Play 服务。签名仍是 1.0.1 起的那把发布密钥。

1.0.6：

| APK | 大小 | SHA-256 |
| --- | --- | --- |
| `com.minxiangnan.bitwarden.zh-arm64-v8a.apk` | 46,332,249 | `aa3b66f96efce440332973ec94620b8f0af89c80a9792d792cce592ecda60ab6` |
| `com.minxiangnan.bitwarden.zh-armeabi-v7a.apk` | 43,211,187 | `1518f93762bea9c98352e9aca40db72c1c1b46a618d9cd6833c2ba1a6ef3d89c` |
| `com.minxiangnan.bitwarden.zh-x86_64.apk` | 45,733,404 | `89e6b4942d91a30c4d38fc2b1f99f36c90546f4cd4d2413ae5b24f4aae02c763` |
| `com.minxiangnan.bitwarden.zh-x86.apk` | 46,133,167 | `c085317f8ced513f6c26c4b4c9e533a0f01e34b7e3b3f8508b2a8009bd335a18` |
| `com.minxiangnan.bitwarden.zh.apk`（universal） | 86,035,039 | `b0a5668aa3af12f7a915773a26fb77d147363a9cc8e533705422c3d4ccd76072` |

1.0.7 把验证器列表收成一行。左侧是名称，有备注时在名称下面用一行小字显示，没有备注就不占高度。右侧是缩小后的验证码和小圆形倒计时。只在剩余不超过 5 秒时，才在验证码下面显示「下一段」。编辑和删除放到长按和右侧菜单，点整行仍然复制验证码。编辑页可以查看和修改备注，备注写进条目的 notes，和服务器、扩展一起同步。搜索不区分大小写，同时匹配名称和备注，匹配的字会高亮。签名仍是 1.0.1 起的那把发布密钥。

1.0.7：

| APK | 大小 | SHA-256 |
| --- | --- | --- |
| `com.minxiangnan.bitwarden.zh-arm64-v8a.apk` | 46,330,833 | `0fe7836e3a3744af4f4cde5a562f29e240df465745cea42107e374070dd993b4` |
| `com.minxiangnan.bitwarden.zh-armeabi-v7a.apk` | 43,209,771 | `56301fab68454a09ef7e9661ba8ea973289003eb208614d2542ee9198064d40f` |
| `com.minxiangnan.bitwarden.zh-x86_64.apk` | 45,731,988 | `d5d9aa0da29462ac04671d113610e303e5a5c2b0659467d1289d0d8cced02c6c` |
| `com.minxiangnan.bitwarden.zh-x86.apk` | 46,131,751 | `4a6ff890135aeef92f6f39fd675dfa11624ad91ca7d34b700905a2feef5b1cc4` |
| `com.minxiangnan.bitwarden.zh.apk`（universal） | 86,033,623 | `6e115618b652bb3e747d4fce2a104c87f290b7d02f1071484ff168eadbd13900` |
