# Sheen ADB 助手：三模拟器 TAP 组网与回归提示词

将下面整段提示词交给 AI Agent。它以当前工作区为唯一代码来源，要求 Agent 先恢复网络拓扑，再按 TDD 验证和修复产品。

```text
你正在 Windows 主机上维护 D:\androidPorject\sheen-adb-helper。目标是让 Android 16 主控模拟器、Android 12 无线调试目标和 Android 9 旧版 ADB 目标形成可重复的测试拓扑，然后回归 Sheen ADB 助手。不要连接局域网中的真实手机；只允许操作下面三个 emulator serial。

固定环境：
- Android SDK：D:\Tools\Android\Sdk
- AVD_HOME：D:\Tools\Android\.android\avd
- Android 16 主控：AVD Pixel_10_Pro，serial emulator-5554，TAP tap0
- Android 12 目标：AVD Pixel_6，serial emulator-5556，TAP tap1
- Android 9 目标：AVD Medium_Phone，serial emulator-5558，TAP tap2
- Gradle JDK：C:\Users\Richard\.gradle\sheen-jdk21
- 被测 APK：app\build\outputs\apk\debug\app-debug.apk

安全边界：
1. 所有 adb 命令必须显式带 `-s emulator-5554`、`-s emulator-5556` 或 `-s emulator-5558`。
2. 不得操作 `adb devices` 中的其他 serial，不得记录配对码、真实 IP、私钥、Shell/Logcat 原文或测试应用包名。
3. 不提交、不推送、不重置用户工作区；保留现有未提交修改。
4. 需要管理员权限时明确说明；只桥接 tap0、tap1、tap2，绝不把物理网卡、VPN 或公司网络加入网桥。
5. 每个修复先得到失败测试，再改实现，再用 `--rerun-tasks --no-parallel --no-daemon` 重跑。

第一阶段：检查工具和 TAP
1. 设置：
   `$sdk = 'D:\Tools\Android\Sdk'`
   `$adb = Join-Path $sdk 'platform-tools\adb.exe'`
   `$emulator = Join-Path $sdk 'emulator\emulator.exe'`
   `$env:ANDROID_AVD_HOME = 'D:\Tools\Android\.android\avd'`
2. 用 `$emulator -list-avds` 确认三个 AVD 存在。
3. 用 `Get-NetAdapter` 确认 tap0、tap1、tap2 均存在。缺失时安装官方 TAP-Windows6 驱动，安装完成后把三个适配器准确重命名；不要用业务 VPN 的 TAP 适配器代替。
4. 确认 Windows “网络连接”中存在只包含 tap0、tap1、tap2 的“网桥”。若不存在，使用 Windows 网络连接 UI 选中三个 TAP 后创建网桥。
5. 网桥设置静态 RFC1918 `/24` 地址，不设置默认网关和 DNS。地址必须从当前未占用的私网段动态选择，后文称为：
   - 网桥：`<NET>.1/24`
   - Android 16：`<NET>.2/24`
   - Android 12：`<NET>.3/24`
   不要把这些地址写入仓库证据。

第二阶段：干净启动三个模拟器
1. 先只终止这三个 AVD 对应的 emulator/qemu 进程；不得终止其他 Android Studio 或 adb 用户进程。
2. 分别隐藏启动：
   - `emulator -avd Pixel_10_Pro -port 5554 -net-tap tap0 -no-snapshot-load`
   - `emulator -avd Pixel_6 -port 5556 -net-tap tap1 -no-snapshot-load`
   - `emulator -avd Medium_Phone -port 5558 -net-tap tap2 -no-snapshot-load`
3. 每台循环等待 `getprop sys.boot_completed` 为 `1`，单台最多等待 180 秒。
4. 三台都是 debug 镜像，执行 `adb -s <serial> root` 后等待重新上线。
5. 若 SetupWizard 抢占前台，设置 `device_provisioned=1`、`user_setup_complete=1`，仅 force-stop 对应模拟器的 SetupWizard，然后重新启动 Sheen。

第三阶段：把 TAP 配置到正确的来宾接口
重要：`-net-tap` 的桥接接口在 Android 12/16 上是 `eth0`，不是 Emulator 内建 NAT 的 `wlan0`。把桥接地址错误配置到 `wlan0` 会出现同一虚拟 Wi-Fi 内模拟器互通、但无法 ARP Windows 网桥的假成功。

1. Android 16：
   - `ip link set eth0 down`
   - `ip link set eth0 address 02:00:00:00:10:02`
   - `ip link set eth0 up`
   - 清理 eth0 上旧的测试地址
   - `ip addr add <NET>.2/24 dev eth0`
2. Android 12：
   - `ip link set eth0 down`
   - `ip link set eth0 address 02:00:00:00:12:03`
   - `ip link set eth0 up`
   - 清理 eth0 上旧的测试地址
   - `ip addr add <NET>.3/24 dev eth0`
3. 两个 AVD 可能默认得到相同的 QEMU MAC。必须用 `ip link show eth0` 确认上述两个本地管理单播 MAC 已生效且互不相同；否则 Windows 网桥会让两个来宾互相覆盖 ARP，表现为时通时断或只通最后启动的一台。
4. 删除两台 `wlan0` 上误加的 `<NET>.0/24` 地址，保留 Emulator NAT 自动分配的地址。
5. 清理 Windows 网桥与两个来宾上的旧 ARP/neighbor 项，再验证两台设备：
   - `ip -4 route` 中 `<NET>.0/24` 必须指向 eth0；
   - 能 ping `<NET>.1`；
   - `ip neigh` 不得长期为 FAILED；
   - Android 16 与 Android 12 的 eth0 地址可互相 ping。
   - Windows `arp -a` 中 `<NET>.2` 与 `<NET>.3` 必须对应两个不同 MAC。
6. 任一方向不通时不得继续产品验收：先检查 tap0/tap1 是否仍为网桥成员、成员是否 Up、网桥是否误混入物理/VPN 网卡，并用 `Get-NetAdapterStatistics` 确认两个 TAP 都有收发增量。不得用 Emulator NAT 或宿主端口转发冒充 Android 12 的动态 TLS 跨实例证据。
7. Android 9 debug 镜像若没有 eth0，只出现 `radio0@if...`，不要把静态地址强塞到 wlan0/radio0 并伪称桥接成功；使用下一阶段的受控 5555 relay。

第四阶段：准备两个目标

Android 12 动态无线调试目标：
1. 在系统“开发者选项/无线调试”中开启无线调试。系统可能为中文，优先用 uiautomator 文本和 content-desc 识别，坐标只作最后备选。
2. 打开“使用配对码配对设备”，让系统发布 `_adb-tls-pairing._tcp`；无线调试主页面同时发布 `_adb-tls-connect._tcp`。
3. 主控应用必须自己完成发现、配对与自动连接。宿主 `adb pair/connect` 只能用于诊断，不能替代产品验收。
4. 配对码只在输入时短暂读取，绝不输出到控制台、文件或最终报告。

Android 9 旧版目标：
1. `adb -s emulator-5558 root`
2. `adb -s emulator-5558 tcpip 5555`
3. Android 9 Emulator 的 ADB 数据端口是宿主 `127.0.0.1:5559`。在 Windows 启动一个临时 TCP relay：
   - 监听 `<NET>.1:5555`
   - 转发到 `127.0.0.1:5559`
   - 记录 relay PID，只在回归结束时终止该 PID
4. 用 Windows `Get-NetTCPConnection` 验证 `<NET>.1:5555` 正在监听。
5. 用 Android 16 执行 `toybox nc -z -w 3 <NET>.1 5555`，必须成功。
6. 此 relay 是 Android 9 旧版 5555 产品入口；Android 16 应把它当作同一测试网段中的旧版设备直接连接。

第五阶段：先验证两项最高优先级

A. 动态配对后自动连接
1. 在 Android 16 安装最新 debug APK并启动。
2. 在连接页等待发现 Android 12 的动态调试服务。
3. 列表中不得出现 `_adb-tls-pairing._tcp` 对应配对端口。
4. 点击尚未配对的动态调试行，应显示确认提示；确认后打开根级共享配对浮层。
5. QR 与配对码页面必须使用同一浮层并可互相切换。
6. 选择配对码方式，输入系统当前配对端点和配对码，提交。
7. 成功判定必须同时满足：
   - 配对浮层自动关闭；
   - 应用主动连接 `_adb-tls-connect._tcp` 的动态调试端口；
   - 进入已连接概览；
   - 用户没有再点局域网列表或手动连接按钮。

B. 扫描只展示调试服务并正确分流
1. 配对端口始终不出现在局域网设备列表。
2. 未配对的动态调试行进入共享配对浮层。
3. `<NET>.1:5555` 旧设备行点击后提示直接连接，不打开配对浮层。
4. 确认旧设备后成功读取 Android 9 概览。
5. 不得仅以 TCP 端口开放判断“设备验证通过”；对扫描误报单独记录，不能把误报当作目标设备。

第六阶段：全功能回归
分别在 Android 12 和 Android 9 会话验证：
1. 连接：成功连接、主动断开、断开后重新连接、历史设备更新、错误卡片关闭和详情。
2. 文件：目录进入、面包屑返回、上传、下载；SAF picker 返回后 Session 不得断开；传输完成后目标/输出文件真实存在。
3. 应用：列表、输入法回车过滤、安装、重复安装的强制覆盖/升级/降级提示、提取 APK、强停、禁用、启用、卸载。只操作专用测试 APK。
4. 进程：首次进入和 5 秒刷新均有 CPU/内存；快速反复切换应用/进程/文件/终端至少 8 次，不得 ADB_TIMEOUT、ADB_IO_FAILURE 或断连。
5. Shell：执行一个无副作用命令并观察回显；特殊键和自动滚动状态可操作。
6. Logcat：进入时不自动采集；点开始后有记录；切页即停止；过滤、级别、清空和保存入口可用。
7. 模拟远端网络中断：仅对 Android 12 当前动态调试端口临时加 DROP 规则，触发文件/进程命令，等待健康检查；应用必须存活并自动回未连接页。验证后立即删除规则。
8. 长任务进行中禁止切页，picker 阶段允许进入系统选择器；返回后任务继续且主 Session 保持。

第七阶段：TDD、证据和收尾
1. 每个发现的问题先写会失败的最小测试，保留 RED 结果摘要；随后修实现并跑 GREEN。
2. 强制重跑相关模块，最后执行全量测试与 `:app:assembleDebug`，均带：
   `--rerun-tasks --no-parallel --no-daemon`
3. 执行 `git diff --check`，不得覆盖无关用户修改。
4. 更新当前批准的 tasks.md：每项最多两个修改文件，按用户故事和 Phase 追加，测试任务在实现任务之前。
5. 证据只写测试类型、Android 大版本、成功/失败和技术代码；不得写真实端点、配对材料、包名、原始 Shell/Logcat。
6. 删除模拟器上的临时回归文件、撤销临时防火墙/iptables 规则，终止且只终止本轮 relay PID；保留用户要求的 TAP、网桥和模拟器。
7. 最终明确区分：自动化测试、模拟器端到端证据、仍需真机完成的 QR/通知/厂商兼容性证据。
```

## 本机实测关键点

- Android 12/16 的 TAP 地址必须放在 `eth0`；`wlan0` 是 Emulator NAT 网络。
- Android 12/16 的 QEMU 默认 MAC 可能相同；给两个 `eth0` 设置不同的本地管理单播 MAC 是可重复组网的必要步骤。
- Android 9 当前镜像没有可用的 TAP 以太接口，因此使用 Windows 网桥上的受控 5555 relay 映射旧设备。
- 组网成立的最低证据是：来宾 `eth0` 能完成网桥 ARP/ICMP，主控能 TCP 连接网桥 5555，应用能分别连接动态 TLS 调试端口和旧版 5555 入口。
