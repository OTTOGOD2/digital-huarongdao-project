import java.util.Arrays;

/**
 * 数字华容道求解器：IDA*，启发值为曼哈顿距离 + 线性冲突。
 *
 * <ul>
 *   <li>{@link #solve} —— 最优解（启发权重 1），耗时可能较长；</li>
 *   <li>{@link #solveFast} —— 加权 IDA*（权重 5），毫秒级返回，
 *       解可能非最优但保证有效，供提示与自动演示使用。</li>
 * </ul>
 *
 * 返回需要依次点击的格子下标（row-major）序列。
 */
public final class PuzzleSolver {
    private static final long FOUND = -1L;
    private static final long INF = Long.MAX_VALUE;
    private static final int MAX_PATH = 256;
    private static final int FAST_WEIGHT = 5;

    private PuzzleSolver() {
    }

    /**
     * 求解 board 的当前局面，保证步数最优。
     *
     * @return 依次要点击的格子下标；已复原时返回空数组；
     *         无解或搜索节点超出 maxNodes 时返回 null。
     */
    public static int[] solve(PuzzleBoard board, long maxNodes) {
        return solveInternal(board, maxNodes, 1);
    }

    /**
     * 快速求解：加权启发（权重 {@value FAST_WEIGHT}），解可能非最优但通常快几个数量级。
     */
    public static int[] solveFast(PuzzleBoard board, long maxNodes) {
        return solveInternal(board, maxNodes, FAST_WEIGHT);
    }

