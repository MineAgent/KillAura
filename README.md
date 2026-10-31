# Kill Aura (Reimplementation)

Minecraft **26.2** / Fabric 客户端模组，行为复刻自 `killaura-fabric-26.1-26.3-1.0.0.jar`
（作者 pokato，MIT 许可，见 [NOTICE.md](NOTICE.md)）。代码针对 26.2 单独重写，不再包含原模组的反射兼容层。

自动攻击距离内的生物。**没有游戏内按键**：开关与配置通过
[MGHttpdProvider](https://github.com/MineAgent/HttpdProvider) 的共享 HTTP 服务
（`127.0.0.1:3420/ka`）控制，配置保存在 `config/killaura.json`。

> ⚠️ 仅客户端模组，不改服务端。用于多人服务器可能违反服务器规则，请自行确认。

## 构建与安装

```bash
./gradlew build          # 产物：build/libs/killaura-reimpl-1.3.0.jar
./gradlew test           # 单元测试（角度、距离、配置、JSON、目标规则）
```

- JDK 25（`options.release = 25`）
- 运行时需要：Fabric Loader ≥ 0.19、**Fabric API**（26.2 版）、**MGHttpdProvider ≥ 1.1**
- 将 `killaura-reimpl-1.3.0.jar` 放入 `.minecraft/mods/`；MGHttpdProvider 不随模组打包
- `fabric.mod.json` 声明依赖 `httpdprovider >= 1.1`、`fabric-lifecycle-events-v1`；
  没装 provider 时 Fabric 会直接拒绝加载

## HTTP API

服务由 MGHttpdProvider 提供，本模组注册 `/ka` 前缀。

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| GET/HEAD | `/ka/` | 使用说明 |
| GET/HEAD | `/ka/state` | 当前状态 JSON |
| POST | `/ka/on` | 开启（已开启时幂等） |
| POST | `/ka/off` | 关闭（已关闭时幂等） |
| POST | `/ka/toggle` | 切换 |
| POST | `/ka/reload` | 重新读取 `config/killaura.json` |
| POST | `/ka/config` | 部分更新配置（JSON），校验后写回 |

```bash
curl http://127.0.0.1:3420/ka/state
curl -X POST http://127.0.0.1:3420/ka/on
curl -X POST http://127.0.0.1:3420/ka/toggle
curl -X POST http://127.0.0.1:3420/ka/reload
curl -X POST -H 'Content-Type: application/json' \
     --data-binary '{"range":4.5,"rotation":"visible"}' \
     http://127.0.0.1:3420/ka/config
./ka state          # 等价的薄封装脚本
./ka on
./ka config '{"range":4.5}'
```

### 状态 JSON

```json
{
  "ok": true,
  "enabled": true,
  "inWorld": true,
  "player": "DSH",
  "config": { "range": 3.0, "attackHostile": true, "...": "..." },
  "target": { "name": "Zombie", "id": 123, "distance": 2.31, "health": 20.0 },
  "lastAttack": { "name": "Zombie", "agoMs": 120 }
}
```

- `target`：仅开启且在世界中时现算；附近没有合法目标时为 `null`
- `lastAttack`：最近一次实际 `attack` 的名字与距今毫秒；本次启动还没打过时为 `null`
- `player`：未进世界时为 `null`
- 所有写操作成功时都返回这份完整状态

### 配置 patch

请求体是 JSON 对象，只改出现的字段，最多 16 KiB：

```json
{
  "range": 4.5,
  "rotation": "visible",
  "targetPlayers": false,
  "attackHostile": true,
  "attackFriendlyDrops": false,
  "denyTargets": ["minecraft:creeper"],
  "alwaysTargetsHigh": ["minecraft:player"],
  "alwaysTargetsMedium": [],
  "alwaysTargetsLow": [],
  "alwaysTargetsLowest": []
}
```

- 数字：`range` 0.5–6.0、`fov` 1–360、`minAttackCharge` 0–1，必须是有限数字
- 布尔：`targetPlayers`、`attackHostile`、`attackFriendlyDrops`、`attackFriendlyNoDrops`、
  `targetArmorStands`、`ignoreInvisible`、`throughWalls`、`pauseInScreens`、`pauseWhileUsingItem`
- 字符串数组：`denyTargets`、`alwaysTargetsHigh`、`alwaysTargetsMedium`、`alwaysTargetsLow`、
  `alwaysTargetsLowest`，元素是实体类型 ID（如 `minecraft:zombie`，可省略 `minecraft:` 前缀）。
  只做语法校验，拼错的合法 ID 不会匹配任何实体
- 字符串：`rotation` = `silent|visible|none`；`priority` = `closest|lowestHealth|health|angle`
- **全有或全无**：任一字段非法则 400，文件不写；`_help` 只读，未知字段报错
- 旧字段 `targetHostile` / `targetPassive` / `alwaysTargets` 已移除；HTTP 里再传会 400 并提示新字段
- 与文件加载不同，HTTP patch 严格拒绝越界值，不静默夹紧

### 返回码

| 状态码 | 含义 |
| --- | --- |
| 200 | 成功（JSON 状态） |
| 204 | OPTIONS |
| 400 | JSON 语法错误 / 字段非法 / 空请求体 |
| 404 | 路径不存在 |
| 405 | 方法不允许（带 `Allow`） |
| 409 | 客户端未就绪或正在关闭 |
| 413 | 请求体过大（>16KB） |
| 500 | 内部错误 |
| 504 | 客户端线程 5 秒内未响应 |

所有读写都通过 `Minecraft.execute` 投递到客户端线程并等待，和 tick 串行，无需额外锁。

## 行为

| 项 | 说明 |
|---|---|
| 控制 | 仅 HTTP（`/ka/on`、`/ka/off`、`/ka/toggle`），无按键 |
| 触发条件 | 存活、非旁观、攻击冷却 ≥ `minAttackCharge`、未暂停 |
| 目标 | 视线/距离内符合条件的 `LivingEntity`，按分类开关和四个 always 数组筛选 |
| 攻击 | `MultiPlayerGameMode#attack` + 主手 swing |
| 转向 | `silent` 发 `ServerboundMovePlayerPacket.Rot`；`visible` 直接改本地 yaw/pitch；`none` 不转向 |
| 选目标 | 包围盒最近点距离 ≤ `range`；可选 FOV、隔墙、隐身、玩家/盔甲架过滤 |
| 优先级 | `closest`（距离）、`lowestHealth`（血量×1000+距离）、`angle`（偏角×1000+距离） |
| 强制目标 | 按 `alwaysTargetsHigh` > 普通敌意 > `alwaysTargetsMedium` > 普通友善有掉落 > `alwaysTargetsLow` > 普通友善无掉落 > `alwaysTargetsLowest` 选择；同档内用 `priority` |

### 目标分类

| 分类 | 判定 | 典型生物 |
|---|---|---|
| 敌意 | 实现 `Enemy` 且不是 `NeutralMob`，或属于 26.2 显式敌对例外；蜘蛛按光照 | 僵尸、骷髅、苦力怕、史莱姆、恶魂、监守者、猪灵蛮兵、疣猪兽、僵尸鹦鹉螺、硫方怪 |
| 中立 | 实现 `NeutralMob`，或属于条件敌对表 | 末影人、僵尸猪灵、狼、铁傀儡、北极熊、蜜蜂、猪灵、羊驼、行商羊驼、熊猫、山羊、河豚 |
| 友善有掉落 | 友善，且默认实体战利品表非空 | 牛、猪、羊、鸡、猫、鹦鹉、马、驴、骡、雪傀儡、铜傀儡、鹦鹉螺、骆驼尸壳 |
| 友善无掉落 | 友善，且默认实体战利品表为空 | 悦灵、犰狳、美西螈、蝙蝠、骆驼、狐狸、青蛙、快乐恶魂、豹猫、嗅探兽、蝌蚪、村民、流浪商人 |

- 条件敌对表：`piglin`、`llama`、`trader_llama`、`panda`、`goat`、`pufferfish`。
- 26.2 显式例外：`zombie_nautilus`、`sulfur_cube` 强制敌意；`mannequin` 强制中立；
  `camel_husk`、`nautilus`、`copper_golem` 归友善有掉落；`happy_ghast` 归友善无掉落。
- `spider` / `cave_spider` 按 `getLightLevelDependentMagicValue() >= 0.5` 动态判定：
  亮处中立、暗处敌意。与原版 `SpiderTargetGoal` 一致；白天被打仍会反击。
- 中立生物不受三个攻击开关控制，只能用 `alwaysTargets*` 数组点名攻击。
- 无掉落判定取自 26.2 客户端 jar 的 `data/minecraft/loot_table/entities/*.json`：
  `pools` 为空即无掉落。只统计默认战利品表，不统计玩家给坐骑/宠物穿戴的鞍、马铠、狼铠、挽具等装备。
- 所有硬编码分类/掉落数据、提取脚本和复现命令见
  [docs/HARDCODED_DATA.md](docs/HARDCODED_DATA.md)。

### 配置 `config/killaura.json`

目标字段已不再与原模组一致。旧文件里的 `targetHostile` / `targetPassive` 会自动迁移：
`targetHostile` → `attackHostile`；`targetPassive=true` → 两个 friendly 开关都开。
旧 `alwaysTargets` 会自动迁移到 `alwaysTargetsHigh`。旧字段和 `showToggleMessage` 会在下次写回时移除。
旧 `targetPassive` 里包含的条件敌对生物（末影人、狼、猪灵等）现在归中立，默认不再被攻击，
需要时请加入对应的 `alwaysTargets*` 数组。

| 字段 | 默认 | 含义 |
|---|---|---|
| `range` | `3.0` | 攻击距离（0.5–6.0） |
| `fov` | `360.0` | 三维视野锥角（1–360，360 表示不限制） |
| `rotation` | `"visible"` | `silent` / `visible` / `none` |
| `priority` | `"closest"` | `closest` / `lowestHealth` / `angle`（`health` 视为 `lowestHealth`） |
| `minAttackCharge` | `0.0` | 仅当攻击蓄力 ≥ 该值（0–1）才出手；0 = 尽可能快 |
| `targetPlayers` | `true` | 攻击玩家（排除旁观/创造） |
| `attackHostile` | `true` | 攻击敌意目标 |
| `attackFriendlyDrops` | `false` | 攻击友善且有掉落目标 |
| `attackFriendlyNoDrops` | `false` | 攻击友善且无掉落目标 |
| `denyTargets` | `[]` | 永不攻击的实体类型 ID 数组 |
| `alwaysTargetsHigh` | `[]` | 最高优先级强制目标 |
| `alwaysTargetsMedium` | `[]` | 高于普通友善有掉落、低于普通敌意的强制目标 |
| `alwaysTargetsLow` | `[]` | 高于普通友善无掉落、低于普通友善有掉落的强制目标 |
| `alwaysTargetsLowest` | `[]` | 最低优先级强制目标 |
| `targetArmorStands` | `false` | 攻击盔甲架 |
| `ignoreInvisible` | `false` | 忽略隐身目标（所有类型，包括盔甲架） |
| `throughWalls` | `false` | 无视视线遮挡 |
| `pauseInScreens` | `true` | 打开界面时暂停 |
| `pauseWhileUsingItem` | `true` | 使用物品时暂停 |

是否攻击的判定优先级不变：

1. `denyTargets`：命中即永不攻击；与任一 `alwaysTargets*` 同时命中时拒绝优先。
2. 任一 `alwaysTargets*`：强制合法；绕过 `targetPlayers`、`targetArmorStands`、
   `ignoreInvisible`、`throughWalls` 的视线检查，但仍受 `range`、`fov`、暂停、蓄力限制。
3. `targetPlayers` / `targetArmorStands` / `ignoreInvisible` / `throughWalls`：普通目标过滤。
4. `attackHostile` / `attackFriendlyDrops` / `attackFriendlyNoDrops`：最后按分类决定。

多个合法目标的选择顺序：

1. `alwaysTargetsHigh`
2. 普通敌意目标
3. `alwaysTargetsMedium`
4. 普通友善有掉落目标
5. `alwaysTargetsLow`
6. 普通友善无掉落目标
7. `alwaysTargetsLowest`

“普通 xxx 目标”指被对应攻击开关匹配到、且不在任一 `alwaysTargets*` 数组中的目标。
同一档位内才按 `priority` 评分选择；档位更高者即使评分更差也优先。
玩家/盔甲架没进 always 数组时按“友善无掉落”档位排序，是否可打仍只看
`targetPlayers` / `targetArmorStands`。

数组元素是精确的实体类型 ID（如 `minecraft:zombie`、`minecraft:player`、`minecraft:armor_stand`），
不匹配子类型、标签或 UUID；可省略 `minecraft:` 前缀。同一 ID 出现在多个 always 数组时最高档生效。
文件加载时非法 ID 会被丢弃，HTTP patch 中非法 ID 返回 400 且整个 patch 不写回。

配置在首次启动时自动生成；开启（`/ka/on`）时会重新读取一次，非法值夹紧/规范化后写回。
运行中手改文件后调用 `/ka/reload` 生效。开关状态不持久化，重启后为关闭。

## 与原模组的关系

- 原模组是 **MIT** 许可（jar 内 `LICENSE_killaura`），本项目在 [NOTICE.md](NOTICE.md) 中保留其版权声明。
- 评分公式、判定顺序等核心行为与原模组对齐；目标分类和配置字段已扩展为
  敌意/中立/友善有掉落/友善无掉落四类，不再与原模组字段完全一致。
- 去掉原模组的 `J` 键和 `showToggleMessage` 动作栏提示，控制改由 MGHttpdProvider 提供。
- 原模组用反射同时兼容 26.1–26.3；本项目只面向 26.2，直接调用官方 API，因此没有 `Compat` 层。
- 26.2 中 `MultiPlayerGameMode#attack` 已负责攻击包，`LocalPlayer#swing` 负责挥手包，
  无需额外 `ServerboundPunchPacket`。
- 模组 id 为 `killaura-reimpl`，可与原模组区分；配置文件仍叫 `killaura.json`，
  旧 `targetHostile` / `targetPassive` / `alwaysTargets` 会自动迁移。

## 验证

单元测试 51 个（`AnglesTest`、`AuraSettingsTest`、`AuraSettingsPatchTest`、`AuraSnapshotTest`、
`TargetClassifierTest`、`TargetFilterTest`、`TargetRulesTest`）全部通过。

用 HMCL 的 26.2-Fabric 实例（带 Fabric API 0.160.0+26.2、MGHttpdProvider 1.1.1、mcctl）
以 Xvfb 无头启动，实测：

- 旧配置自动迁移：`targetHostile` / `targetPassive` / `alwaysTargets` 被写回新字段，
  旧 `alwaysTargets` 进入 `alwaysTargetsHigh`；`_help` 刷新为新文本
- `POST /ka/config` 合法 patch 立即生效并同步写回 `~/.minecraft/config/killaura.json`
- 非法 patch（数组类型错误、非法实体 ID、旧字段）返回 400 且文件不变；`range:99`、未知字段、坏 JSON 同样 400
- 僵尸在 `attackHostile=true` 时被选中；只开 `attackFriendlyDrops` 时目标是牛，只开
  `attackFriendlyNoDrops` 时目标是村民
- 档位排序逐项通过：high 羊 > 普通僵尸；普通僵尸 > medium 牛；medium 牛 > 普通猪；
  普通猪 > low 蝙蝠；low 蝙蝠 > 普通村民；普通村民 > lowest 狼；只有 lowest 狼时仍会攻击
- `denyTargets` 优先于 `alwaysTargetsHigh`；被拒绝的高档目标不会回落到普通档位
- 玩家/盔甲架未进 always 时按友善无掉落档排序：low 蝙蝠优先于普通盔甲架，没有蝙蝠时才选盔甲架
- 狼（中立）在三个开关都开时仍不被选中，加入 always 数组后可被选中
- 蜘蛛白天/亮处为 `target:null`，夜晚/暗处被 `attackHostile` 选中
- `sulfur_cube` 被 `attackHostile` 选中；`mannequin` 默认不被选中
- 测试结束已恢复默认配置、清空五个数组、关闭开关、清理测试生物；
  **`killaura-reimpl-1.3.0.jar` 保留安装在 `~/.minecraft/mods/`**

无头环境没有人工按键操作，但按键已经从代码中移除，实测通过 HTTP 完成全部控制。

## 结构

```
src/main/java/dev/dsh/killaura/
├── AuraClient.java      # ClientModInitializer：注册 tick 与 /ka endpoint
├── AuraEndpoint.java    # HTTP 路由、客户端线程投递、JSON 响应
├── AuraSnapshot.java    # 状态 JSON 数据模型
├── AuraEngine.java      # 每 tick 状态机：开关、瞄准、攻击、最近命中记录
├── TargetFilter.java    # 目标筛选、七档选择与档位内评分
├── TargetClassifier.java # 敌意/中立/友善有掉落/友善无掉落分类
├── TargetRules.java     # 拒绝/强制/普通过滤/分类开关的判定顺序
├── Angles.java          # yaw/pitch 与偏角计算
├── AuraSettings.java    # Gson 配置读写、迁移、夹紧与严格 patch 校验
└── Help.java            # GET /ka/ 的手册
docs/HARDCODED_DATA.md   # 硬编码分类/掉落数据的来源与复现方法
ka                       # curl 封装脚本
libs/httpdprovider-1.1.1.jar  # compileOnly，不打包
```

## 许可

- **代码**：LGPL-3.0-only，完整文本见 [`LICENSE`](LICENSE)（其中引用的 GPL-3.0 见
  [`LICENSE.GPL-3.0`](LICENSE.GPL-3.0)）；源码文件头部标有 `SPDX-License-Identifier: LGPL-3.0-only`。
- **文档**：CC BY-NC-SA 4.0，见 [`LICENSE.CC-BY-NC-SA-4.0`](LICENSE.CC-BY-NC-SA-4.0)。
- 行为来源与原模组的 MIT 许可、MGHttpdProvider 的 LGPL 许可详见 [`NOTICE.md`](NOTICE.md)。
