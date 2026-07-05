# PowerQueue · 新能源汽车充电桩高并发预约与调度系统

> 基于 Spring Boot 3 + Vue 3 的四层智能化充电调度平台。从"抢桩防超卖"到"LLM 自然语言助手"，全链路落地。

---

## 🏗 四层智能架构

| 层级 | 定位 | 核心能力 |
|------|------|---------|
| **L1 调度智能** | P0 核心 | 四因子加权推荐引擎、Redis Sorted Set 等待队列、Redisson 分布式锁 + 锁内重读 + MySQL 乐观锁三重防线、10 分钟确认占位窗口 + 超时自动让位 |
| **L2 预测智能** | P1 | 动态供需定价（时段系数 × 站点负载系数 + 滞后区防震荡）、移动平均 + 季节性分解需求预测 |
| **L3 体验智能** | P1 | WebSocket 实时推送（JWT 鉴权 + 离线补偿 + 心跳重连）替代 5s 轮询、充电计划向导 |
| **L4 交互智能** | P1 杀手锏 | DeepSeek 大模型两阶段编排（意图提取 → 平台数据注入 → 语言组织）从架构杜绝幻觉 + Resilience4j 熔断/限流/降级 |

---

## ✨ 功能特性

### 车主端（6 个页面）
- 🔐 注册 / 登录（JWT + BCrypt）
- ⚡ **智能充电**：地名搜索 / GPS 定位 → 四因子打分推荐最优桩位 → 一键抢桩或自动入队
- 🗺️ **充电地图**：站点列表 + 三色负载标签 🟢🟡🔴 + 实时空闲统计
- 🔌 **站点详情**：WebSocket 实时桩状态网格、动态电价、加入排队、8 小时预测条
- 📋 **我的预约**：状态流转（排队 → 确认占位 → 充电 → 结算）、10 分钟倒计时
- 🤖 **AI 助手**：自然语言问答（"附近哪个桩便宜还不排队？"），回复带桩卡片
- 📅 **充电计划**：向导式创建 + Cron 定时自动预约 + 漏充提醒
- 👤 个人中心：资料编辑 + 余额充值

### 管理后台
- 📊 数据看板（ECharts：营收趋势、桩状态分布、订单统计）
- 🏢 充电站 / 🔌 充电桩 CRUD
- 🧾 订单管理（多条件分页）
- 👥 用户管理（CRUD / 启禁用 / 重置密码）

---

## 🛠 技术栈

| 层 | 技术 |
|----|------|
| 后端 | Java 17、Spring Boot 3.2、Spring MVC、MyBatis-Plus、Maven |
| 数据库 | MySQL 8 |
| 缓存 / 并发 | Redis、Redisson（分布式锁） |
| 鉴权 | JWT（jjwt 0.12.x）、BCrypt |
| 实时通信 | WebSocket（Spring WebSocket） |
| LLM | DeepSeek API + Resilience4j（熔断 / 限流 / 降级） |
| 定时任务 | Spring @Scheduled（预测预计算 / 计划扫描 / 超时让位） |
| 前端 | Vue 3、Vite、Vue Router、Pinia、Axios、Element Plus、ECharts |

---

## 🚀 快速开始

### 0. 环境要求
- JDK 17+、Maven 3.6+
- Node.js 16+（建议 18/20）
- MySQL 8、Redis 5+

### 1. 环境变量（启动前必须设置）

```bash
# 必填
export MYSQL_PASSWORD=你的MySQL密码
export POWERQUEUE_JWT_SECRET=至少48字节的随机字符串

# 可选（不设则 AI 助手自动降级、演示账号使用默认密码）
export DEEPSEEK_API_KEY=sk-xxxxxxxx
export POWERQUEUE_DEMO_PASSWORD=你的演示密码   # 默认: powerqueue_demo_2026
```

### 2. 数据库
```bash
mysql -u root -p < sql/powerqueue.sql
```

首次启动时 `DataInitializer` 会自动执行 V2 增量迁移（建表 + 索引 + 种子数据），无需手动执行 v2 SQL。

### 3. 启动 Redis
```bash
redis-server
```

### 4. 后端
```bash
cd backend
mvn spring-boot:run
# 或 IDE 中运行 PowerQueueApplication
# 默认端口 8080
```

### 5. 前端
```bash
cd frontend
npm install
npm run dev        # http://localhost:5173, 已配置 /api 代理到 :8080
```

### 6. 登录
| 角色 | 账号 | 密码 |
|------|------|------|
| 管理员 | `admin` | 环境变量 `POWERQUEUE_DEMO_PASSWORD` |
| 车主 | `user` | 环境变量 `POWERQUEUE_DEMO_PASSWORD` |

首次启动自动创建以上账号与示例历史订单。

---

## 🔥 核心亮点

### 抢桩防超卖（四重防线 + 桩预留）

```
用户抢桩请求
  │
  ├─ 防线 1: lock:user:{userId}  —— 单用户同时只能持有一个进行中订单
  ├─ 防线 2: lock:pile:{pileId}  —— 同桩并发请求串行化
  ├─ 防线 3: 锁内重读桩状态      —— 防快照过期
  └─ 防线 4: DB 乐观锁           —— WHERE status IN ('IDLE','RESERVED') AND version=?
```

