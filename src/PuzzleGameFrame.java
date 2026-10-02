import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayDeque;
import java.util.Deque;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * 数字华容道主窗口：点击相邻格子或方向键滑动，
 * 支持撤销、最佳成绩、滑动动画与 IDA* 求解器（提示 / 自动演示）。
 */
public class PuzzleGameFrame extends JFrame {
    private static final Color BG = new Color(18, 24, 38);
    private static final Color PANEL = new Color(28, 36, 54);
    private static final Color TILE = new Color(86, 171, 228);
    private static final Color TILE_ACCENT = new Color(120, 198, 247);
    private static final Color TEXT = new Color(240, 246, 255);
    private static final Color DIM = new Color(170, 186, 210);
    private static final Color SOLVED = new Color(72, 180, 132);
    private static final Color HINT_TILE = new Color(255, 214, 10);
    private static final String FONT_FAMILY = "Microsoft YaHei UI";
    private static final String DEFAULT_HINT = "点击 / 方向键移动 · Z 撤销 · H 提示 · R 重开 · Esc 停止演示";
    private static final int DEMO_INTERVAL_MS = 130;
    private static final long SOLVE_NODE_BUDGET = 20_000_000L;

    private final RecordStore recordStore = new RecordStore();
    private PuzzleBoard board;
    private final BoardPanel boardPanel;
    private final JLabel movesLabel;
    private final JLabel timeLabel;
    private final JLabel recordLabel;
    private final JComboBox<String> sizeBox;
    private final Timer clock;
    private final Timer demoTimer;
    private final Timer statusTimer;
    private final JButton demoButton;
    private final JLabel hintLabel;

    private final Deque<int[]> history = new ArrayDeque<>();
    private int moves;
    private int elapsedSeconds;
    private boolean playing;
    private boolean won;
    private boolean newRecord;
    private boolean usedSolver;
    private boolean solving;
    private boolean demoRunning;
    private int hintPos = -1;
    private int[] solution;
    private int demoIndex;
    private int solveGeneration;

