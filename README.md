# 数字华容道

用 Java Swing 实现的滑动数字拼图（n-puzzle）。

## 运行

需要已安装 JDK 8 或更高版本。

```bat
cd src
javac -encoding UTF-8 *.java
java DigitalHuarongdao
```

或在项目根目录：

```bat
javac -encoding UTF-8 -d out src\*.java
java -cp out DigitalHuarongdao
```

也可以直接双击 `run.bat`。

> 源码为 UTF-8 编码，`-encoding UTF-8` 不能省略，否则在默认 GBK 编码的 Windows 上编译，中文界面文字会乱码。

## 测试

```bat
javac -encoding UTF-8 -d out src\*.java
java -cp out PuzzleBoardTest
java -cp out RecordStoreTest
java -cp out PuzzleSolverTest
java -cp out LayeredSolverTest
```

全部通过时输出统计并以退出码 0 结束，存在失败时逐条列出并以退出码 1 结束。
`PuzzleSolverTest` 会对 3x3 全状态空间（181440 个局面）做 BFS，抽样核对 `PuzzleSolver.solve` 求得的解确实最优，并验证快速模式 `solveFast` 在最优求解超预算的难局上也能毫秒级返回有效解。
`LayeredSolverTest` 验证分层归位求解器在 3x3~8x8 全尺寸随机局面上都能给出可复原的解。

## 打包

执行 `build.bat`：编译 → 跑测试 → 打 jar → `jpackage` 生成自带运行时的独立程序（应用图标见 `assets/app.ico`）：

```
dist\DigitalHuarongdao\DigitalHuarongdao.exe
```

双击即可运行，无需目标机器安装 Java。打包机需要 JDK 14+（jpackage）；日常开发继续用 `run.bat` 即可。

## 玩法

- 把数字按从小到大排好，空白格在右下角即通关。
- 点击与空白格相邻的数字，或用方向键 / WASD 移动空白格。
- 可选 3x3 ~ 8x8 六档难度。
- `Z` 撤销上一步（局面与步数同步回退），`R` 重新开始。
- 处于正确位置的数字块带绿色描边，方便观察进度。
- `H` 提示一步（黄色高亮），"自动演示"按钮完整通关，`Esc` 或点击棋盘停止演示。
- 提示与演示所有尺寸（3x3~8x8）均可用：4x4 及以下用加权 IDA* 快速求解（路径接近最优）；5x5 及以上用分层归位求解器 `LayeredSolver`（模仿人类逐圈归位策略，毫秒级完成，路径较长但不保证最短，演示时会自动加速播放）。另提供严格最优的 `PuzzleSolver.solve`（供测试与分析）。
- 本局一旦使用提示或演示，成绩不计入纪录。
- 每个难度分别记录最少步数与最短用时，通关后自动保存到用户目录的 `.digital-huarongdao-records.properties`。
