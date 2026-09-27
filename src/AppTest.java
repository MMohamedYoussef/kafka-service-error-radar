import java.util.List;

public class AppTest {
    private static void same(List<String> expected, List<String> actual) {
        if (!expected.equals(actual)) throw new AssertionError("expected=" + expected + " actual=" + actual);
    }

    private static void rejects(Runnable action) {
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("invalid severity was accepted");
    }

    public static void main(String[] args) {
        same(List.of("service,minute,error_count", "api,2,3", "api,15,3"), App.process(List.of(
                "a,api,0,ERROR", "b,api,1,ERROR", "c,api,2,ERROR", "d,api,4,ERROR", "d,api,4,ERROR",
                "e,api,13,ERROR", "f,api,14,ERROR", "g,api,15,ERROR")));
        rejects(() -> App.process(List.of("a,api,0,WARN")));
        rejects(() -> App.process(List.of("a,api,2,ERROR", "b,api,1,ERROR")));
        rejects(() -> App.process(List.of("a,api,0,ERROR", "a,api,0,INFO")));
        same(List.of("service,minute,error_count"), App.process(List.of()));
        System.out.println("radar tests passed");
    }
}