    public PuzzleGameFrame() {
        super("数字华容道");
        board = new PuzzleBoard(4);

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(520, 640));
        getContentPane().setBackground(BG);
        setLayout(new BorderLayout(12, 12));
        ((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        JPanel top = new JPanel(new BorderLayout(8, 8));
        top.setOpaque(false);

        JLabel title = new JLabel("数字华容道", SwingConstants.LEFT);
        title.setForeground(TEXT);
        title.setFont(font(Font.BOLD, 26));
        top.add(title, BorderLayout.WEST);

        JPanel stats = new JPanel();
        stats.setOpaque(false);
        movesLabel = metric("步数 0");
        timeLabel = metric("用时 00:00");
        recordLabel = metric("纪录 --");
        stats.add(movesLabel);
        stats.add(timeLabel);
        stats.add(recordLabel);
        top.add(stats, BorderLayout.EAST);
        add(top, BorderLayout.NORTH);

        boardPanel = new BoardPanel();
        add(boardPanel, BorderLayout.CENTER);

        JPanel bottom = new JPanel();
        bottom.setOpaque(false);

        sizeBox = new JComboBox<>(new String[] {
                "3 x 3 简单", "4 x 4 经典", "5 x 5 困难", "6 x 6 大师", "7 x 7 宗师", "8 x 8 地狱" });
        sizeBox.setSelectedIndex(1);
        sizeBox.setFont(font(Font.PLAIN, 14));
        sizeBox.addActionListener(e -> startNewGame());

        JButton shuffleButton = actionButton("重新开始");
        shuffleButton.addActionListener(e -> startNewGame());

        demoButton = actionButton("自动演示");
        demoButton.addActionListener(e -> toggleAutoSolve());

        hintLabel = new JLabel(DEFAULT_HINT);
        hintLabel.setForeground(DIM);
        hintLabel.setFont(font(Font.PLAIN, 13));

        bottom.add(sizeBox);
        bottom.add(shuffleButton);
        bottom.add(demoButton);
        bottom.add(hintLabel);
        add(bottom, BorderLayout.SOUTH);

        clock = new Timer(1000, e -> {
            if (playing && !won) {
                elapsedSeconds++;
                refreshStats();
            }
        });
        clock.start();

        demoTimer = new Timer(DEMO_INTERVAL_MS, e -> demoTick());
        statusTimer = new Timer(2500, e -> hintLabel.setText(DEFAULT_HINT));
        statusTimer.setRepeats(false);

        bindKeys();
        startNewGame();
        pack();
        setLocationRelativeTo(null);
    }

    private static Font font(int style, int size) {
        return new Font(FONT_FAMILY, style, size);
    }

    private JLabel metric(String text) {
        JLabel label = new JLabel(text);
        label.setOpaque(true);
        label.setBackground(PANEL);
        label.setForeground(TEXT);
        label.setFont(font(Font.BOLD, 14));
        label.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        return label;
    }

    private JButton actionButton(String text) {
        JButton button = new JButton(text);
        button.setFont(font(Font.BOLD, 14));
        button.setFocusPainted(false);
        button.setBackground(new Color(47, 125, 196));
        button.setForeground(Color.WHITE);
        return button;
    }

    private int selectedSize() {
        return sizeBox.getSelectedIndex() + 3;
    }

    private void startNewGame() {
        solveGeneration++;
        stopDemo();
        board = new PuzzleBoard(selectedSize());
        board.shuffle(selectedSize() * selectedSize() * 25);
        history.clear();
        moves = 0;
        elapsedSeconds = 0;
        playing = true;
        won = false;
        newRecord = false;
        usedSolver = false;
        solving = false;  // 后台线程若仍在跑，靠 generation 失配丢弃结果
        hintPos = -1;
        boardPanel.stopSlide();
        if (!clock.isRunning()) {
            clock.start();
        }
        refreshStats();
        boardPanel.repaint();
        boardPanel.requestFocusInWindow();
    }

    private void refreshStats() {
        movesLabel.setText("步数 " + moves);
        timeLabel.setText("用时 " + formatTime(elapsedSeconds));
        Integer bestMoves = recordStore.bestMoves(board.getSize());
        Integer bestSeconds = recordStore.bestSeconds(board.getSize());
        if (bestMoves == null || bestSeconds == null) {
            recordLabel.setText("纪录 --");
        } else {
            recordLabel.setText("纪录 " + bestMoves + "步 " + formatTime(bestSeconds));
        }
    }

    private String formatTime(int seconds) {
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    private boolean inputLocked() {
        return !playing || won || solving || demoRunning;
    }

    private void tryMove(int row, int col) {
        if (inputLocked()) {
            return;
        }
        int toRow = board.getEmptyRow();
        int toCol = board.getEmptyCol();
        if (board.moveTile(row, col)) {
            registerMove(row, col, toRow, toCol);
        }
    }

    private void tryMoveEmpty(int dRow, int dCol) {
        if (inputLocked()) {
            return;
        }
        int fromRow = board.getEmptyRow() + dRow;
        int fromCol = board.getEmptyCol() + dCol;
        int toRow = board.getEmptyRow();
        int toCol = board.getEmptyCol();
        if (board.moveEmpty(dRow, dCol)) {
            registerMove(fromRow, fromCol, toRow, toCol);
        }
    }

    /** 记一步：入撤销栈、计数、播放滑动动画并检查胜利。 */
    private void registerMove(int fromRow, int fromCol, int toRow, int toCol) {
        int value = board.getTile(toRow, toCol);
        history.push(new int[] { toRow, toCol });
        moves++;
        hintPos = -1;
        refreshStats();
        boardPanel.slideTile(fromRow, fromCol, toRow, toCol, value);
        checkWin();
    }

    /** 撤销上一步：局面与步数同步回退到该步之前。 */
    private void undo() {
        if (inputLocked()) {
            return;
        }
        int[] tileAt = history.pop();
        int toRow = board.getEmptyRow();
        int toCol = board.getEmptyCol();
        int value = board.getTile(tileAt[0], tileAt[1]);
        if (board.moveTile(tileAt[0], tileAt[1])) {
            moves--;
            refreshStats();
            boardPanel.slideTile(tileAt[0], tileAt[1], toRow, toCol, value);
        }
    }

    private void checkWin() {
        if (!board.isSolved()) {
            return;
        }
        won = true;
        playing = false;
        clock.stop();
        stopDemo();
        newRecord = false;
        if (!usedSolver) {
            newRecord = recordStore.submit(board.getSize(), moves, elapsedSeconds);
        }
        refreshStats();
        boardPanel.repaint();
    }

    /** H：后台求最优解，把第一步的格子高亮提示。 */
    private void requestHint() {
        if (inputLocked()) {
            return;
        }
        if (board.getSize() > 4) {
            setStatus("提示目前支持到 4 x 4");
            return;
        }
        solving = true;
        setStatus("思考中…");
        PuzzleBoard snapshot = board;
        int generation = solveGeneration;
        startSolverWorker(snapshot, generation, result -> {
            solving = false;
            if (generation != solveGeneration || won || result == null || result.length == 0) {
                if (result == null && generation == solveGeneration && !won) {
                    setStatus("提示失败：超出搜索预算");
                }
                return;
            }
            usedSolver = true;
            hintPos = result[0];
            boardPanel.repaint();
        });
    }

    /** 自动演示按钮：再点一次或按 Esc 停止。 */
    private void toggleAutoSolve() {
        if (demoRunning) {
            stopDemo();
            return;
        }
        if (!playing || won || solving) {
            return;
        }
        if (board.getSize() > 4) {
            setStatus("求解器目前支持到 4 x 4");
            return;
        }
        solving = true;
        demoButton.setText("求解中…");
        PuzzleBoard snapshot = board;
        int generation = solveGeneration;
        startSolverWorker(snapshot, generation, result -> {
            solving = false;
            demoButton.setText("自动演示");
            if (generation != solveGeneration || won || result == null || result.length == 0) {
                if (result == null && generation == solveGeneration && !won) {
                    setStatus("求解失败：超出搜索预算");
                }
                return;
            }
            usedSolver = true;
            solution = result;
            demoIndex = 0;
            demoRunning = true;
            demoButton.setText("停止演示");
            demoTimer.start();
        });
    }

    /** 在后台线程用快速模式求解（毫秒级），结果回到 EDT 处理。 */
    private void startSolverWorker(PuzzleBoard snapshot, int generation, java.util.function.Consumer<int[]> callback) {
        Thread worker = new Thread(() -> {
            int[] result = PuzzleSolver.solveFast(snapshot, SOLVE_NODE_BUDGET);
            SwingUtilities.invokeLater(() -> callback.accept(result));
        });
        worker.setDaemon(true);
        worker.start();
    }

    private void demoTick() {
        if (!demoRunning || !playing || won || demoIndex >= solution.length) {
            stopDemo();
            return;
        }
        int pos = solution[demoIndex];
        int size = board.getSize();
        int row = pos / size;
        int col = pos % size;
        int toRow = board.getEmptyRow();
        int toCol = board.getEmptyCol();
        if (board.moveTile(row, col)) {
            demoIndex++;
            registerMove(row, col, toRow, toCol);
        } else {
            stopDemo();  // 局面与解意外脱同步，安全起见停止
        }
    }

    private void stopDemo() {
        demoRunning = false;
        if (demoTimer != null) {
            demoTimer.stop();
        }
        if (demoButton != null) {
            demoButton.setText("自动演示");
        }
    }

    /** 在提示文字位置临时显示状态，2.5 秒后自动恢复。 */
    private void setStatus(String message) {
        hintLabel.setText(message);
        statusTimer.restart();
    }

    private void bindKeys() {
        JComponent root = getRootPane();
        bind(root, KeyEvent.VK_UP, "up", () -> tryMoveEmpty(1, 0));
        bind(root, KeyEvent.VK_DOWN, "down", () -> tryMoveEmpty(-1, 0));
        bind(root, KeyEvent.VK_LEFT, "left", () -> tryMoveEmpty(0, 1));
        bind(root, KeyEvent.VK_RIGHT, "right", () -> tryMoveEmpty(0, -1));
        bind(root, KeyEvent.VK_W, "w", () -> tryMoveEmpty(1, 0));
        bind(root, KeyEvent.VK_S, "s", () -> tryMoveEmpty(-1, 0));
        bind(root, KeyEvent.VK_A, "a", () -> tryMoveEmpty(0, 1));
        bind(root, KeyEvent.VK_D, "d", () -> tryMoveEmpty(0, -1));
        bind(root, KeyEvent.VK_Z, "undo", this::undo);
        bind(root, KeyEvent.VK_H, "hint", this::requestHint);
        bind(root, KeyEvent.VK_ESCAPE, "stopdemo", this::stopDemo);
        bind(root, KeyEvent.VK_R, "restart", this::startNewGame);
    }

    private void bind(JComponent root, int key, String name, Runnable action) {
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(key, 0), name);
        root.getActionMap().put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    private class BoardPanel extends JPanel {
        private static final int GAP = 10;
        private static final int BOARD_ARC = 18;
        private static final int TILE_ARC = 16;
        private static final int SLIDE_MS = 90;

        private Font tileFont;
        private int tileFontSize = -1;

        private final Timer slideTimer;
        private int slideValue = -1;
        private int slideFromRow;
        private int slideFromCol;
        private int slideToRow;
        private int slideToCol;
        private long slideStart;

        private BoardPanel() {
            setPreferredSize(new Dimension(460, 460));
            setBackground(PANEL);
            slideTimer = new Timer(15, e -> tickSlide());
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (won) {
                        startNewGame();
                        return;
                    }
                    int[] cell = cellAt(e.getPoint());
                    if (cell != null) {
                        tryMove(cell[0], cell[1]);
                    }
                }
            });
        }

