# 硬编码数据来源说明

本文记录 Kill Aura 代码中所有会影响目标选择/攻击行为的硬编码数据，以及它们的来源和推导方式。目标版本固定为 **Minecraft 26.2**；升级游戏版本时必须重新核对本文。

约定：

- **数据推导**：从 26.2 客户端 jar 的类/资源中取得，可复现。
- **设计常量**：为了让模组行为可控而人为选定的值，不是游戏数据。
- **用户规则**：由需求直接指定的优先级/顺序。

## 0. 数据环境

| 项 | 值 |
|---|---|
| 客户端 jar | `~/.gradle/caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-deobf/26.2/minecraft-merged-deobf-26.2.jar` |
| SHA-256 | `eaea8a3b5e1b7168cfd070216180480b21d3ea5d7e5e1832471e450ca56629f9` |
| 实体战利品表数量 | 109 个 `data/minecraft/loot_table/entities/*.json` |
| 提取日期 | 2026-10-29 |
| 代码位置 | `src/main/java/dev/dsh/killaura/TargetClassifier.java` |

`minecraft-merged-deobf-26.2.jar` 是 Loom 使用的合并/去混淆客户端+服务端 jar；类名和资源名与 26.2 官方名一致。

## 1. 目标分类硬编码

### 1.1 `Enemy` / `NeutralMob` 回退

```java
if (neutralMob || CONDITIONAL.contains(id) || FORCED_NEUTRAL.contains(id)) {
    return NEUTRAL;
}
if (enemy) {
    return HOSTILE;
}
return FRIENDLY_NO_DROPS.contains(id) ? FRIENDLY_NO_DROPS : FRIENDLY_DROPS;
```

- `Enemy`：`net.minecraft.world.entity.monster.Enemy`，原版敌对生物标记接口。
- `NeutralMob`：`net.minecraft.world.entity.NeutralMob`，可被激怒/有持续愤怒状态的生物标记接口。
- 客户端拿不到服务端 AI 的当前目标（`Mob#getTarget` 不保证同步），所以这里不判断“此刻正在打谁”，只判断类型/光照。

检查方式：

```bash
JAR=$(find ~/.gradle/caches/fabric-loom/minecraftMaven -name 'minecraft-merged-deobf-26.2.jar' | head -1)
javap -classpath "$JAR" net.minecraft.world.entity.monster.Monster
javap -classpath "$JAR" net.minecraft.world.entity.animal.wolf.Wolf
```

### 1.2 `CONDITIONAL`：条件敌对，归中立

| ID | 26.2 依据 |
|---|---|
| `minecraft:piglin` | `Piglin extends AbstractPiglin extends Monster`，实现 `Enemy`，但没有 `NeutralMob`；只有玩家没穿金甲/打开箱子等条件下才会攻击，因此不能按“始终敌意”处理。 |
| `minecraft:llama` | `Llama` 不实现 `Enemy` / `NeutralMob`，但有 `Llama$LlamaHurtByTargetGoal`，被攻击后会反击并吐口水。 |
| `minecraft:trader_llama` | 继承 `Llama`，同上。 |
| `minecraft:panda` | `Panda` 不实现 `Enemy` / `NeutralMob`，但有 `Panda$PandaHurtByTargetGoal`，被攻击后会反击。 |
| `minecraft:goat` | `Goat` 不实现 `Enemy` / `NeutralMob`，但存在冲撞（ram）行为，可能伤害玩家。 |
| `minecraft:pufferfish` | `Pufferfish` 不实现 `Enemy` / `NeutralMob`，但接触会造成伤害。 |

验证命令：

```bash
javap -classpath "$JAR" net.minecraft.world.entity.monster.piglin.Piglin
javap -classpath "$JAR" net.minecraft.world.entity.animal.equine.Llama
javap -classpath "$JAR" -p -c net.minecraft.world.entity.animal.panda.Panda | grep -A5 PandaHurtByTargetGoal
```

### 1.3 `FORCED_HOSTILE`：没有 `Enemy` 但确认为敌意

| ID | 依据 |
|---|---|
| `minecraft:zombie_nautilus` | `ZombieNautilus extends AbstractNautilus`，不实现 `Enemy` / `NeutralMob`；但 26.2 的 brain 常量池包含 `ANGRY_AT`、`ATTACK_TARGET_COOLDOWN`、`SensorType.NEAREST_PLAYERS`，因此按敌意处理；未单独做目标选中测试。 |
| `minecraft:sulfur_cube` | `SulfurCube extends AbstractCubeMob`（包名 `monster.cubemob`），不实现 `Enemy`；`AbstractCubeMob` 自带攻击 goal，游戏内实测在 `attackHostile=true` 时被选中。 |

验证命令：

