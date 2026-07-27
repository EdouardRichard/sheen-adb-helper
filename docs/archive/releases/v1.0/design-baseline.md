# v1.0 UI 设计基线复核

**复核日期**：2026-07-26  
**状态**：PASS（13/13）  
**权威清单**：`specs/007-v1-ui-refactor/research.md`

本记录仅保存设计源相对路径、SHA-256 与一致性结论，不复制设计稿中的端点、包名、文件名、Shell 或 Logcat 样例内容。

| 设计源 | SHA-256 | 结论 |
|---|---|---|
| `technical_terminal_systems/DESIGN.md` | `d8033b1f788bb576f724be3e7e0923385c2d35f539725153429b7b8ef1cfc878` | MATCH |
| `文件管理页/code.html` | `6310a8f8b1f059037341d15bce0ef8d36cbc79551a514eb90fc180d614dc8974` | MATCH |
| `文件管理页/screen.png` | `a8a27ce121e5bdef5103aa054b9de4edcc901d6cbe47bc0812934465d5fef0a6` | MATCH |
| `应用管理页/code.html` | `b363e340a81e30f8c5bc40f4321817fc86e50be423a85f500f1f27d29eec815a` | MATCH |
| `应用管理页/screen.png` | `156fce49f7b726b81ee06c254f7b45f9f0b03cdc856e1b9b3eefc74f88d2efc3` | MATCH |
| `进程管理/code.html` | `fbf6894c48cf514d912f451acbdf0facd1759980eb0eb9de31129c856dfd2fa7` | MATCH |
| `进程管理/screen.png` | `4e50ba1e448756a5bbe7f0ae47c7e088baf9e7d2af061c07f7521dd702930349` | MATCH |
| `shell终端/code.html` | `904496fe105a7fcd41bbc1e8285256cde017407a3291b78b2ec54e9b2d334ec9` | MATCH |
| `shell终端/screen.png` | `9fee6f5fbe0a0efded35da5c7342571ad070c95a1e695df6e07285c33a6750c3` | MATCH |
| `logcat页/code.html` | `4df0aa18d1e44652b283c4f23a2dadfe3715a5f13bbd413e3e7e1ba38c52a393` | MATCH |
| `logcat页/screen.png` | `2db73b1a7d2a36fc16d03cda14c832e6851c41ff52b1e63c83ddb585534ecd1e` | MATCH |
| `连接页-未连接状态/code.html` | `b2d85b636a1f08dc2503be11cb72eaa1ac4cbe091865e956d7765489170c10b2` | MATCH |
| `连接页-已连接/code.html` | `36a4e0f26ad199d0ccb1b0189612b95ed43124c81e433ce11bb172ae85624ac5` | MATCH |

## 执行门禁

- 13 个文件均与规划基线一致，可以按已批准规格进入实现。
- 后续任一设计源哈希变化时，受影响页面必须暂停，并先完成差异审计。
- 不得通过更新本记录或规划哈希来掩盖未经审计的设计变化。
