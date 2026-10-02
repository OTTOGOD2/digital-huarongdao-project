import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

/**
 * 分层归位求解器：像人类玩家一样逐圈把顶行与左列归位，
 * 每行/列的最后两块联合放置以保证空格始终留在未锁定区域，
 * 剩余 4x4 交给加权 IDA*（PuzzleSolver.solveCells）。
 *
 * 锁定块都是 row-major 前缀、不贡献逆序数，因此剩余 4x4 的局部可解性
 * 与全局可解性等价，全程无死锁、无需回溯。解不保证最短，但任何尺寸
 * （3~8）都在毫秒级完成，供大棋盘的提示与自动演示使用。
 */
public final class LayeredSolver {
    private static final long REST_NODES = 20_000_000L;

    private LayeredSolver() {
    }

    /**
     * 求解 board 当前局面。
     *
     * @return 依次要点击的格子下标（row-major）；已复原时返回空数组；理论无失败分支
     */
    public static int[] solve(PuzzleBoard board) {
        int n = board.getSize();
        int total = n * n;
        int[] cells = new int[total];
        for (int r = 0; r < n; r++) {
            for (int c = 0; c < n; c++) {
                cells[r * n + c] = board.getTile(r, c);
            }
        }
        if (isSolved(cells)) {
            return new int[0];
        }

        List<Integer> clicks = new ArrayList<>();
        boolean[] locked = new boolean[total];
        int blank = indexOf(cells, 0);

        int r = 0;
        while (n - r > 4) {
            // 顶行：前 n-r-2 块逐块归位
            for (int c = r; c < n - 2; c++) {
                blank = placeSingle(cells, n, locked, r * n + c + 1, r * n + c, blank, clicks);
                locked[r * n + c] = true;
            }
            // 顶行最后两块联合放置（保证空格不困在锁定格）
            blank = placePair(cells, n, locked,
                    r * n + n - 1, r * n + n - 2,
                    r * n + n, r * n + n - 1,
                    blank, clicks);
            locked[r * n + n - 2] = true;
            locked[r * n + n - 1] = true;

            // 左列：中间块逐块归位
            for (int r2 = r + 1; r2 < n - 2; r2++) {
                blank = placeSingle(cells, n, locked, r2 * n + r + 1, r2 * n + r, blank, clicks);
                locked[r2 * n + r] = true;
            }
            // 左列最后两块联合放置
            blank = placePair(cells, n, locked,
                    (n - 2) * n + r + 1, (n - 2) * n + r,
                    (n - 1) * n + r + 1, (n - 1) * n + r,
                    blank, clicks);
            locked[(n - 2) * n + r] = true;
            locked[(n - 1) * n + r] = true;
            r++;
        }

        // 剩余 4x4：抽取并重编号为局部 row-major 目标，交给加权 IDA*
        int m = n - r;
        int[] local = new int[m * m];
        for (int dr = 0; dr < m; dr++) {
            for (int dc = 0; dc < m; dc++) {
                int v = cells[(r + dr) * n + (r + dc)];
                if (v == 0) {
                    local[dr * m + dc] = 0;
                } else {
                    int g = v - 1;
                    local[dr * m + dc] = (g / n - r) * m + (g % n - r) + 1;
                }
            }
        }
        int[] localSolution = PuzzleSolver.solveCells(local, m, REST_NODES, 5);
        if (localSolution == null) {
            return null;  // 理论不发生：剩余 4x4 与全局可解性等价
        }
        for (int pos : localSolution) {
            clicks.add((r + pos / m) * n + (r + pos % m));
        }

        int[] result = new int[clicks.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = clicks.get(i);
        }
        return result;
    }

