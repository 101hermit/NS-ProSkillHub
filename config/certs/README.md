# config/certs · 公司根证书（本工程自带）

「拿走即用」需要的东西，都在这个目录里：

| 文件 | 用途 |
| --- | --- |
| `ca-bundle.pem` | 公司根 CA（多张证书）。**运行时**由后端 `NetEgress` 读取，用于访问外部 Agent / MCP 时的 TLS 校验 |
| `neusoft-root-ca.pem` | 单张公司根 CA（排障时单独导入用） |
| `trust-all.p12` | **JDK 自带 CA + 公司根 CA** 的合集，口令 `changeit`。**构建期**由 `scripts\build.ps1` 交给 Maven 的 JVM |

## 为什么需要两个（PEM 与 P12）

- 运行时代码（`NetEgress`）**自己读 PEM 构 SSLContext**，用户不需要会 keytool；
- 构建期是 Maven 自己的 JVM 在下载依赖，改不了代码，只能靠 `-Djavax.net.ssl.trustStore` 指一个 P12。

## 换一台机器怎么办

- 如果新环境**直连外网**（不需要代理）：什么都不用做，`NetEgress` 会自动回落到 JDK 默认信任库。
- 如果新环境**也走公司代理**：把新环境导出的 `ca-bundle.pem` 覆盖本目录同名文件，
  再跑一次 `scripts\build.ps1`（它会重新生成 `trust-all.p12`）。
- 完全断网：本工程**不需要**任何证书（三个版本在断网下都能启动，见各 README 的「已知限制」）。