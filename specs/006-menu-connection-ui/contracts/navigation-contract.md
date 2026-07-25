# Navigation and Menu Contract

`SheenApp` 是导航壳唯一 owner，输入为当前 `UiLanguage`、`AdbConnectionState`、历史 profile 列表和各 feature route callbacks，输出为顶部栏、底部栏、抽屉和当前内容。底部栏顺序固定为：连接、文件、应用、进程、终端、日志。文件、应用、进程、Shell、日志的既有 route 和业务逻辑保持不变。未连接时需要连接的入口显示禁用或引导状态；连接断开时回到连接入口，不清除历史 profile。

抽屉事件包括 `OpenDrawer`、`CloseDrawer`、`Reconnect(profileId)`、重命名/删除确认、`OpenSettings`、`OpenAbout` 和 `SetLanguage(UiLanguage)`。历史设备来自 `DeviceProfileRepository.profiles`，在线标记比较当前 endpoint host 与 port；所有变更委托 `DevicesViewModel`，抽屉不得直接访问 ADB 或 DataStore。关于弹层包含 GitHub 链接 `https://github.com/EdouardRichard/sheen-adb-helper`。

未连接连接页提供 IP:端口、10 秒扫描（下拉刷新重新触发）、设备点击连接、二维码、6 位配对码和本机配对。已连接分支显示 endpoint、状态、可用概览和三个快捷操作入口。Compose 通过 ViewModel 和 feature-owned `QuickActionUseCase` 触发动作；app 只负责依赖装配，并把系统 launcher 结果转换为项目自有导出目标后回传。

UI implementation first reads `D:\androidPorject\stitch_adb\technical_terminal_systems\DESIGN.md`, then converts the target pages' HTML + Tailwind `code.html` structures to Jetpack Compose and checks them against `screen.png`. It must not embed WebView content or runtime CDN assets. The drawer footer uses the real build version, and 44px visual targets retain at least a 48dp clickable area.
