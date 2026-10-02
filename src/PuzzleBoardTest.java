import java.util.ArrayList;
import java.util.List;

/**
 * PuzzleBoard 纯逻辑自测，不依赖 JUnit：
 *
 * <pre>
 * javac -encoding UTF-8 -d out src\*.java
 * java -cp out PuzzleBoardTest
 * </pre>
 *
 * 全部通过时输出统计并以 0 退出；有失败则逐条列出并以 1 退出。
 */
public class PuzzleBoardTest {
    private final List<String> failures = new ArrayList<>();
    private int passed = 0;

    public static void main(String[] args) {
        PuzzleBoardTest suite = new PuzzleBoardTest();
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
        testNewBoardIsSolved();
        testSizeValidation();
        testCanMoveAdjacency();
        testMoveTileSwaps();
        testMoveEmptyBounds();
        testSolvabilityRule();
        testShuffleAlwaysSolvable();
    }

    private void testNewBoardIsSolved() {
        PuzzleBoard board = new PuzzleBoard(4);
        check(board.isSolved(), "初始局面应为复原状态");
        check(board.getEmptyRow() == 3 && board.getEmptyCol() == 3, "空格应在右下角");
        check(board.getTile(0, 0) == 1 && board.getTile(3, 2) == 15 && board.getTile(3, 3) == 0,
                "初始排布应为 1..15 后接 0");
    }

    private void testSizeValidation() {
        check(throwsForSize(2) && throwsForSize(9), "小于 3 或大于 8 应抛 IllegalArgumentException");
        check(!throwsForSize(3) && !throwsForSize(8), "3 和 8 应为合法尺寸");
    }

    private boolean throwsForSize(int size) {
        try {
            new PuzzleBoard(size);
            return false;
        } catch (IllegalArgumentException expected) {
            return true;
        }
    }

    private void testCanMoveAdjacency() {
        PuzzleBoard board = new PuzzleBoard(4);
        check(board.canMove(2, 3), "空格上方的格子应可移动");
        check(board.canMove(3, 2), "空格左侧的格子应可移动");
        check(!board.canMove(0, 0), "远离空格的格子不可移动");
        check(!board.canMove(-1, 0) && !board.canMove(0, 4), "越界坐标不可移动");
    }

    private void testMoveTileSwaps() {
        PuzzleBoard board = new PuzzleBoard(3);
        check(board.moveTile(1, 2), "与空格相邻的格子应可移动");
        check(board.getTile(2, 2) == 6 && board.getTile(1, 2) == 0, "移动后数字应落入原空格位");
        check(board.getEmptyRow() == 1 && board.getEmptyCol() == 2, "空格位置应更新");
        check(!board.moveTile(0, 0), "不相邻的格子应移动失败");
        check(board.getEmptyRow() == 1 && board.getEmptyCol() == 2, "失败的移动不应改变局面");
    }

    private void testMoveEmptyBounds() {
        PuzzleBoard board = new PuzzleBoard(3);
        check(!board.moveEmpty(1, 0) && !board.moveEmpty(0, 1), "朝边界外移动空格应失败");
        check(board.moveEmpty(-1, 0), "朝界内方向移动空格应成功");
    }

    private void testSolvabilityRule() {
        check(isSolvableByParity(sequence(1, 15), 4, 1), "4x4 复原态应判定有解");
        int[] swapped4 = sequence(1, 15);
        swapped4[13] = 15;
        swapped4[14] = 14;
        check(!isSolvableByParity(swapped4, 4, 1), "4x4 交换 14/15 后应判定无解");
        check(isSolvableByParity(sequence(1, 8), 3, 1), "3x3 复原态应判定有解");
        int[] swapped3 = sequence(1, 8);
        swapped3[6] = 8;
        swapped3[7] = 7;
        check(!isSolvableByParity(swapped3, 3, 1), "3x3 交换 7/8 后应判定无解");
    }

    private void testShuffleAlwaysSolvable() {
        for (int size = 3; size <= 8; size++) {
            PuzzleBoard board = new PuzzleBoard(size);
            for (int round = 0; round < 20; round++) {
                board.reset();
                board.shuffle(size * size * 25);
                check(!board.isSolved(), size + "x" + size + " 打乱后不应处于复原态");
                check(boardIsSolvable(board), size + "x" + size + " 打乱后应保持有解");
            }
        }
    }

    /** 逐格读取局面并按逆序奇偶性判断是否有解。 */
    private boolean boardIsSolvable(PuzzleBoard board) {
        int size = board.getSize();
        int[] values = new int[size * size - 1];
        int index = 0;
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                int tile = board.getTile(r, c);
                if (tile != 0) {
                    values[index++] = tile;
                }
            }
        }
        return isSolvableByParity(values, size, size - board.getEmptyRow());
    }

    /**
     * 逆序奇偶性判定：宽为奇数时逆序数须为偶；
     * 宽为偶数时（逆序数 + 空格所在行自底向上数）须为奇数。
     */
    private static boolean isSolvableByParity(int[] values, int width, int blankRowFromBottom) {
        long inversions = inversionCount(values);
        if (width % 2 == 1) {
            return inversions % 2 == 0;
        }
        return (inversions + blankRowFromBottom) % 2 == 1;
    }

    private static long inversionCount(int[] values) {
        long inversions = 0;
        for (int i = 0; i < values.length; i++) {
            for (int j = i + 1; j < values.length; j++) {
                if (values[i] > values[j]) {
                    inversions++;
                }
            }
        }
        return inversions;
    }

    private static int[] sequence(int from, int to) {
        int[] values = new int[to - from + 1];
        for (int i = 0; i < values.length; i++) {
            values[i] = from + i;
        }
        return values;
    }

    private void check(boolean condition, String message) {
        if (condition) {
            passed++;
        } else {
            failures.add(message);
        }
    }
}