        /** 让数字块从 (fromRow, fromCol) 平滑滑到 (toRow, toCol)。 */
        private void slideTile(int fromRow, int fromCol, int toRow, int toCol, int value) {
            slideValue = value;
            slideFromRow = fromRow;
            slideFromCol = fromCol;
            slideToRow = toRow;
            slideToCol = toCol;
            slideStart = System.currentTimeMillis();
            slideTimer.restart();
            repaint();
        }

        private void stopSlide() {
            slideValue = -1;
            slideTimer.stop();
        }

        private void tickSlide() {
            if (slideProgress() >= 1.0) {
                slideTimer.stop();
            }
            repaint();
        }

        private double slideProgress() {
            long elapsed = System.currentTimeMillis() - slideStart;
            return Math.min(1.0, elapsed / (double) SLIDE_MS);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int size = board.getSize();
            Rectangle outer = boardRect();
            int cell = cellSize();

            g2.setColor(new Color(22, 28, 44));
            g2.fillRoundRect(outer.x, outer.y, outer.width, outer.height, BOARD_ARC, BOARD_ARC);

            int fontSize = Math.max(18, cell / 3);
            if (fontSize != tileFontSize) {
                tileFontSize = fontSize;
                tileFont = font(Font.BOLD, fontSize);
            }
            g2.setFont(tileFont);
            FontMetrics metrics = g2.getFontMetrics();
            int ascent = metrics.getAscent();

            boolean sliding = slideValue > 0 && slideProgress() < 1.0;
            for (int r = 0; r < size; r++) {
                for (int c = 0; c < size; c++) {
                    int value = board.getTile(r, c);
                    if (value == 0) {
                        Rectangle rect = cellRect(r, c);
                        g2.setColor(new Color(36, 46, 68));
                        g2.fill(new RoundRectangle2D.Float(rect.x, rect.y, rect.width, rect.height, TILE_ARC, TILE_ARC));
                        continue;
                    }
                    if (sliding && r == slideToRow && c == slideToCol) {
                        continue;  // 动画中的块稍后在插值位置绘制
                    }
                    drawTile(g2, cellRect(r, c), value, value == r * size + c + 1,
                            r * size + c == hintPos, metrics, ascent);
                }
            }

            if (sliding) {
                Rectangle from = cellRect(slideFromRow, slideFromCol);
                Rectangle to = cellRect(slideToRow, slideToCol);
                double t = slideProgress();
                int x = (int) Math.round(from.x + (to.x - from.x) * t);
                int y = (int) Math.round(from.y + (to.y - from.y) * t);
                boolean correct = slideValue == slideToRow * size + slideToCol + 1;
                drawTile(g2, new Rectangle(x, y, from.width, from.height), slideValue, correct, false, metrics, ascent);
            }

            if (won) {
                paintOverlay(g2, outer);
            }
            g2.dispose();
        }