- **桩预留机制**：队首用户确认窗口期内桩锁定为 `RESERVED`，队列外用户无法插队
- **超时自动让位**：@Scheduled 每分钟扫超时未确认订单，自动轮换队首
- **状态机闭环**：PENDING → QUEUED → WAITING_CONFIRM → PENDING/CHARGING → FINISHED/CANCELLED，全路径加锁保护

### 并发表明测试
```bash
cd backend
mvn test -Dtest=ReservationConcurrencyTest   # 需 MySQL + Redis 已启动
```
20 个线程同时抢同一桩 → 断言仅 1 笔成功、订单唯一、桩状态正确流转。

### 智能排队转化
满桩自动入队 → Redis Sorted Set 优先级排队 → 预估等待时间 → 轮到后 WebSocket 推送 `QUEUE_TURN` → 10 分钟确认窗口 → 超时自动让位给下一位。

### 动态供需定价
```
最终电价 = 基础电价 × 时段系数(谷0.7 / 平1.0 / 峰1.5) × 站点负载系数(>50%→0.9引流 / <20%→1.3分流)
```
带 5% 滞后区防价格震荡，开充时锁定价格快照，用户不被中途调价影响。

### DeepSeek AI 助手
两阶段编排杜绝幻觉：意图提取（槽位识别）→ 平台真实数据注入（桩位 / 价格 / 等待时间）→ 语言组织。Resilience4j CircuitBreaker + RateLimiter 保护 LLM 调用。

---

## 📡 主要接口

| 模块 | 方法 & 路径 | 说明 |
|------|------------|------|
| 认证 | `POST /api/auth/login` `POST /api/auth/register` | 登录 / 注册 |
| 用户 | `GET/PUT /api/user/me` `POST /api/user/recharge` | 个人中心 / 充值 |
| **推荐** | `POST /api/recommend` | **智能匹配推荐（四因子打分）** |
| **队列** | `POST /api/queue/enqueue` `POST /api/queue/confirm` `POST /api/queue/leave` | **入队 / 确认占位 / 退出排队** |
| **定价** | `GET /api/pricing/calc` | **动态电价计算** |
| 站点 | `GET /api/stations` `GET /api/stations/{id}` `GET /api/stations/{id}/piles` `GET /api/stations/{id}/load` `GET /api/stations/{id}/forecast` | 列表 / 详情 / 桩位 / 负载 / 预测 |
| 预约 | `POST /api/reservations` `GET /api/reservations` `PUT /api/reservations/{id}/start\|finish\|cancel` | 抢桩 / 我的预约 / 状态流转 |
| **AI** | `POST /api/ai/chat` | **DeepSeek 自然语言对话** |
| **充电计划** | `GET/POST/PUT/DELETE /api/charge-plans` | **向导创建 + 定时自动预约** |
| 后台 | `GET /api/admin/dashboard` `/api/admin/stations\|piles\|users\|reservations` | 看板 / 管理 CRUD |

> `WebSocket /ws/charging?token=xxx` 实时推送：`PILE_STATE` / `QUEUE_TURN` / `CONFIRM_TIMEOUT` / `PLAN_REMIND`

---

## 🗄️ 数据库

核心表：`user` / `station` / `charging_pile` / `reservation` / `score_weight_config` / `pricing_rule` / `demand_forecast` / `charge_plan` / `ai_chat_log`

关键索引：
- `charging_pile(station_id, status)` — 站点下查空闲桩（抢桩高频）
- `reservation(pile_id, status)` — 桩占用查询 / 防重复占用
- `reservation(user_id, status)` — 我的预约
- `reservation(status, reserve_time)` — 超时让位扫描
- `reservation(pile_id, queue_state)` — 排队队首轮换

---

## 📁 项目结构

```
PowerQueue/
├── sql/                          # 建库脚本（V1 基础 + V2 增量）
├── docs/                         # 设计文档（架构 / 简历 / 面试 / 接口）
├── backend/                      # Spring Boot 后端
│   ├── src/main/java/com/powerqueue/
│   │   ├── common/               # 统一响应 / 异常 / 枚举 / 上下文
│   │   ├── config/               # Redis / Redisson / CORS / JWT / DataInit
│   │   ├── controller/           # 前端接口 + admin 管理接口
│   │   ├── service/              # 业务逻辑（含四层智能化各模块）
│   │   ├── scheduler/            # 定时任务（预测 / 计划 / 超时让位）
│   │   ├── ws/                   # WebSocket 握手 / 广播 / 会话管理
│   │   ├── ai/                   # DeepSeek 客户端 / 配置
│   │   ├── mapper/ entity/ dto/ vo/
│   │   └── utils/                # JWT 工具
│   └── src/test/                 # 抢桩并发防超卖集成测试
└── frontend/                     # Vue 3 前端
    └── src/
        ├── api/                  # axios 封装（含 recommend / queue / price / ai / chargeplan）
        ├── utils/                # WebSocket 单例管理器
        ├── router/ layouts/ store/ styles/
        └── views/                # user（6 页）+ admin（1 页）+ auth
```