    private static int[] solveInternal(PuzzleBoard board, long maxNodes, int weight) {
        int size = board.getSize();
        int[] cells = new int[size * size];
        int blank = 0;
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                int value = board.getTile(r, c);
                cells[r * size + c] = value;
                if (value == 0) {
                    blank = r * size + c;
                }
            }
        }
        if (!isSolvable(cells, size)) {
            return null;
        }
        return new Solver(cells, size, blank, maxNodes, weight).run();
    }

    /** 逆序奇偶性判定：奇数宽看逆数序，偶数宽再看空格所在行（自底向上数）。 */
    static boolean isSolvable(int[] cells, int size) {
        int inversions = 0;
        int blankRowFromBottom = 0;
        for (int i = 0; i < cells.length; i++) {
            if (cells[i] == 0) {
                blankRowFromBottom = size - i / size;
                continue;
            }
            for (int j = i + 1; j < cells.length; j++) {
                if (cells[j] != 0 && cells[i] > cells[j]) {
                    inversions++;
                }
            }
        }
        if (size % 2 == 1) {
            return inversions % 2 == 0;
        }
        return (inversions + blankRowFromBottom) % 2 == 1;
    }

    private static final class Solver {
        private final int size;
        private final int[] cells;
        private final long maxNodes;
        private final int weight;
        private final int[] rowConf;
        private final int[] colConf;
        private final int[] path = new int[MAX_PATH];
        private int blank;
        private long nodes;
        private boolean aborted;
        private int h;
        private int bound;
        private int pathLen;

        Solver(int[] cells, int size, int blank, long maxNodes, int weight) {
            this.cells = cells;
            this.size = size;
            this.blank = blank;
            this.maxNodes = maxNodes;
            this.weight = weight;
            this.rowConf = new int[size];
            this.colConf = new int[size];
        }

        int[] run() {
            if (isSolvedCells()) {
                return new int[0];
            }
            computeHeuristic();
            bound = h;
            // 加权模式下 f = g + weight*h 可达 MAX_PATH * weight，阈值循环上限随之放大
            while (bound < MAX_PATH * weight) {
                long t = dfs(0, -1);
                if (t == FOUND) {
                    return Arrays.copyOf(path, pathLen);
                }
                if (t == INF || aborted) {
                    return null;
                }
                bound = (int) t;
            }
            return null;
        }

        private boolean isSolvedCells() {
            for (int i = 0; i < cells.length - 1; i++) {
                if (cells[i] != i + 1) {
                    return false;
                }
            }
            return cells[cells.length - 1] == 0;
        }

        private void computeHeuristic() {
            int md = 0;
            for (int p = 0; p < cells.length; p++) {
                int v = cells[p];
                if (v == 0) {
                    continue;
                }
                int goal = v - 1;
                md += Math.abs(p / size - goal / size) + Math.abs(p % size - goal % size);
            }
            for (int r = 0; r < size; r++) {
                rowConf[r] = rowConflict(r);
            }
            for (int c = 0; c < size; c++) {
                colConf[c] = colConflict(c);
            }
            int lineSum = 0;
            for (int r = 0; r < size; r++) {
                lineSum += rowConf[r];
            }
            for (int c = 0; c < size; c++) {
                lineSum += colConf[c];
            }
            h = md + lineSum;
        }

        /**
         * 把 target 处的数字滑入空格（空格随之移到 target），
         * 增量更新曼哈顿距离与受影响行列的线性冲突。反向再 slide 一次即撤销。
         */
        private void slide(int target) {
            int from = target;
            int to = blank;
            int v = cells[target];
            int r1 = from / size;
            int c1 = from % size;
            int r2 = to / size;
            int c2 = to % size;
            int goal = v - 1;
            int mdDelta = (Math.abs(r2 - goal / size) + Math.abs(c2 - goal % size))
                    - (Math.abs(r1 - goal / size) + Math.abs(c1 - goal % size));

            cells[to] = v;
            cells[from] = 0;
            blank = from;

            int removed;
            int added;
            if (r1 == r2) {
                // 水平移动：影响两列与所在行（行内顺序变了）
                removed = rowConf[r1] + colConf[c1] + colConf[c2];
                rowConf[r1] = rowConflict(r1);
                colConf[c1] = colConflict(c1);
                colConf[c2] = colConflict(c2);
                added = rowConf[r1] + colConf[c1] + colConf[c2];
            } else {
                // 垂直移动：影响两行与所在列（列内顺序变了）
                removed = rowConf[r1] + rowConf[r2] + colConf[c1];
                rowConf[r1] = rowConflict(r1);
                rowConf[r2] = rowConflict(r2);
                colConf[c1] = colConflict(c1);
                added = rowConf[r1] + rowConf[r2] + colConf[c1];
            }
            h += added - removed + mdDelta;
        }

        private long dfs(int g, int prevBlank) {
            if (h == 0) {
                return FOUND;
            }
            long f = g + (long) weight * h;
            if (f > bound) {
                return f;
            }
            if (++nodes > maxNodes) {
                aborted = true;
                return INF;
            }
            long min = INF;
            int oldBlank = blank;
            for (int dir = 0; dir < 4; dir++) {
                int np = neighbor(oldBlank, dir);
                if (np < 0 || np == prevBlank || pathLen == MAX_PATH) {
                    continue;
                }
                slide(np);
                path[pathLen++] = np;
                long t = dfs(g + 1, oldBlank);
                if (t == FOUND) {
                    return FOUND;  // 命中目标：保留路径直接层层返回，不再弹出与撤销
                }
                pathLen--;
                slide(oldBlank);
                if (aborted) {
                    return INF;
                }
                if (t < min) {
                    min = t;
                }
            }
            return min;
        }

        private int neighbor(int pos, int dir) {
            switch (dir) {
                case 0: return pos >= size ? pos - size : -1;
                case 1: return pos < cells.length - size ? pos + size : -1;
                case 2: return pos % size > 0 ? pos - 1 : -1;
                default: return pos % size < size - 1 ? pos + 1 : -1;
            }
        }

        /** 行线性冲突：处于目标行的数字中，为恢复顺序需移出该行的最少个数 × 2。 */
        private int rowConflict(int r) {
            int k = 0;
            int[] goalCols = new int[size];
            for (int c = 0; c < size; c++) {
                int v = cells[r * size + c];
                if (v != 0 && (v - 1) / size == r) {
                    goalCols[k++] = (v - 1) % size;
                }
            }
            return 2 * (k - longestIncreasing(goalCols, k));
        }

        /** 列线性冲突：处于目标列的数字中，为恢复顺序需移出该列的最少个数 × 2。 */
        private int colConflict(int c) {
            int k = 0;
            int[] goalRows = new int[size];
            for (int r = 0; r < size; r++) {
                int v = cells[r * size + c];
                if (v != 0 && (v - 1) % size == c) {
                    goalRows[k++] = (v - 1) / size;
                }
            }
            return 2 * (k - longestIncreasing(goalRows, k));
        }

        private static int longestIncreasing(int[] values, int k) {
            int best = 0;
            int[] lis = new int[k];
            for (int i = 0; i < k; i++) {
                lis[i] = 1;
                for (int j = 0; j < i; j++) {
                    if (values[j] < values[i] && lis[j] + 1 > lis[i]) {
                        lis[i] = lis[j] + 1;
                    }
                }
                if (lis[i] > best) {
                    best = lis[i];
                }
            }
            return best;
        }
    }
}
