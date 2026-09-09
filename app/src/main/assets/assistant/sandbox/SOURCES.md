# 沙盒资产来源与许可（proot 沙盒）

> 本目录随 APK 分发，用于「助手」的**沙盒模式**（proot 真 Linux，PLAN §10.2）。
> 所有文件均为**原样再分发**，未做修改（`libtalloc.so.2` 仅为按 `DT_NEEDED` 约定重命名）。

## 1. proot（GPL-2.0-or-later）

| 项 | 值 |
|----|-----|
| 文件 | `proot`（244,088 B）、`proot-loader`（18,136 B） |
| 版本 | 5.1.107.92（Termux 打包版，上游 proot 5.1.107） |
| 上游 | https://github.com/termux/proot · https://proot-me.github.io/ |
| 来源包 | `https://packages.termux.dev/apt/termux-main/pool/main/p/proot/proot_5.1.107.92_aarch64.deb` |
| 许可 | **GPL-2.0-or-later**，全文见同目录 `LICENSE-proot-GPL-2.0.txt` |
| 依赖 | `libtalloc.so.2`、`libandroid-shmem.so`、Android 系统 `libc.so`（`PT_INTERP=/system/bin/linker64`） |

### GPL 源码获取（合规义务）

本项目**以未修改的二进制形式再分发** proot。依据 GPL-2.0 §3，源代码可通过以下任一途径获得：

1. **上游仓库**：https://github.com/termux/proot （tag `v5.1.107`，Termux 打包脚本与补丁同仓）；
2. **发行包**：上表 `来源包` 的 `.deb` 内含 Termux 构建产物与元数据；
3. **书面请求**：可向本仓库提交 Issue 索取对应源码副本（承诺在合理期限内提供）。

> 注意：proot 与本项目代码**各自独立**（本 App 仅以子进程方式调用 proot 可执行文件），
> 本项目自身代码仍为 CC BY-NC 4.0。

## 2. libtalloc / libandroid-shmem

| 文件 | 版本 | 来源包 | 许可 |
|------|------|--------|------|
| `libtalloc.so.2` | 2.4.3 | `pool/main/libt/libtalloc/libtalloc_2.4.3_aarch64.deb` | LGPL-3.0-or-later / BSD（Samba talloc） |
| `libandroid-shmem.so` | 0.7 | `pool/main/liba/libandroid-shmem/libandroid-shmem_0.7_aarch64.deb` | MIT |

两者均**原样再分发**；源码可从 Termux 包仓库对应包或上游项目获取
（talloc：https://talloc.samba.org/ ；libandroid-shmem：https://github.com/termux/libandroid-shmem ）。

## 3. Alpine minirootfs

| 项 | 值 |
|----|-----|
| 文件 | `rootfs.tar.gz`（3,947,906 B，压缩态；解压约 8 MB） |
| 版本 | Alpine Linux 3.20.3（aarch64 minirootfs） |
| 来源 | `https://dl-cdn.alpinelinux.org/alpine/v3.20/releases/aarch64/alpine-minirootfs-3.20.3-aarch64.tar.gz` |
| 许可 | Alpine 各包各自许可（主要为 MIT / BSD / GPL），基础系统以 MIT 为主；详见解压后 `/lib/apk/db/installed` 的许可证字段 |

**未预装** Node/Python/Git：由用户在沙盒内 `apk add` 按需安装（PLAN §10.2 的体积决策）。

## 4. 完整性校验

见同目录 `manifest.json`（版本号 + SHA-256）。`tools/assistant-sandbox.mjs --check` 可校验。
