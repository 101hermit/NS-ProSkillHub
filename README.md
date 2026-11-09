# ProSkillHub 1.0（PSKH）· 面向 Skill 的多功能 Agent 平台

> **一句话**：在 StanSkillHub 的库管理之上，把"实测"从**单点**升级为**实验设计**，
> 把"评估"从**打分**升级为**知识级洞见** —— 多智能体 × 多 MCP × 多场景的连通与实测矩阵、
> 三张实验表、知识图谱、统计检验与多准则决策。

| 项 | 值 |
| --- | --- |
| 产品全名 | **ProSkillHub** |
| 版本 | **1.0** |
| 缩写 | **PSKH** |
| 端口 | **30802** |
| 定位 | 多功能模式（面向 Skill 的 Agent 平台 + 专业级评估） |
| 软件开发方法 | 原型开发 + 定制化敏捷开发 + 增量模型 |
| 目标客户 | 专业级 Skill 企业、学术/工程研究机构（横向比 Skill、纵向比 Agent、斜向比超参） |
| 与标准版的关系 | **包含** SSKH 的全部 19 个页面与 37 个端点，行为一致 |
| 数据库 | H2 2.2.x（默认）/ MySQL 8，**29 张表** |

## 30 秒跑起来

```cmd
cd NS-ProSkillHub
start.cmd
```

首次启动：建 29 张表 → 导入 375 个 Skill → 库语义种子（同标准版）→
登记 **4 个 Agent**（`local-stub` / `deepseek-flash` / `glm-4-flash` / `ark-kimi`）与 **2 个 MCP**。
打开 <http://127.0.0.1:30802>。

## 这个版本比标准版多了什么（四块）

### 1. 连通性矩阵

**每个 Agent × 每个 MCP** 逐格探活，形成热力图与归因表。
探活口径：任何 HTTP 响应（含 401/403）都算"通道可达"——那证明网络与证书都通了；
只有连接失败 / TLS 失败 / 解析失败才算不通，并给出可执行的下一步。

### 2. 多维实测（三张实验表）

| 表 | 类型 | 回答什么 |
| --- | --- | --- |
| **(a) 单体实测** | `/eval/live` | 一次调用好不好？（输出 + 推理过程 + 9 项指标 + 与期望对比） |
| **(b) Skill 维度扫描** | `SKILL_SCAN` | 一个 Skill × 多 Agent × 多超参 × 多用例，各种条件下表现如何？ |
| **(c) Skill 横向对比** | `SKILL_COMPARE` | 多 Skill × 固定条件（单 Agent / 单参数 / 单场景），谁更强？ |

每格都保存输出、推理过程、时延、tokens 与指标分；对比页给出**按 Skill / 按 Agent / 按参数**
三张分组表 + TOPSIS 排名 + 中文结论。单元数上限 120（避免误点把共享模型打满）。

### 3. 知识图谱

**不用图数据库**：实体与关系落在 `kg_entity` / `kg_relation` 两张关系表，前端用 SVG 力导向图渲染。

| 关系 | 含义 |
| --- | --- |
| `BELONGS_TO` | Skill → 分类（能力归属） |
| `IN_SCENE` | Skill → 场景 |
| `HAS_TAG` | Skill → 标签 |
| `SIMILAR_TO` | 同分类且共享标签的 Skill 之间（权重 = 共享标签数） |
| `EVALUATED_BY` | Skill → Agent（有实测记录时） |

支持按场景下钻、节点详情、相似 Skill、导出 `JSON / CSV / GraphML`。
实体来源被严格限制在三处：Skill 库、分类本体、接入注册表 —— **不允许外部注入知识**。

### 4. 知识级分析（六种方法）

| 方法 | 输出 |
| --- | --- |
| `DESCRIBE` | n / 均值 / 标准差 / 中位数 / 直方图 |
| `HYPOTHESIS` | Welch t 检验：t / df / p / Cohen's d / 是否显著（分组不足时自动按用例回退） |
| `TOPSIS` | 多准则排序（均值权重 3、稳定性权重 1） |
| `CLUSTER` | k-means 聚类：每簇中心与成员 |
| `REGRESSION` | 一元最小二乘：系数 / R² / 结论（时延能否解释分数） |
| `AHP` | 层次分析法权重 + 一致性比率 CR |

每种方法都给一段**中文结论**，不只是一堆数字。

## 这个版本**不**做什么

- ❌ 训练 / 微调模型
- ❌ 外部向量数据库 / 图数据库（只用关系表 + 内存算法）
- ❌ 分布式调度 / 消息队列（矩阵串行执行，可解释、可复现）
- ❌ 登录鉴权（三系统统一：`X-SKH-Actor` / `X-SKH-Role`）
- ❌ Docker / 镜像 / 部署交付

## 相对标准版新增的代码

```
backend/src/main/java/org/skillhub/
├─ business/  ConnService（连通矩阵）· MatrixService（三种实验 + 分组对比 + TOPSIS）
│             KnowledgeService（图谱重建/查询/相似/导出）· InsightService（六种分析方法）
├─ mapper/    ProMapper（矩阵/单元/实体/关系/快照/报告）
├─ model/     ProEntities（6 个嵌套实体）
├─ dto/       ProDtos
└─ action/v1/ ProAction（/conn /eval/matrix /kg /ix）

frontend/src/
├─ api/       pro.js
└─ views/     ConnMatrixView · EvalLabView · MatrixDetailView · KgView · IxView
```

## 验收

```powershell
powershell -ExecutionPolicy Bypass -File scripts\verify.ps1
node scripts\api-test.mjs
cd <母本仓库> ; node tools\verify-all.mjs          # 三系统一致性（需要三个工程同时在仓库里）
```

当前状态：接口 **40/40 通过**，页面 **12/12 路由通过**，三系统一致性 **30/30 通过**。

## 已知限制（诚实清单）

1. **不做登录**：与另两个版本相同，身份头可伪造。
2. **矩阵串行执行**：4 Agent × 2 MCP × 2 参数 × 3 用例 = 48 格，离线桩毫秒级；
   接真实模型时按每格 1-3 秒估算，建议单次不超过 40 格。
3. **知识图谱是"轻图谱"**：关系由标签与分类**规则**生成，没有做实体消歧与语义嵌入。
4. **假设检验的 p 值用正态近似 + Edgeworth 修正**，样本量很小时（<10）结论仅供参考。
5. **聚类是纯 k-means（k=2..3）**，没有做肘部法选 k。
6. **离线桩会污染统计检验**：`local-stub` 与真实模型的分数不可直接比较（页面会标注 offline）。
7. **导出只导当前视图**（≤300 节点），全量导出请直接用 SQL/查询台。
