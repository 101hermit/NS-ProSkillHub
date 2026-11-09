---
name: failure-mode-generation-kvcache
description: "分析 yuanrong-datasystem 的 KVCACHE access/runtime 日志、K_* 错误码和全量调用图，生成故障模式节点、分层 DAG 与错误码信息表。适用于全量重建或按当前源码快照更新 KVCACHE 故障知识；不用于只分析一条普通日志。"
version: latest
license: UNKNOWN
category: quality
scene: general
origin: easy
source: https://gitcode.com/openeuler/witty-ub/blob/master/.opencode/skills/failure-mode-generation-kvcache/SKILL.md
---
# KVCACHE 故障模式生成

## 输入、输出与边界

输入必须是 yuanrong-datasystem 仓库根，同时存在 `include/datasystem/utils/status.h` 和 `src/datasystem`。

输出：

- `data/kvcache/kvcache_failure_mode.json`
- `data/failure_mode_tree.json` 的 `kvcache` key
- `data/kvcache/kvcache_error_code_info.json`

不修改 `data/kvcache/kvcache_conn_fault_mode.json`，也不改变 `failure_mode_tree.json` 的其他 key。

每次必须重新扫描当前源码。允许复用 JSONL checkpoint，但只有函数体、日志模板、状态表达式等组成的 `content_hash` 唯一且完全一致时才复用；不得用旧 JSON、git 历史或来源不明的 `/tmp` 文件代替扫描。

## 按需读取详细契约

- 分析 runtime groups、日志模板、Status、局部调用图或 DAG 时，读取 [references/runtime-analysis.md](references/runtime-analysis.md)。
- 生成节点字段、access 根或错误码信息表时，读取 [references/output-contract.md](references/output-contract.md)。
- 写入最终文件前，读取并执行 [references/validation.md](references/validation.md)。

不要提前加载与当前阶段无关的 reference。执行完整生成时三个 reference 都必须在对应阶段读取。

## 生成当前快照的中间产物

从本仓库根运行：

```bash
python3 .opencode/skills/failure-mode-generation-kvcache/scripts/extract_status_codes.py \
  <yuanrong-datasystem-repo-root>
python3 .opencode/skills/failure-mode-generation-kvcache/scripts/find_kvcache_runtime_err.py \
  <yuanrong-datasystem-repo-root>
python3 .opencode/skills/failure-mode-generation-kvcache/scripts/prepare_kvcache_runtime_batches.py \
  /tmp/kvcache_runtime_err.json --lane semantic --target-groups 30
python3 .opencode/skills/failure-mode-generation-kvcache/scripts/kvcache_runtime_state.py init
python3 .opencode/skills/failure-mode-generation-kvcache/scripts/find_kvcache_access_err.py \
  <yuanrong-datasystem-repo-root>
python3 .opencode/skills/failure-mode-generation-kvcache/scripts/generate_kvcache_callchains.py \
  <yuanrong-datasystem-repo-root>
python3 .opencode/skills/failure-mode-generation-kvcache/scripts/analyze_kvcache_status_ast.py \
  <yuanrong-datasystem-repo-root>
```

产物：

| 路径 | 用途 |
| --- | --- |
| `/tmp/kvcache_status_codes.json` | 全部 K_* 覆盖基准 |
| `/tmp/kvcache_runtime_err.json` | 物理点、模板、分流和安全聚合组 |
| `/tmp/kvcache_runtime_batches/` | callable 局部语义批次 |
| `/tmp/kvcache_runtime_state.jsonl` | 内容寻址的分析 checkpoint |
| `/tmp/kvcache_access_err.json` | AccessRecorderKey、构造点和 Result 点 |
| `/tmp/kvcache_callchains.json` | 全量保守调用图，只供查询脚本读取 |
| `/tmp/kvcache_status_ast.json` | Clang AST Status 传播证据 |

调用图 `analysis.complete=false` 时先修复编译数据库或 TU 失败，不得继续构图。AST 失败不阻塞全量工作，但失败或不完整的组必须回退人工源码分析。

## Token 受控工作流

1. 校验中间产物 fingerprint 和数量，建立全部 K_* 错误码信息表。
2. 为 `status.h` 中每个非零 K_* 建 access 根，不依赖该错误码是否出现在 runtime groups；无 runtime 子节点时在树中保留空数组。随后追加 code=0/respMsg 和 FATAL 两个特殊根。
3. 先处理 `deterministic` groups；它们的错误码和日志模板均已由脚本确定，可直接采用，不重复逐物理点阅读源码。脚本不得把 `template_confidence=dynamic_only` 的组放入该 lane。
4. 对 `semantic` groups，按 `skill_analysis` 的任务类型处理：`resolve_status_code` 先采用 `kvcache_status_ast.json` 中同 `content_hash` 的完整唯一归码；`resolve_log_template` 必须读取当前 callable，追踪局部字符串构造、包装宏及上游消息来源。其余未解决任务按 batch 顺序处理。一个 callable 只读取一次，同时完成其中所有组。
5. 每组完成后将分析记录写为小 JSON，并执行：

   ```bash
   python3 .opencode/skills/failure-mode-generation-kvcache/scripts/kvcache_runtime_state.py \
     record <group_id> resolved --result <result.json>
   ```

6. 建关系时不得读取全量调用图。按 group 或 batch 查询局部 slice：

   ```bash
   python3 .opencode/skills/failure-mode-generation-kvcache/scripts/query_kvcache_callgraph.py \
     --group-id <group_id> --depth 3 --direction callers
   ```

   深度不足时递增查询；匹配歧义时读当前 callable 源码消歧。只有源码证明失败被记录、包装或透传时才建边。
7. 全部 state 进入 `resolved`/`excluded` 后，渲染三个最终 JSON，执行完成闸门再提交。

   ```bash
   python3 .opencode/skills/failure-mode-generation-kvcache/scripts/validate_kvcache_pipeline.py \
     --batches /tmp/kvcache_runtime_batches \
     --state /tmp/kvcache_runtime_state.jsonl --require-complete \
     --ast /tmp/kvcache_status_ast.json \
     --callgraph /tmp/kvcache_callchains.json \
     --nodes data/kvcache/kvcache_failure_mode.json \
     --tree data/failure_mode_tree.json
   ```

## 不可放宽的原则

- `entries` 只用于物理点覆盖；分析和建节点以 `groups` 为单位。
- 不因公共 API 不可达而排除 RPC、线程、回调或静态入口。
- 唯一 K_* 必须有完整传播证据；动态、多码或外部码写 `null`。
- `need_skill` 由非空 `skill_analysis` 推导；错误码已确定但日志模板未确定时仍必须进入 semantic lane。
- 原则上不得生成“无稳定关键字”。源码分析后仍无法得到业务稳定字面量时，必须记录完整证据并显式禁用该节点的日志匹配。
- 每个 runtime 节点都必须通过分析故障点及必要上下游源码给出可能的解决办法；无法唯一确定时写明适用条件和验证方向，不得默认填写“无”。
- 每个 runtime 节点必须有真实父节点，DAG 不得成环。
- 任何候选、错误码、关系或排除都必须可由当前源码快照和 checkpoint 证据追溯。
