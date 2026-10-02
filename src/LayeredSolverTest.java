import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.Random;

/**
 * LayeredSolver 自测，不依赖 JUnit：
 *
 * <pre>
 * javac -encoding UTF-8 -d out src\*.java
 * java -cp out LayeredSolverTest
 * </pre>
 *
 * 对 3~8 全尺寸的随机打乱局面求解并验证：每步必须合法、应用后必须复原。
 * 另验证已复原局面返回空序列。
 */
public class LayeredSolverTest {
    private int passed = 0;
    private final List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        LayeredSolverTest suite = new LayeredSolverTest();
        suite.runAll();
        System.out.println("通过 " + suite.passed + " 项，失败 " + suite.failures.size() + " 项。");
        for (String failure : suite.failures) {
            System.out.println("失败: " + failure);
        }
        if (!suite.failures.isEmpty()) {
            System.exit(1);
        }
    }

    private void runAll() {
        testSolvedReturnsEmpty();
        for (int size = 3; size <= 8; size++) {
            testSize(size);
        }
    }

    private void testSolvedReturnsEmpty() {
        PuzzleBoard board = new PuzzleBoard(5);
        check(LayeredSolver.solve(board).length == 0, "已复原局面应返回空序列");
    }

    private void testSize(int size) {
        long worst = 0;
        int totalSteps = 0;
        int rounds = 4;
        for (int round = 0; round < rounds; round++) {
            PuzzleBoard board = new PuzzleBoard(size);
            randomWalk(board, size * size * 25, new Random(100L * size + round));
            long start = System.currentTimeMillis();
            int[] clicks = LayeredSolver.solve(board);
            long ms = System.currentTimeMillis() - start;
            worst = Math.max(worst, ms);
            check(clicks != null, size + "x" + size + " 第 " + round + " 轮应返回解");
            if (clicks == null) {
                continue;
            }
            check(clicks.length > 0, size + "x" + size + " 第 " + round + " 轮解不应为空（打乱后不应是复原态）");
            check(apply(board, clicks), size + "x" + size + " 第 " + round + " 轮解应用后应复原");
            totalSteps += clicks.length;
        }
        System.out.println("  " + size + "x" + size + "：" + rounds + " 局，平均 "
                + totalSteps / rounds + " 步，最大耗时 " + worst + "ms");
    }

    /** 逐步点击执行解，任何一步不合法即失败。 */
    private boolean apply(PuzzleBoard board, int[] clicks) {
        for (int pos : clicks) {
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
