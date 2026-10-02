import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public class DigitalHuarongdao {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
                // 使用默认外观即可
            }
            PuzzleGameFrame frame = new PuzzleGameFrame();
            frame.setVisible(true);
        });
    }
}