        private void drawTile(Graphics2D g2, Rectangle rect, int value, boolean correct, boolean hinted,
                FontMetrics metrics, int ascent) {
            g2.setColor(won ? SOLVED : TILE);
            g2.fill(new RoundRectangle2D.Float(rect.x, rect.y, rect.width, rect.height, TILE_ARC, TILE_ARC));
            if (hinted) {
                g2.setColor(HINT_TILE);
            } else {
                g2.setColor(correct && !won ? SOLVED : TILE_ACCENT);
            }
            g2.draw(new RoundRectangle2D.Float(rect.x + 1, rect.y + 1, rect.width - 3, rect.height - 3, TILE_ARC, TILE_ARC));
            g2.setColor(TEXT);
            String text = String.valueOf(value);
            int tw = metrics.stringWidth(text);
            g2.drawString(text, rect.x + (rect.width - tw) / 2, rect.y + (rect.height + ascent) / 2 - 4);
        }

        /** 通关后的半透明结算层，替代模态弹窗。 */
        private void paintOverlay(Graphics2D g2, Rectangle outer) {
            g2.setColor(new Color(10, 16, 28, 200));
            g2.fillRoundRect(outer.x, outer.y, outer.width, outer.height, BOARD_ARC, BOARD_ARC);
            int centerX = outer.x + outer.width / 2;
            int centerY = outer.y + outer.height / 2;
            drawCentered(g2, "通关！", font(Font.BOLD, 30), TEXT, centerX, centerY - 52);
            drawCentered(g2, "步数 " + moves + " · 用时 " + formatTime(elapsedSeconds), font(Font.BOLD, 17), TEXT, centerX, centerY - 8);
            if (newRecord) {
                drawCentered(g2, "新纪录！", font(Font.BOLD, 15), SOLVED, centerX, centerY + 22);
            } else if (usedSolver) {
                drawCentered(g2, "本局使用了求解器，不计入纪录", font(Font.PLAIN, 14), DIM, centerX, centerY + 22);
            }
            drawCentered(g2, "按 R 或点击棋盘再来一局", font(Font.PLAIN, 13), DIM, centerX, centerY + 56);
        }