    /**
     * BFS 在未锁定格中把 tile 滑到 goal，点击序列追加到 out。
     * 状态 = (tile 位置, 空格位置)；目标只约束 tile 位置，空格必然停在未锁定格。
     *
     * @return 结束时的空格位置，搜索失败返回 -1（理论不发生）
     */
    private static int placeSingle(int[] cells, int n, boolean[] locked,
            int tile, int goal, int blank, List<Integer> out) {
        int total = n * n;
        int tilePos = indexOf(cells, tile);
        int[] parent = new int[total * total];
        int[] step = new int[total * total];
        Arrays.fill(parent, -1);
        int start = tilePos * total + blank;
        parent[start] = start;

        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(start);
        int[] dirs = { -n, n, -1, 1 };
        int finalState = -1;
        bfs:
        while (!queue.isEmpty()) {
            int state = queue.poll();
            int tp = state / total;
            int bp = state % total;
            for (int d : dirs) {
                int nb = bp + d;
                if (!isNeighbor(bp, nb, n) || !allow(locked, nb)) {
                    continue;
                }
                int ntp = nb == tp ? bp : tp;
                int newState = ntp * total + nb;
                if (parent[newState] != -1) {
                    continue;
                }
                parent[newState] = state;
                step[newState] = nb;
                if (ntp == goal) {
                    finalState = newState;
                    break bfs;
                }
                queue.add(newState);
            }
        }
        if (finalState < 0) {
            return -1;
        }
        return replay(cells, n, parent, step, start, finalState, out);
    }

    /**
     * 联合放置两块：BFS 状态 = (A 位置, B 位置, 空格位置)，目标为 A、B 同时就位。
     * 用于每行/列的最后两块，避免手工编排放置顺序。
     */
    private static int placePair(int[] cells, int n, boolean[] locked,
            int tileA, int goalA, int tileB, int goalB, int blank, List<Integer> out) {
        int total = n * n;
        int plane = total * total;
        int posA = indexOf(cells, tileA);
        int posB = indexOf(cells, tileB);
        int[] parent = new int[plane * total];
        int[] step = new int[plane * total];
        Arrays.fill(parent, -1);
        int start = (posA * total + posB) * total + blank;
        parent[start] = start;

        Deque<Integer> queue = new ArrayDeque<>();
        queue.add(start);
        int[] dirs = { -n, n, -1, 1 };
        int finalState = -1;
        bfs:
        while (!queue.isEmpty()) {
            int state = queue.poll();
            int pa = state / plane;
            int pb = state / total % total;
            int bp = state % total;
            for (int d : dirs) {
                int nb = bp + d;
                if (!isNeighbor(bp, nb, n) || !allow(locked, nb)) {
                    continue;
                }
                int npa = pa;
                int npb = pb;
                if (nb == pa) {
                    npa = bp;
                } else if (nb == pb) {
                    npb = bp;
                }
                int newState = (npa * total + npb) * total + nb;
                if (parent[newState] != -1) {
                    continue;
                }
                parent[newState] = state;
                step[newState] = nb;
                if (npa == goalA && npb == goalB) {
                    finalState = newState;
                    break bfs;
                }
                queue.add(newState);
            }
        }
        if (finalState < 0) {
            return -1;
        }
        return replay(cells, n, parent, step, start, finalState, out);
    }

    /** 从 BFS 终态回溯出点击序列，在 cells 副本上逐步执行，返回最终空格位置。 */
    private static int replay(int[] cells, int n, int[] parent, int[] step,
            int start, int finalState, List<Integer> out) {
        int total = n * n;
        int[] path = new int[64];
        int len = 0;
        for (int state = finalState; state != start; state = parent[state]) {
            if (len == path.length) {
                path = Arrays.copyOf(path, len * 2);
            }
            path[len++] = step[state];
        }
        int blank = start % total;
        for (int i = len - 1; i >= 0; i--) {
            int click = path[i];
            cells[blank] = cells[click];
            cells[click] = 0;
            blank = click;
            out.add(click);
        }
        return blank;
    }

    private static boolean allow(boolean[] locked, int pos) {
        return !locked[pos];
    }

    private static boolean isNeighbor(int from, int to, int n) {
        if (to < 0 || to >= n * n) {
            return false;
        }
        return Math.abs(from / n - to / n) + Math.abs(from % n - to % n) == 1;
    }

    private static int indexOf(int[] cells, int value) {
        for (int i = 0; i < cells.length; i++) {
            if (cells[i] == value) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isSolved(int[] cells) {
        for (int i = 0; i < cells.length - 1; i++) {
            if (cells[i] != i + 1) {
                return false;
            }
        }
        return cells[cells.length - 1] == 0;
    }
}