```bash
javap -classpath "$JAR" net.minecraft.world.entity.animal.nautilus.ZombieNautilus
javap -classpath "$JAR" -p -v net.minecraft.world.entity.animal.nautilus.ZombieNautilus \
  | grep -E 'ANGRY_AT|ATTACK_TARGET_COOLDOWN|NEAREST_PLAYERS'
javap -classpath "$JAR" net.minecraft.world.entity.monster.cubemob.SulfurCube
javap -classpath "$JAR" net.minecraft.world.entity.monster.cubemob.AbstractCubeMob
```

### 1.4 `FORCED_NEUTRAL`

| ID | 依据 |
|---|---|
| `minecraft:mannequin` | `Mannequin extends Avatar`，是装饰性活体，不是可战斗 Mob；游戏内实测三个攻击开关全开时 `target` 仍为 `null`。 |

### 1.5 `LIGHT_DEPENDENT` 与光照阈值

```java
private static final Set<String> LIGHT_DEPENDENT = Set.of(
        "minecraft:spider",
        "minecraft:cave_spider");
...
entity.getLightLevelDependentMagicValue() >= 0.5F
```

来源是原版 `Spider$SpiderTargetGoal.canUse()`：它先取 `mob.getLightLevelDependentMagicValue()`，`>= 0.5F` 直接返回 `false`，否则才走 `NearestAttackableTargetGoal`。`CaveSpider` 没有重写目标逻辑，继承同一判断。

验证命令：

```bash
javap -classpath "$JAR" -p -c 'net.minecraft.world.entity.monster.spider.Spider$SpiderTargetGoal'
```

输出中的关键部分：

```text
getLightLevelDependentMagicValue:()F
ldc 0.5f
fcmpl
iflt ...
iconst_0
ireturn
```

因此：

- 亮处（`>= 0.5`）→ `NEUTRAL`，普通攻击开关不打；
- 暗处（`< 0.5`）→ `HOSTILE`；
- 白天被玩家攻击仍会通过 `HurtByTargetGoal` 反击，但这不改变上面的分类。

### 1.6 未知/模组实体

没有硬编码的 ID 使用以下回退：

- 实现 `Enemy` → `HOSTILE`
- 实现 `NeutralMob` → `NEUTRAL`
- 其他 → `FRIENDLY_DROPS`（保守地视为“可能有掉落”）

## 2. 友善无掉落集合

### 2.1 提取方法

从 26.2 客户端 jar 读取所有实体战利品表，`pools` 为空即默认没有任何掉落：

```python
import json, zipfile, sys

jar = sys.argv[1]
with zipfile.ZipFile(jar) as z:
    names = sorted(
        n for n in z.namelist()
        if n.startswith("data/minecraft/loot_table/entities/") and n.endswith(".json")
    )
    for name in names:
        data = json.loads(z.read(name))
        if not data.get("pools"):
            print(name.rsplit("/", 1)[-1][:-5])
```

结果：109 个表中有 30 个空表。再按分类规则筛选：只有 **友善** 且空表的 13 个 ID 进入 `FRIENDLY_NO_DROPS`：

| 硬编码 ID | 说明 |
|---|---|
| `minecraft:allay` | 悦灵 |
| `minecraft:armadillo` | 犰狳 |
| `minecraft:axolotl` | 美西螈 |
| `minecraft:bat` | 蝙蝠 |
| `minecraft:camel` | 骆驼 |
| `minecraft:fox` | 狐狸 |
| `minecraft:frog` | 青蛙 |
| `minecraft:happy_ghast` | 快乐恶魂 |
| `minecraft:ocelot` | 豹猫 |
| `minecraft:sniffer` | 嗅探兽 |
| `minecraft:tadpole` | 蝌蚪 |
| `minecraft:villager` | 村民 |
| `minecraft:wandering_trader` | 流浪商人 |

其余 17 个空表不进入该集合：

| 分组 | ID | 不进入的原因 |
|---|---|---|
| 中立 | `bee`、`goat`、`piglin`、`wolf` | 不属于友善分类，保留在 `NEUTRAL` |
| 敌意 | `creaking`、`ender_dragon`、`endermite`、`giant`、`illusioner`、`piglin_brute`、`silverfish`、`sulfur_cube`、`vex`、`wither` | 不属于友善分类，保留在 `HOSTILE` |
| 特殊 | `armor_stand`、`mannequin`、`player` | 由 `targetArmorStands` / `targetPlayers` / 中立特殊规则处理 |

### 2.2 口径

- “有掉落”指实体默认战利品表 `pools` 非空，不保证每次击杀都掉落。
- 不统计玩家给坐骑/宠物穿戴的鞍、马铠、狼铠、挽具等装备掉落。
- 没有硬编码“有掉落”列表；不在 `FRIENDLY_NO_DROPS` 里的友善实体一律归 `FRIENDLY_DROPS`。

## 3. 配置默认值与范围

