---
name: spack-develop-skill
description: "在 HPCKit（鲲鹏/aarch64）生态下开发 Spack package.py 的完整指南，覆盖 bisheng/clang 与 gcc 双编译器变体、hmpi、KML 计算库系列（kpblas/kpfftw/kpkml 等），以及 CMake/Autotools/Makefile/Python/Meson/Generic 全构建系统的版本、变体、依赖、环境注入、patch 与本地仓库验证。当需要把 HPC/科学计算软件打包为 Spack 包、接入 HPCKit 工具链、做 aarch64 适配或编写非标准构建安装脚本时使用。"
version: latest
license: UNKNOWN
category: development
scene: general
origin: easy
source: https://gitcode.com/wmrd/spack-develop-skill/blob/main/spack-develop-skill/SKILL.md
---
# HPCKit 生态 · Spack package.py 开发指南

把 HPC / 科学计算软件打包为 Spack 包并接入 HPCKit 工具链（bisheng/gcc 双编译器、
hmpi、KML 计算库系列）。本文件只保留**触发判断、工作流程与最高频规则**；每个阶段的
完整说明、可复制代码模板与案例都在 `references/` 下，按需打开，避免一次性占用上下文。

## 触发场景

- 依赖 HPCKit 编译器（bisheng / gcc 双变体）与 hmpi 的 C / C++ / Fortran 项目
- 需要将科学计算软件打包为 Spack 包并接入 HPCKit 工具链
- 含 vendor 子项目（htslib、FoX 等）需要 autotools 改造与 gnuconfig 替换
- 需要改 CMakeLists.txt / 源文件（OpenMP、aarch64 适配、MPI find_package 注入）
- 需要在构建期与运行期注入编译器、MPI、HDF5 等环境
- 需要为非标准构建系统（原生 Makefile、csh、交互式 configure、build.py 等）写安装逻辑
- 需要在本地 Spack 仓库创建新包并完成 spec / install / 运行验证
- 依赖 KML 计算库系列（kpblas / kpfftw / kpkml / kpksvml / kpkupl 等）的数值项目

## 工作流程总览

按顺序推进，每一步的详细做法见对应 reference：

| # | 阶段 | 参考文件 |
|---|------|---------|
| 1 | 分析软件构建系统 | [references/01-analyze-build-system.md](references/01-analyze-build-system.md) |
| 2 | 选择 Spack 基类与导入 | [references/02-base-class-and-imports.md](references/02-base-class-and-imports.md) |
| 3 | 确定 HPCKit 生态依赖与双变体 | [references/03-hpckit-ecosystem.md](references/03-hpckit-ecosystem.md) |
| 4 | 声明版本 / 变体 / 依赖 | [references/04-versions-variants-dependencies.md](references/04-versions-variants-dependencies.md) |
| 5 | 编写构建逻辑 | [references/05-build-logic.md](references/05-build-logic.md) |
| 6 | 配置环境注入（构建期 + 运行期） | [references/06-environment-injection.md](references/06-environment-injection.md) |
| 7 | 处理 Patch 与源码改造 | [references/07-patches.md](references/07-patches.md) |
| 8 | 处理常见构建问题 | [references/08-common-build-issues.md](references/08-common-build-issues.md) |
| 9 | 遵守包名命名规则 | [references/09-naming-conventions.md](references/09-naming-conventions.md) |
| 10 | 本地仓库注册与验证 | [references/10-repo-and-verification.md](references/10-repo-and-verification.md) |

辅助参考：[实战案例索引](references/11-case-index.md)、
[关键规则速查](references/12-quick-reference.md)。

## 各阶段要点

### 1. 分析构建系统
克隆源码后按 `CMakeLists.txt` / `configure*` / `Makefile` / `setup.py|pyproject.toml`
/ `meson.build` 判定基类；确认版本、语言、外部依赖、Fortran `.mod` 并行安全性。
HPCKit 额外判断 MPI / HDF5 / BLAS / FFTW / SVML / KUPL 需求与 aarch64 兼容性。

### 2. 选择基类（不得在重构时更改原版基类）
CMake→`CMakePackage`、Autotools→`AutotoolsPackage`、Makefile→`MakefilePackage`、
Python→`PythonPackage`、Meson→`MesonPackage`、其它/自定义→`Package`。
基类决定构建阶段（如 Makefile 是 `edit→build→install`，Generic 只有 `install`），改错会缺阶段。

