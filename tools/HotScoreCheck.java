/**
 * 热度分自检器（不依赖 JUnit / 不依赖 Maven surefire）
 *
 * 为什么不用 mvn test：本机离线 + 本地 Maven 仓库只读，
 * surefire 的 junit-platform 插件下不下来，mvn test 直接报错。
 * 所以这里用最笨但最可靠的办法：一个 main 方法，手写断言。
 *
 * 用法（在项目根目录）：
 *   javac -encoding UTF-8 -cp "campus-server/target/classes" -d .dsh-hotcheck tools/HotScoreCheck.java
 *   java -cp "campus-server/target/classes;.dsh-hotcheck" HotScoreCheck
 */
import com.campus.service.impl.ReportServiceImpl;

public class HotScoreCheck {

    private static int pass = 0;
    private static int fail = 0;

    private static void check(String name, double expected, Integer sold, Integer elapsed, Integer fav) {
        double actual;
        try {
            actual = ReportServiceImpl.calcHotScore(sold, elapsed, fav);
        } catch (Throwable t) {
            fail++;
            System.out.println("  FAIL  " + name + "  → 抛异常: " + t.getClass().getSimpleName()
                    + (t.getMessage() == null ? "" : " " + t.getMessage()));
            return;
        }
        boolean ok = Math.abs(actual - expected) <= 0.01;
        if (ok) {
            pass++;
        } else {
            fail++;
        }
        System.out.println("  " + (ok ? "PASS" : "FAIL") + "  " + pad(name, 42)
                + "  期望=" + String.format("%.2f", expected)
                + "  实际=" + String.format("%.2f", actual));
    }

    private static String pad(String s, int n) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < n) {
            sb.append(' ');
        }
        return sb.toString();
    }

    public static void main(String[] args) {
        System.out.println("=== calcHotScore 自检 ===");

        // 一、四个典型场景
        check("昨天挂今天卖,收藏5 → 84.67",        84.67, 1, 1, 5);
        check("卖了但花60天,无收藏 → 60",          60.00, 1, 60, 0);
        check("挂30天没卖,收藏3 → 23",             23.00, 0, 30, 3);
        check("挂200天没卖,无收藏 → 0",             0.00, 0, 200, 0);

        // 二、边界与兜底
        check("全 null → 0（不许 NPE）",            0.00, null, null, null);
        check("满分:卖+当天+收藏爆表 → 100",       100.00, 1, 0, 100);
        check("收藏封顶:20 和 999 一样",            50.00, 0, 0, 20);
        check("收藏封顶:999 也当 20",               50.00, 0, 0, 999);
        check("90天封顶:90 天 = 0 耗时分",           0.00, 0, 90, 0);
        check("90天封顶:999 天也是 0",               0.00, 0, 999, 0);

        // 三、设计意图：卖出是分水岭
        check("卖过的最差情况 → 50",                50.00, 1, 999, 0);
        check("没卖过的最好情况 → 50",              50.00, 0, 0, 999);
        check("同耗时同收藏,卖过多 50 分",          50.00 + 20.00 / 3 + 5, 1, 10, 5);

        System.out.println();
        System.out.println("=== 结果: PASS=" + pass + "  FAIL=" + fail + "  总计=" + (pass + fail) + " ===");
        if (fail > 0) {
            System.exit(1);
        }
    }
}
