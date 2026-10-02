import java.util.Random;

/**
 * 数字华容道棋盘：空白格用 0 表示，目标状态为 1..n^2-1 后接 0。
 */
public class PuzzleBoard {
    private final int size;
    private final int[][] tiles;
    private int emptyRow;
    private int emptyCol;

    public PuzzleBoard(int size) {
        if (size < 3 || size > 8) {
            throw new IllegalArgumentException("棋盘大小需在 3 到 8 之间");
        }
        this.size = size;
        this.tiles = new int[size][size];
        reset();
    }

    public int getSize() {
        return size;
    }

    public int getTile(int row, int col) {
        return tiles[row][col];
    }

    public int getEmptyRow() {
        return emptyRow;
    }

    public int getEmptyCol() {
        return emptyCol;
    }

    public void reset() {
        int value = 1;
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                tiles[r][c] = value++;
            }
        }
        tiles[size - 1][size - 1] = 0;
        emptyRow = size - 1;
        emptyCol = size - 1;
    }

    public boolean isSolved() {
        int expected = 1;
        for (int r = 0; r < size; r++) {
            for (int c = 0; c < size; c++) {
                if (r == size - 1 && c == size - 1) {
                    return tiles[r][c] == 0;
                }
                if (tiles[r][c] != expected++) {
                    return false;
                }
            }
        }
        return true;
    }

    public boolean canMove(int row, int col) {
        if (row < 0 || row >= size || col < 0 || col >= size) {
            return false;
        }
        return Math.abs(row - emptyRow) + Math.abs(col - emptyCol) == 1;
    }

    public boolean moveTile(int row, int col) {
        if (!canMove(row, col)) {
            return false;
        }
        tiles[emptyRow][emptyCol] = tiles[row][col];
        tiles[row][col] = 0;
        emptyRow = row;
        emptyCol = col;
        return true;
    }

    public boolean moveEmpty(int dRow, int dCol) {
        return moveTile(emptyRow + dRow, emptyCol + dCol);
    }

    /**
     * 通过随机合法滑动打乱，保证局面一定有解。
     */
    public void shuffle(int minMoves) {
        Random random = new Random();
        int moves = Math.max(minMoves, size * size * 20);
        int lastDr = 0;
        int lastDc = 0;
        int performed = 0;
        int guard = 0;

        while (performed < moves && guard < moves * 8) {
            guard++;
            int[][] dirs = {{ -1, 0 }, { 1, 0 }, { 0, -1 }, { 0, 1 }};
            int[] dir = dirs[random.nextInt(dirs.length)];
            int dr = dir[0];
            int dc = dir[1];
            if (dr == -lastDr && dc == -lastDc) {
                continue;
            }
            if (moveEmpty(dr, dc)) {
                lastDr = dr;
                lastDc = dc;
                performed++;
            }
        }

        if (isSolved()) {
            shuffle(minMoves);
        }
    }
}
