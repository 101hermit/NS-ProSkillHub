---
name: supplier-finder
description: "基于全网络多渠道检索策略，快速发现全球范围内的潜在供应商，适用于食品原料及制造业采购场景。"
version: 1.0.2
license: unverified
category: traffic
scene: tra
origin: pro
source: https://skillhub.rigol.com/api/v1/download?slug=supplier-finder
---
# 🔍 SKILL: Supplier Discovery (Full-Network Edition)

## 🎯 功能
基于**全网络渠道（搜索引擎 + B2B平台 + 行业数据库 + 社交媒体 + 海关数据）**，系统化挖掘潜在供应商。

---

## 🔄 执行流程

### 1️⃣ 智能关键词生成（核心引擎）

自动构建多语言关键词：

#### 英文
- {product} manufacturer
- {product} supplier
- {product} factory
- {product} OEM / bulk / wholesale

#### 中文
- {产品} 生产厂家
- {产品} 工厂
- {产品} 批发
- {产品} OEM

#### 进阶（高价值）
- {product} HS code supplier
- {product} export company
- {product} GMP factory

---

### 2️⃣ 全渠道检索矩阵（🔥核心升级）

#### 🌐 搜索引擎
- Google（主力）
- Bing
- 百度
- Yandex（俄语区）

---

#### 🏭 B2B平台
- Alibaba（国际站）
- 1688（中国内贸）
- Made-in-China
- Global Sources
- ThomasNet（北美工业）
- EC21（韩国）
- TradeIndia（印度）

---

#### 🧾 行业数据库 / 名录
- 海关进出口数据（Panjiva / ImportGenius）
- Yellow Pages（全球黄页）
- 行业协会网站
- 展会参展商名单（广交会 / 食品展）

---

#### 🏢 企业信息平台
- 天眼查 / 企查查（中国）
- OpenCorporates（全球）
- Crunchbase（部分适用）

---

#### 💬 社交 & 内容平台（高价值补充）
- LinkedIn（找工厂负责人）
- YouTube（工厂展示视频）
- Facebook（东南亚供应商常用）
- Reddit / 行业论坛（口碑信息）

---

#### 🛒 电商反向挖掘（隐藏供应商）
- Amazon（品牌反查供应链）
- 淘宝 / 京东（爆款反推工厂）

---

### 3️⃣ 数据去重与合并

规则：
- 同一公司多平台出现 → 合并
- 优先保留官网信息
- 保留最完整数据源

---

### 4️⃣ 初筛过滤

保留：
- 有公司名称
- 有产品信息
- 有联系方式或官网

过滤：
- 空壳页面
- 无主体信息
- 低质量中介

---

### 5️⃣ 供应商分类

自动标记：

- manufacturer（生产商）
- trader（贸易商）
- brand（品牌方）

---

### 6️⃣ 输出结构化数据

```json
{
  "suppliers": [
    {
      "name": "XXX Co., Ltd",
      "region": "China",
      "type": "manufacturer",
      "products": "citric acid",
      "source": "Alibaba",
      "link": "https://xxx.com",
      "notes": "支持OEM，出口型企业"
    }
  ]
}
