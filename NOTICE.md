# NOTICE — 许可与来源

Kill Aura (Reimplementation) 的**代码**按 **LGPL-3.0-only** 分发，**文档**按
**CC BY-NC-SA 4.0** 分发。源码文件头部标有 `SPDX-License-Identifier: LGPL-3.0-only`。

| 内容 | 许可 | 文件 |
| --- | --- | --- |
| Java 源码、`ka` 脚本、构建脚本 | LGPL-3.0-only | [`LICENSE`](LICENSE)、[`LICENSE.GPL-3.0`](LICENSE.GPL-3.0) |
| `README.md`、`NOTICE.md`、`Help.java` 中的文档文本 | CC BY-NC-SA 4.0 | [`LICENSE.CC-BY-NC-SA-4.0`](LICENSE.CC-BY-NC-SA-4.0) |

## 行为来源：killaura 1.0.0（行为改编，未打包）

本项目是 **Kill Aura 1.0.0**（作者 **pokato**）在 Minecraft 26.2 上的行为兼容重实现。

- 原始产物：`killaura-fabric-26.1-26.3-1.0.0.jar`
- SHA-256：`330bb2e070c06109707c825a215a60edf0723c5b60c920368a6685bc93dc9b2b`
- 原始许可：MIT（`fabric.mod.json` 声明，jar 内附 `LICENSE_killaura`）

本仓库没有获取到原作者源码，Java 代码是按 26.2 的官方 API 独立编写的；配置字段、目标筛选规则、
评分公式与用户可见文案刻意与原模组保持一致。原模组为 MIT 许可，其许可文本复制如下。

```
MIT License

Copyright (c) 2026 pokato

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## 依赖

- **MGHttpdProvider**（`libs/httpdprovider-1.1.1.jar`，仅 `compileOnly`，不打进产物）：
  Copyright © MineAgent，LGPL-3.0-only。运行时由 Fabric Loader 从 `.minecraft/mods/` 加载。
- **Fabric API**（`fabric-lifecycle-events-v1`）：Copyright © FabricMC，Apache-2.0。不随本模组打包。
