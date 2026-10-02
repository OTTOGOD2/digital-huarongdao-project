import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * PuzzleSolver 自测，不依赖 JUnit：
 *
 * <pre>
 * javac -encoding UTF-8 -d out src\*.java
 * java -cp out PuzzleSolverTest
 * </pre>
 *
 * 3x3 部分先对全状态空间（181440 个可达局面）做 BFS 得到真实最优步数，
 * 再抽样与 IDA* 结果逐一核对；4x4 部分验证解的有效性。
 */
public class PuzzleSolverTest {
    private static final long BUDGET = 200_000_000L;

    private int passed = 0;
    private final java.util.List<String> failures = new java.util.ArrayList<>();

    public static void main(String[] args) throws Exception {
        PuzzleSolverTest suite = new PuzzleSolverTest();
        suite.runAll();
        System.out.println("通过 " + suite.passed + " 项，失败 " + suite.failures.size() + " 项。");
        for (String failure : suite.failures) {
            System.out.println("失败: " + failure);
        }
        if (!suite.failures.isEmpty()) {
            System.exit(1);
        }
    }

    private void runAll() throws Exception {
        testSolvedReturnsEmpty();
        testTwoMoveScrambleIsOptimal();
        testBudgetExhaustion();
        testUnsolvableReturnsNull();
        testParityRule();
        test3x3MatchesBfs();
        test4x4SolutionsValid();
        testFastSolvedReturnsEmpty();
        testFast3x3SolutionsValid();
        testFastHandlesHard4x4();
    }

    private void testFastSolvedReturnsEmpty() {
        PuzzleBoard board = new PuzzleBoard(3);
        check(PuzzleSolver.solveFast(board, BUDGET).length == 0, "快速模式：已复原局面应返回空序列");
    }

    private void testSolvedReturnsEmpty() {
        PuzzleBoard board = new PuzzleBoard(3);
        check(PuzzleSolver.solve(board, BUDGET).length == 0, "已复原局面应返回空序列");
    }

    private void testTwoMoveScrambleIsOptimal() {
        PuzzleBoard board = new PuzzleBoard(3);
        board.moveEmpty(-1, 0);
        board.moveEmpty(0, -1);
        int[] moves = PuzzleSolver.solve(board, BUDGET);
        check(moves != null && moves.length == 2, "两步打乱应求得 2 步最优解");
        check(applySolution(board, moves), "最优解应用后应复原");
    }

    private void testBudgetExhaustion() {
        PuzzleBoard board = new PuzzleBoard(3);
        board.moveEmpty(-1, 0);
        board.moveEmpty(0, -1);
        check(PuzzleSolver.solve(board, 1) == null, "节点预算为 1 时应放弃并返回 null");
    }

    private void testUnsolvableReturnsNull() throws Exception {
        PuzzleBoard board = new PuzzleBoard(3);
        swapTiles(board, 2, 0, 2, 1);  // 交换 7/8 得到经典无解局面
        check(PuzzleSolver.solve(board, BUDGET) == null, "无解局面应返回 null");
    }

    private void testParityRule() {
        check(PuzzleSolver.isSolvable(new int[] { 1, 2, 3, 4, 5, 6, 7, 8, 0 }, 3), "3x3 复原态应有解");
        check(!PuzzleSolver.isSolvable(new int[] { 1, 2, 3, 4, 5, 6, 8, 7, 0 }, 3), "3x3 交换 7/8 应无解");
        check(PuzzleSolver.isSolvable(new int[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 0 }, 4),
                "4x4 复原态应有解");
        check(!PuzzleSolver.isSolvable(new int[] { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 15, 14, 0 }, 4),
                "4x4 交换 14/15 应无解");
    }

    /** 全状态 BFS 后抽样核对：IDA* 步数必须与真实最优完全一致。 */
    private void test3x3MatchesBfs() throws Exception {
        Map<String, Integer> optimal = bfsAll3x3();
        System.out.println("  BFS 完成，共 " + optimal.size() + " 个可达局面");
        int sampled = 0;
        int index = 0;
        for (Map.Entry<String, Integer> entry : optimal.entrySet()) {
            if (index++ % 3000 != 0) {
                continue;
            }
            sampled++;
            PuzzleBoard board = boardFromKey(entry.getKey());
            int[] moves = PuzzleSolver.solve(board, BUDGET);
            check(moves != null, "有解局面不应返回 null: " + entry.getKey());
            if (moves == null) {
                continue;
            }
            check(moves.length == entry.getValue(),
                    "步数应为最优 " + entry.getValue() + " 实得 " + moves.length + ": " + entry.getKey());
            check(applySolution(board, moves), "解应用后应复原: " + entry.getKey());
        }
        check(sampled >= 50, "抽样数量应不少于 50，实际 " + sampled);
    }

    private void test4x4SolutionsValid() {
        Random random = new Random(42);
        for (int round = 0; round < 5; round++) {
            PuzzleBoard board = new PuzzleBoard(4);
            randomWalk(board, 120, random);
            int[] moves = PuzzleSolver.solve(board, BUDGET);
            check(moves != null, "4x4 随机局面应在预算内求得解（第 " + round + " 轮）");
            if (moves == null) {
                continue;
            }
            check(moves.length <= 120, "4x4 解不应长于打乱步数（第 " + round + " 轮）");
            check(applySolution(board, moves), "4x4 解应用后应复原（第 " + round + " 轮）");
        }
    }

