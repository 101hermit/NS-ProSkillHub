# 分发与安装：三种把 Skill 送到同事手里的方式

## 0. 先理解一件事

Skill 没有"安装包"这个概念。它的安装就是**把一个文件夹放到 AI 助手会读的目录**。

各类工具的约定位置：

| 工具 | 目录 |
| --- | --- |
| Claude Code / 兼容工具 | `~/.claude/skills/<skill-name>/` |
| 项目级（只对某个仓库生效） | `<repo>/.claude/skills/<skill-name>/` |

所以"分发"的本质是：**把 `<skill-name>/` 这个文件夹送到别人的磁盘上**。

## 1. 三种分发方式

### 方式 A：下载 ZIP（最稳，任何环境都能用）

```
GET /api/v1/skills/{skill_id}/download
→ 200 application/zip
→ 内容：<slug>/SKILL.md + 该 Skill 目录下的其它文件
```

优点：不依赖对方装任何工具；缺点：需要手工解压。

**安全设计**：ZIP 打包时会做 zip-slip 防护（丢弃 `..` 段与绝对路径），
下载解压不会写到 `~/.claude/skills` 之外。

### 方式 B：复制一条命令（最快）

```bash
curl -fL -o /tmp/<slug>.zip "http://<本站>/api/v1/skills/<编码后的skill_id>/download" \
  && mkdir -p ~/.claude/skills \
  && unzip -oq /tmp/<slug>.zip -d ~/.claude/skills
```

页面上的按钮会生成好整条命令并一键复制，参数已经编码好 —— 直接粘贴即可。

> 为什么 `skill_id` 要编码？因为它形如 `github:owner/repo/name`，含冒号和斜杠。
> 本站所有详情类接口都同时支持「路径变量」与 `?id=` 两种传法，就是为了绕开容器对 `%2F` 的拒绝。

### 方式 C：源码直链（想审计、想改造就用这个）

跳到上游仓库读原文。本站**只接受三种平台格式**：

| 平台 | 形如 |
| --- | --- |
| GitHub | `https://github.com/<owner>/<repo>/...` |
| Gitee | `https://gitee.com/<owner>/<repo>/...` |
| GitLab | `https://gitlab.com/<owner>/<repo>/...` |

优点：可审计、可 fork、可提 PR；缺点：对方要能访问那个平台。

## 2. 三种方式怎么选

| 场景 | 推荐 |
| --- | --- |
| 内网、对方不装任何工具 | A（下载 ZIP） |
| 对方是工程师、想省事 | B（复制命令） |
| 要长期维护、要改造成公司版本 | C（源码直链） |
| 批量铺给很多台机器 | B（把命令写进初始化脚本） |

## 3. 站内分发链接

详情页的「安装」卡里，三种方式各有一个复制按钮。除此之外：

- **分享链接**：详情页地址本身就是可分享的（hash 路由，任何静态托管都能跑）。
- **批量导出**：目前未提供打包整库下载 —— 375 个 Skill / 57 MB，直接用 `skills/` 目录同步更省事
  （库目录与站内数据是解耦的，见 [简介](intro) 的「数据从哪来」）。

## 4. 常见问题

**Q：下载的 ZIP 解压后多了一层目录，是 bug 吗？**
A：不是。ZIP 里固定是 `<slug>/...`，这样解压到 `~/.claude/skills` 就直接得到
`~/.claude/skills/<slug>/SKILL.md`，正是工具期望的结构。

**Q：能不能直接给我一条 `skhub install` 命令？**
A：标准版 StanSkillHub 提供了 CLI（`skhub install`）。资源站只保证"不依赖任何客户端"，
所以这里给的是 `curl + unzip`。

**Q：内网没有外网，命令里的域名怎么办？**
A：把 `<本站>` 换成内网 IP（例如 `http://10.9.248.225:30800`）即可。
站内生成的命令默认用 `127.0.0.1:<端口>`，分发前替换成实际可达地址。
