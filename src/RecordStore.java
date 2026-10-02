import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * 按棋盘大小保存最佳成绩（最少步数、最短用时），
 * 持久化到用户目录下的 .digital-huarongdao-records.properties。
 */
public class RecordStore {
    private static final String MOVES_KEY = "best.moves.";
    private static final String SECONDS_KEY = "best.seconds.";

    private final Path file;
    private final Properties records = new Properties();

    public RecordStore() {
        this(Paths.get(System.getProperty("user.home"), ".digital-huarongdao-records.properties"));
    }

    RecordStore(Path file) {
        this.file = file;
        load();
    }

    public Integer bestMoves(int size) {
        return read(MOVES_KEY + size);
    }

    public Integer bestSeconds(int size) {
        return read(SECONDS_KEY + size);
    }

    /** 提交一次通关成绩，任一项刷新即算新纪录并写盘。 */
    public boolean submit(int size, int moves, int seconds) {
        boolean improved = submitKey(MOVES_KEY + size, moves);
        improved |= submitKey(SECONDS_KEY + size, seconds);
        if (improved) {
            save();
        }
        return improved;
    }

    private boolean submitKey(String key, int value) {
        Integer old = read(key);
        if (old == null || value < old) {
            records.setProperty(key, String.valueOf(value));
            return true;
        }
        return false;
    }

    private Integer read(String key) {
        String value = records.getProperty(key);
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void load() {
        if (!Files.isReadable(file)) {
            return;
        }
        try (InputStream in = Files.newInputStream(file)) {
            records.load(in);
        } catch (IOException e) {
            // 读不到就当作没有历史纪录
        }
    }

    private void save() {
        try (OutputStream out = Files.newOutputStream(file)) {
            records.store(out, "digital-huarongdao best records");
        } catch (IOException e) {
            // 写不进去只影响纪录保存，不影响游戏本身
        }
    }
}