| 常量 | 值 | 来源 |
|---|---|---|
| `DEFAULT_RANGE` | `3.0` | 原版默认攻击距离（3 格） |
| `MIN_RANGE` / `MAX_RANGE` | `0.5` / `6.0` | 项目限制，避免误配置成过大/过小距离 |
| `DEFAULT_FOV` | `360.0` | 默认不限制角度 |
| `MIN_FOV` / `MAX_FOV` | `1.0` / `360.0` | 圆锥角合法范围；360 = 全周 |
| `DEFAULT_CHARGE` | `0.0` | 原版攻击蓄力范围是 0–1；0 = 尽可能快 |
| `attackHostile` | `true` | 保持旧默认行为 |
| `attackFriendlyDrops` / `attackFriendlyNoDrops` | `false` | 保持旧默认不攻击友善生物 |
| `targetPlayers` | `true` | 旧默认行为 |
| `targetArmorStands` | `false` | 旧默认行为 |
| `ignoreInvisible` / `throughWalls` | `false` | 旧默认行为 |
| `pauseInScreens` / `pauseWhileUsingItem` | `true` | 避免在界面/使用物品时误攻击 |
| `rotation` | `"visible"` | 旧默认行为 |
| `priority` | `"closest"` | 旧默认行为 |

实体 ID 规范化规则：去空白、转小写、无命名空间时补 `minecraft:`、用 `Identifier.tryParse` 校验语法。该规则来自 Minecraft 的 `ResourceLocation` / 26.2 的 `Identifier` 约定，不校验实体是否已注册。

## 4. 评分与空间常量

| 常量 | 位置 | 含义/来源 |
|---|---|---|
| `settings.range + 2.0` | `TargetFilter` 搜索包围盒 | 设计余量：先粗筛比 `range` 大 2 格的 AABB，再把候选按精确的“眼睛到碰撞箱最近点”距离过滤；2 格用于覆盖大型碰撞箱，属于设计值，不是游戏数据 |
| `health * 1000.0 + gap` | `TargetFilter` 评分 | 让血量主键压过距离；距离上限远小于 1000，因此血量差 1 就优先于任何距离差 |
| `offAxis * 1000.0 + gap` | `TargetFilter` 评分 | 让偏角主键压过距离，按偏角排序 |
| `settings.fov / 2.0` | `TargetFilter` | `fov` 是完整圆锥角，实际比较的是半角 |
| `-90.0F..90.0F` | `Angles.lookAt` | Minecraft pitch 的合法范围 |
| `1.0E-6` | `Angles.offAxisDegrees` | 防止目标与眼睛重合时除零 |
| `180.0` / `360.0` | `Angles.wrapDegrees` | 角度归一化到 `[-180, 180)` |

## 5. 用户指定的规则

以下不是从游戏数据推导，而是需求指定：

- 是否攻击的检查顺序：
  `denyTargets` > 任一 `alwaysTargets*` > `targetPlayers` / `targetArmorStands` / `ignoreInvisible` / `throughWalls` > 三个攻击开关。
- 多个合法目标的选择顺序：
  `alwaysTargetsHigh` > 普通敌意 > `alwaysTargetsMedium` > 普通友善有掉落 > `alwaysTargetsLow` > 普通友善无掉落 > `alwaysTargetsLowest`。
- “普通 xxx”指不在任一 `alwaysTargets*` 数组中的目标。
- 玩家/盔甲架未进 always 数组时按 `FRIENDLY_NO_DROPS` 档位排序。
- 同一 ID 出现在多个 always 数组时最高档生效。

## 6. HTTP/其他常量

| 常量 | 值 | 来源 |
|---|---|---|
| `MAX_BODY_BYTES` | `16 * 1024` | 防止异常大的配置 patch，设计值 |
| `CLIENT_TIMEOUT_MS` | `5000` | 客户端线程 5 秒未响应即 504，设计值 |
| 状态码 200/204/400/404/405/409/413/500/504 | — | HTTP 语义 + 模组约定 |
| `range`/`fov` 上下界 | 见上表 | 项目限制 |
| 实体战利品表总数 | 109 | 26.2 客户端 jar 实际计数 |

## 7. 26.2 升级时的重新核对流程

1. 确认新版本客户端 jar 路径和 SHA-256。
2. 重新运行第 2.1 节脚本，得到新的空战利品表集合。
3. 用 `javap` 检查新增实体的 `Enemy` / `NeutralMob` 实现和 AI goal。
4. 在游戏内用 `/summon` 验证新实体的实际归属（尤其是新 mob）。
5. 更新 `TargetClassifier` 的四个集合和本文档。
6. 运行 `./gradlew test build`，再做一次无头游戏验收。

## 8. 相关代码

- [TargetClassifier.java](../src/main/java/dev/dsh/killaura/TargetClassifier.java)
- [TargetRules.java](../src/main/java/dev/dsh/killaura/TargetRules.java)
- [TargetFilter.java](../src/main/java/dev/dsh/killaura/TargetFilter.java)
- [AuraSettings.java](../src/main/java/dev/dsh/killaura/AuraSettings.java)
- [Angles.java](../src/main/java/dev/dsh/killaura/Angles.java)
