# PowerQueue · 新能源汽车充电桩高并发预约与调度系统

> 基于 **B/S 架构**的线上充电桩共享与调度平台,解决新能源车主高峰时段充电排队痛点。
> 包含车主**预约**、充电桩**状态实时查询**、空闲快充桩**「抢约」**等核心功能,前后端彻底分离。

---

## ✨ 功能特性

### 车主端
- 🔐 注册 / 登录(JWT 鉴权)
- 🗺️ 充电站地图列表 + 名称/地址搜索,实时空闲快充桩统计
- ⚡ 充电桩**实时状态网格**(空闲/已预约/充电中/故障,5 秒轮询刷新)
- 🏁 空闲快充桩**一键抢约**(高并发防超卖)
- 📋 我的预约:开始充电 / 结束结算 / 取消
- 👤 个人中心:资料编辑 + 余额充值

### 管理后台
- 📊 数据看板(ECharts:营收趋势、桩状态分布、订单状态、资源概览)
- 🏢 充电站管理(CRUD)
- 🔌 充电桩管理(CRUD,变更即时失效缓存)
- 🧾 订单管理(多条件分页查询)
- 👥 用户管理(CRUD / 启禁用 / 重置密码)

---

## 🛠 技术栈

| 层 | 技术 |
|----|------|
| 后端 | Java 17、Spring Boot 3.2、Spring MVC、MyBatis-Plus、Maven |
| 数据库 | MySQL 8 |
| 缓存 / 并发 | Redis、Redisson(分布式锁) |
| 鉴权 | JWT(jjwt)、BCrypt |
| 前端 | Vue 3、Vite、Vue Router、Pinia、Axios、Element Plus、ECharts |

> **JDK 8 用户**:如本机为 JDK 8,请将 `backend/pom.xml` 的 Spring Boot 版本改为 `2.7.18`、`java.version` 改为 `8`,并把代码中 `jakarta.*` 包名改回 `javax.*`(校验注解与 servlet)。默认方案为 JDK 17 + Spring Boot 3.2。

---

## 📁 项目结构

```
PowerQueue/
├── sql/
│   └── powerqueue.sql          # 建库 + 建表 + 索引 + 充电站/充电桩初始数据
├── backend/                    # Spring Boot 后端(用 IntelliJ IDEA 打开)
│   ├── pom.xml
│   └── src/main/java/com/powerqueue/
│       ├── common/             # 统一响应 Result、异常、枚举、当前用户上下文
│       ├── config/             # Redis / Redisson / MyBatis-Plus / CORS / JWT 拦截器 / 数据初始化
│       ├── controller/         # 认证、用户、站点、预约 + admin/* 管理接口
│       ├── service/            # 业务逻辑(含抢桩核心 ReservationServiceImpl)
│       ├── mapper/             # MyBatis-Plus Mapper(+ 自定义抢桩/统计 SQL)
│       ├── entity/ dto/ vo/    # 实体 / 请求 / 视图对象
│       └── utils/              # JWT 工具
│   └── src/test/...            # 抢桩并发防超卖测试
└── frontend/                   # Vue3 前端(用 WebStorm 打开)
    ├── package.json  vite.config.js
    └── src/
        ├── api/                # axios 封装 + 各模块接口
        ├── store/              # Pinia(登录态)
        ├── router/             # 路由 + 登录/角色守卫
        ├── styles/             # 深色科技主题令牌 + 全局样式
        ├── layouts/            # 车主端 / 管理后台布局
        └── views/              # auth / user / admin 页面
```

---

## 🚀 快速开始

### 0. 环境要求
- JDK 17+(或 JDK 8,见上文说明)、Maven 3.6+
- Node.js 16+(建议 18/20)
- MySQL 8、Redis 5+

### 1. 数据库
```bash
mysql -u root -p < sql/powerqueue.sql
# 或在 Navicat / DataGrip 中直接运行 sql/powerqueue.sql
```

### 2. 启动 Redis
```bash
redis-server
```

