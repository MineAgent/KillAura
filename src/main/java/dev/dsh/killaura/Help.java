// SPDX-License-Identifier: LGPL-3.0-only
// Copyright (C) 2026 MineAgent

package dev.dsh.killaura;

/** The manual returned for {@code GET /ka/}. */
public final class Help {
    private Help() {
    }

    public static String text() {
        return """
                Kill Aura (reimpl) — 远程控制 (Fabric, Minecraft 26.2)
                ========================================================
                挂载地址: http://127.0.0.1:3420/ka
                必需模组: MGHttpdProvider
                本模组没有游戏内按键, 开关和配置全部走 HTTP。

                  GET  /ka/          返回本使用说明
                  GET  /ka/state     当前状态 (JSON)
                  POST /ka/on        开启 (已开启时幂等)
                  POST /ka/off       关闭 (已关闭时幂等)
                  POST /ka/toggle    切换开关
                  POST /ka/reload    重新读取 config/killaura.json
                  POST /ka/config    更新配置 (application/json, 部分字段)

                状态 JSON
                --------------------------------------------------------
                  {
                    "ok": true,
                    "enabled": true,
                    "inWorld": true,
                    "player": "DSH",
                    "config": { "range": 3.0, "rotation": "visible", ... },
                    "target": { "name": "Zombie", "id": 123, "distance": 2.31, "health": 20.0 },
                    "lastAttack": { "name": "Zombie", "agoMs": 120 }
                  }
                  target      当前会攻击的目标; 未开启/未进世界/附近没有目标时为 null
                  lastAttack  最近一次实际攻击; 本次启动还没打过时为 null

                配置 patch
                --------------------------------------------------------
                  请求体是 JSON 对象, 只改出现的字段:
                    { "range": 4.5, "rotation": "visible", "targetPlayers": false }
                  数字: range 0.5-6.0, fov 1-360, minAttackCharge 0-1
                  布尔: targetPlayers, targetHostile, targetPassive, targetArmorStands,
                        ignoreInvisible, throughWalls, pauseInScreens, pauseWhileUsingItem
                  字符串: rotation = silent|visible|none
                          priority = closest|lowestHealth|health|angle
                  全有或全无: 任一字段非法则整个请求返回 400, 文件不改。
                  _help 只读; 未知字段报错。

                示例
                  curl http://127.0.0.1:3420/ka/state
                  curl -X POST http://127.0.0.1:3420/ka/on
                  curl -X POST http://127.0.0.1:3420/ka/off
                  curl -X POST http://127.0.0.1:3420/ka/toggle
                  curl -X POST http://127.0.0.1:3420/ka/reload
                  curl -X POST -H 'Content-Type: application/json' \\
                       --data-binary '{"range":4.5}' \\
                       http://127.0.0.1:3420/ka/config
                  curl http://127.0.0.1:3420/           # 所有 endpoint

                返回
                  200  成功 (JSON 状态)
                  204  OPTIONS
                  400  JSON 语法错误 / 字段非法 / 请求体为空
                  404  路径不存在
                  405  方法不允许 (Allow: GET, HEAD, POST, OPTIONS)
                  409  客户端未就绪或正在关闭
                  413  请求体过大 (>16KB)
                  500  内部错误
                  504  客户端线程 5 秒内没有响应

                注意
                  - 只监听 127.0.0.1, 没有鉴权, 不要把这个端口暴露出去。
                  - 开关状态不持久化, 重启客户端后是关闭的。
                  - 开启时会重新读取一次 config/killaura.json; 运行中改文件后用 /ka/reload。
                  - pauseInScreens=true 时打开任意界面会暂停攻击; pauseWhileUsingItem=true
                    时使用物品会暂停。
                  - 控制只影响客户端, 不修改服务端规则; 多人服使用前请确认服务器允许。
                """;
    }
}