    /** 快速模式在 3x3 抽样局面上只校验解的有效性，不校验最优性。 */
    private void testFast3x3SolutionsValid() throws Exception {
        Map<String, Integer> optimal = bfsAll3x3();
        int sampled = 0;
        int index = 0;
        for (String stateKey : optimal.keySet()) {
            if (index++ % 7000 != 0) {
                continue;
            }
            sampled++;
            PuzzleBoard board = boardFromKey(stateKey);
            int[] moves = PuzzleSolver.solveFast(board, BUDGET);
            check(moves != null, "快速模式：有解局面不应返回 null: " + stateKey);
            if (moves == null) {
                continue;
            }
            check(applySolution(board, moves), "快速模式：解应用后应复原: " + stateKey);
        }
        check(sampled >= 20, "快速模式抽样数量应不少于 20，实际 " + sampled);
    }

    /** 快速模式必须在最优求解超预算的难局上仍能快速返回有效解。 */
    private void testFastHandlesHard4x4() {
        long worst = 0;
        for (long seed = 1; seed <= 5; seed++) {
            PuzzleBoard board = new PuzzleBoard(4);
            randomWalk(board, 400, new Random(seed));
            long start = System.currentTimeMillis();
            int[] moves = PuzzleSolver.solveFast(board, 20_000_000L);
            long ms = System.currentTimeMillis() - start;
            worst = Math.max(worst, ms);
            check(moves != null, "快速模式：4x4 难局 seed=" + seed + " 应在节点预算内求得解");
            if (moves == null) {
                continue;
            }
            check(applySolution(board, moves), "快速模式：4x4 难局 seed=" + seed + " 解应用后应复原");
        }
        System.out.println("  快速模式 5 个难局最大耗时 " + worst + "ms");
    }

    /** 从复原态出发的 3x3 全状态空间 BFS，返回每个局面到复原的最短步数。 */
    private static Map<String, Integer> bfsAll3x3() {
        int[] goal = { 1, 2, 3, 4, 5, 6, 7, 8, 0 };
        Map<String, Integer> dist = new HashMap<>();
        Deque<int[]> queue = new ArrayDeque<>();
        dist.put(key(goal), 0);
        queue.add(goal);
        while (!queue.isEmpty()) {
            int[] state = queue.poll();
            int d = dist.get(key(state));
            int blank = indexOf(state, 0);
            int[] neighbors = neighbors9(blank);
            for (int np : neighbors) {
                int[] next = state.clone();
                next[blank] = next[np];
                next[np] = 0;
                String k = key(next);
                if (!dist.containsKey(k)) {
                    dist.put(k, d + 1);
                    queue.add(next);
                }
            }
        }
        return dist;
    }

    private static int indexOf(int[] state, int value) {
        for (int i = 0; i < state.length; i++) {
            if (state[i] == value) {
                return i;
            }
        }
        return -1;
    }

    private static int[] neighbors9(int blank) {
        int r = blank / 3;
        int c = blank % 3;
        int[] result = new int[4];
        int k = 0;
        if (r > 0) result[k++] = blank - 3;
        if (r < 2) result[k++] = blank + 3;
        if (c > 0) result[k++] = blank - 1;
        if (c < 2) result[k++] = blank + 1;
        int[] exact = new int[k];
        System.arraycopy(result, 0, exact, 0, k);
        return exact;
    }

    private static String key(int[] state) {
        StringBuilder sb = new StringBuilder(state.length);
        for (int v : state) {
            sb.append((char) ('0' + v));
        }
        return sb.toString();
    }

    /** 由 9 位状态串构造 PuzzleBoard（反射写入 tiles 与空格坐标）。 */
    private static PuzzleBoard boardFromKey(String stateKey) throws Exception {
        PuzzleBoard board = new PuzzleBoard(3);
        int[] cells = new int[9];
        int blank = 0;
        for (int i = 0; i < 9; i++) {
            cells[i] = stateKey.charAt(i) - '0';
            if (cells[i] == 0) {
                blank = i;
            }
        }
        int[][] tiles = new int[3][3];
        for (int i = 0; i < 9; i++) {
            tiles[i / 3][i % 3] = cells[i];
        }
        setField(board, "tiles", tiles);
        setField(board, "emptyRow", blank / 3);
        setField(board, "emptyCol", blank % 3);
        return board;
    }

    private static void swapTiles(PuzzleBoard board, int r1, int c1, int r2, int c2) throws Exception {
        Field field = PuzzleBoard.class.getDeclaredField("tiles");
        field.setAccessible(true);
        int[][] tiles = (int[][]) field.get(board);
        int tmp = tiles[r1][c1];
        tiles[r1][c1] = tiles[r2][c2];
        tiles[r2][c2] = tmp;
    }

    private static void setField(PuzzleBoard board, String name, Object value) throws Exception {
        Field field = PuzzleBoard.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(board, value);
    }

    /** 依次点击解中的格子，返回最终是否复原（每步都必须合法）。 */
    private static boolean applySolution(PuzzleBoard board, int[] moves) {
        for (int pos : moves) {
            if (!board.moveTile(pos / board.getSize(), pos % board.getSize())) {
                return false;
            }
        }
        return board.isSolved();
    }

    private static void randomWalk(PuzzleBoard board, int steps, Random random) {
        int[][] dirs = { { -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 } };
        int lastDr = 0;
        int lastDc = 0;
        int done = 0;
        int guard = 0;
        while (done < steps && guard < steps * 10) {
            guard++;
            int[] dir = dirs[random.nextInt(4)];
            if (dir[0] == -lastDr && dir[1] == -lastDc) {
                continue;
            }
            if (board.moveEmpty(dir[0], dir[1])) {
                lastDr = dir[0];
                lastDc = dir[1];
                done++;
            }
        }
    }

    private void check(boolean condition, String message) {
        if (condition) {
            passed++;
        } else {
            failures.add(message);
        }
    }
}
