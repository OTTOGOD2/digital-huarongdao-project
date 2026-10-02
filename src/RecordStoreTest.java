import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * RecordStore 自测，不依赖 JUnit：
 *
 * <pre>
 * javac -encoding UTF-8 -d out src\*.java
 * java -cp out RecordStoreTest
 * </pre>
 *
 * 使用临时文件，结束后清理，不触碰真实纪录文件。
 */
public class RecordStoreTest {
    private final List<String> failures = new ArrayList<>();
    private int passed = 0;

    public static void main(String[] args) throws Exception {
        RecordStoreTest suite = new RecordStoreTest();
        Path file = Files.createTempFile("huarongdao-records", ".properties");
        try {
            suite.runAll(file);
        } finally {
            Files.deleteIfExists(file);
        }
        System.out.println("通过 " + suite.passed + " 项，失败 " + suite.failures.size() + " 项。");
        for (String failure : suite.failures) {
            System.out.println("失败: " + failure);
        }
        if (!suite.failures.isEmpty()) {
            System.exit(1);
        }
    }

    private void runAll(Path file) {
        RecordStore store = new RecordStore(file);
        check(store.bestMoves(4) == null && store.bestSeconds(4) == null, "初始应无纪录");
        check(store.submit(4, 50, 120), "首次提交应算新纪录");
        check(is(store.bestMoves(4), 50) && is(store.bestSeconds(4), 120), "首次提交后应能读到");
        check(store.submit(4, 60, 90), "仅用时更优也应算新纪录");
        check(is(store.bestMoves(4), 50) && is(store.bestSeconds(4), 90), "较优项更新、较差项保留");
        check(!store.submit(4, 55, 100), "两项都不更优应返回 false");
        check(store.bestMoves(5) == null && store.bestSeconds(5) == null, "不同尺寸互不影响");

        RecordStore reloaded = new RecordStore(file);
        check(is(reloaded.bestMoves(4), 50) && is(reloaded.bestSeconds(4), 90), "重新加载应保持已存纪录");
        check(reloaded.submit(4, 40, 80), "全面刷新应返回 true");
        check(is(new RecordStore(file).bestMoves(4), 40) && is(new RecordStore(file).bestSeconds(4), 80),
                "刷新后应已写盘");
    }

    private boolean is(Integer actual, int expected) {
        return actual != null && actual == expected;
    }

    private void check(boolean condition, String message) {
        if (condition) {
            passed++;
        } else {
            failures.add(message);
        }
    }
}