### 3. 后端(IDEA)
1. 用 IntelliJ IDEA 打开 `backend/` 目录(自动导入 Maven 依赖)。
2. **启动前必填**:打开 `src/main/resources/application.yml`,把占位符替换为你自己的值:
   - `spring.datasource.password`:`your_mysql_password` → 你本机 MySQL 密码
   - `powerqueue.jwt.secret`:改成你自己的随机密钥(HS384 算法要求 **≥ 48 字节**)
   - 若 Redis 设了密码,取消注释 `spring.data.redis.password` 并填写
3. 运行 `PowerQueueApplication`,默认端口 **8080**。
4. 首次启动会自动初始化账号与示例订单。

### 4. 前端(WebStorm)
```bash
cd frontend
npm install
npm run dev        # 默认 http://localhost:5173,已配置 /api 代理到后端
```

### 5. 登录
| 角色 | 账号 | 密码 |
|------|------|------|
| 管理员 | `admin` | `123456` |
| 车主 | `user` | `123456` |

---

## 🔥 高并发抢桩方案(核心亮点)

黄金时段大量车主同时抢同一个空闲快充桩,系统用**三道防线**杜绝「重复预定 / 超卖」:

1. **Redis 缓存降压**:热门站点的充电桩状态/空闲数缓存在 Redis(缓存旁路 + TTL),车主端轮询与列表查询优先读缓存,显著降低 MySQL 读压力。状态变更后主动失效缓存。
2. **Redisson 分布式锁**:抢桩按 `lock:pile:{pileId}` 加锁,把同一充电桩的并发请求**串行化**,配合用户级「进行中订单」校验防重复下单。
3. **数据库乐观锁(兜底)**:
   ```sql
   UPDATE charging_pile SET status='RESERVED', version=version+1
   WHERE id=? AND status='IDLE' AND version=?
   ```
   按**影响行数**判定是否抢到。即使分布式锁在事务提交前释放,后续请求也会因 `version` 已自增而更新失败,从根本上保证不超卖。

### 并发测试(防超卖证明)
`backend/src/test/java/com/powerqueue/ReservationConcurrencyTest.java`
模拟 **20 个不同车主并发抢同一个充电桩**,断言:
- 只有 **1 个**请求成功
- 桩最终状态为 `RESERVED`
- 仅生成 **1 条**订单

```bash
cd backend
mvn test -Dtest=ReservationConcurrencyTest   # 需 MySQL + Redis 已启动
```

---

## 🗄️ 数据库设计与优化

- 四张核心表:`user` / `station` / `charging_pile` / `reservation`。
- 针对高频查询建立**联合索引**:
  - `charging_pile(station_id, status)` — 站点下查空闲桩(抢桩高频)
  - `reservation(user_id, status)` — 我的预约
  - `reservation(pile_id, status)` — 桩占用查询 / 防重复占用
  - `reservation(order_no)` 唯一键、`user(username)` 唯一键
- 看板统计、订单分页采用**自定义 SQL + 多表 JOIN**(见 `mapper/*.xml`)。
- 逻辑删除(`deleted`)+ 乐观锁(`version`)由 MyBatis-Plus 统一管理。

---

## 📡 主要接口

| 模块 | 方法 & 路径 | 说明 |
|------|------------|------|
| 认证 | `POST /api/auth/register` `POST /api/auth/login` | 注册 / 登录 |
| 用户 | `GET /api/user/me` `PUT /api/user/profile` `POST /api/user/recharge` | 个人中心 |
| 站点 | `GET /api/stations` `GET /api/stations/{id}` `GET /api/stations/{id}/piles` | 列表/详情/实时桩位 |
| 预约 | `POST /api/reservations` | **抢桩** |
| 预约 | `GET /api/reservations` `PUT /api/reservations/{id}/start|finish|cancel` | 我的预约 / 状态流转 |
| 后台 | `GET /api/admin/dashboard` | 数据看板 |
| 后台 | `/api/admin/stations|piles|users|reservations` | 管理 CRUD / 分页 |

> 所有响应统一封装为 `{ code, message, data }`;`/api/admin/**` 需要 ADMIN 角色。

---

## 🎨 界面设计

深色科技 + 能源主题:深色基底、能源绿(`#14e0a0`)→ 电光青(`#2ad6ff`)渐变品牌色、玻璃态卡片、充电桩发光状态格与脉冲动画、ECharts 数据可视化。