        private void drawCentered(Graphics2D g2, String text, Font textFont, Color color, int centerX, int y) {
            g2.setFont(textFont);
            g2.setColor(color);
            FontMetrics metrics = g2.getFontMetrics();
            g2.drawString(text, centerX - metrics.stringWidth(text) / 2, y);
        }

        /** 棋盘外框（含四周留白），在面板内居中。 */
        private Rectangle boardRect() {
            int size = board.getSize();
            int side = cellSize() * size + GAP * (size + 1);
            return new Rectangle((getWidth() - side) / 2, (getHeight() - side) / 2, side, side);
        }

        /** 第 (row, col) 格的矩形。 */
        private Rectangle cellRect(int row, int col) {
            Rectangle outer = boardRect();
            int cell = cellSize();
            return new Rectangle(
                    outer.x + GAP + col * (cell + GAP),
                    outer.y + GAP + row * (cell + GAP),
                    cell, cell);
        }

        /** 依据面板当前宽高计算格子边长。 */
        private int cellSize() {
            int size = board.getSize();
            return (Math.min(getWidth(), getHeight()) - GAP * (size + 1)) / size;
        }

        /** 命中的格子坐标 {row, col}；点到缝隙或棋盘外返回 null。 */
        private int[] cellAt(Point p) {
            for (int r = 0; r < board.getSize(); r++) {
                for (int c = 0; c < board.getSize(); c++) {
                    if (cellRect(r, c).contains(p)) {
                        return new int[] { r, c };
                    }
                }
            }
            return null;
        }
    }
}