### 3. HPCKit 双编译器与依赖
- 编译器变体：`%llvm`→`bisheng`，`%gcc`→`gcc`；运行时库路径不同（gcc 用
  `lib64`/`lib64_public`，bisheng 用 `lib`，必要时加 `lib/aarch64-unknown-linux-gnu`）。
- hmpi / kp* 包都内置 `compiler_variant`，在消费者里用
  `depends_on("hmpi compiler_variant=bisheng", when="%llvm", ...)` 成对声明，
  **不写死版本**，由用户 `^hpckit@<ver>` 切换。
- 不直接依赖 hmpi/kp* 的包**不要**声明 hpckit，也不要注入编译器运行时。

### 4–6. 声明与构建
- CMake 项目即使纯 C++ 也必须 `depends_on("c", type="build")`，否则报 `SPACK_CC_* not set`。
- Generic Package 里**没有**内置 `cmake()`/`make()`，必须 `which("cmake")` / `which("make")`。
- 消费者只注入：① 编译器运行时库；② CC/CXX/FC 指向 hmpi wrapper。
  hmpi 已注入自身库/头与 `OMPI_*`，**不要重复注入**。
- MPI wrapper 用 `MPI_C_COMPILER` / `MPI_CXX_COMPILER` 提示 `find_package(MPI)`，
  **不要**塞给 `CMAKE_*_COMPILER`。

### 7–8. Patch 与常见坑
- sed 改源码优先沉淀为 `patch()` 文件（放在 `depends_on` 之后）；level 看路径前缀
  （`a/`、`./` → 1；无前缀 → 0）。
- vendor 子项目老 `config.guess/config.sub` 用 `gnuconfig` 在 `edit()` 替换。
- aarch64 需移除 x86 标志（`-m64 -msse* -mavx* -march=native` 等）。
- `filter_file` 逐行匹配，**不支持跨行**，删连续两行要分两次调用。

### 9–10. 命名与验证
- 仓库**目录名/类名用下划线与驼峰**（`ncbi_blast_opt` / `NcbiBlastOpt`），
  spec、`depends_on`、`spec["..."]` 用连字符（`ncbi-blast-opt`）。
- 包名含 `-` 时默认 `.libs`/`.headers` 会失败（`-` 被当通配符），改用
  `spec["pkg-opt"].prefix` 拼 lib/include。
- 验证链路：`spack list` → `spack style -t import` → `spack spec` → `spack install`
  → `spack load` 后 `ldd` / 运行 smoke test。

## 最高频红线（先看这张表）

| 规则 | 要点 |
|------|------|
| 无内置 `cmake()`/`make()` | Generic Package 用 `which()` 获取 |
| 包名含 `-` 的 `.libs` 会失败 | 用 `spec["pkg-opt"].prefix` 拼路径 |
| 双变体声明 | `when="%llvm"`→bisheng / `when="%gcc"`→gcc，不写死版本 |
| 不重复注入 hmpi 库/头、不注入 OMPI_CC | hmpi 的 dependent 环境已处理 |
| 不直接用 hmpi/kp* 就不声明 hpckit | compiler wrapper 会自动处理 |
| 重构已有包不得改基类 | 各基类构建阶段不同 |
| CMake 项目必须 `depends_on("c")` | 即使纯 C++ |
| MPI wrapper 不传 `CMAKE_*_COMPILER` | 用 `MPI_*_COMPILER` |
| Fortran `.mod` / vendor 子库 | `parallel = False` |
| 所有修改只在 package.py / patch 内 | 不改 yaml 配置、不改 kp*/hmpi 基础包 |

## 参考文件索引（按需加载）

- [01 分析构建系统](references/01-analyze-build-system.md)
- [02 基类与导入模板](references/02-base-class-and-imports.md)
- [03 HPCKit 生态（目录布局/双变体/hmpi/KML 系列）](references/03-hpckit-ecosystem.md)
- [04 版本 / 变体 / 依赖声明](references/04-versions-variants-dependencies.md)
- [05 各构建系统构建逻辑与代码模板](references/05-build-logic.md)
- [06 环境注入（构建期 / 运行期 / wrapper / pip）](references/06-environment-injection.md)
- [07 Patch 声明、level、filter_file、CMake 改造](references/07-patches.md)
- [08 常见构建问题（Fortran/架构/MPI/缓存等）](references/08-common-build-issues.md)
- [09 包名命名规则与 spec 歧义](references/09-naming-conventions.md)
- [10 仓库注册、语法/spec/install/运行验证](references/10-repo-and-verification.md)
- [11 实战案例索引](references/11-case-index.md)
- [12 关键规则速查与参考路径](references/12-quick-reference.md)
