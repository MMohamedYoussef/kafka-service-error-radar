import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class App {
    public static List<String> process(List<String> records) {
        List<String> output = new ArrayList<>(List.of("service,minute,error_count"));
        Map<String, String> seen = new HashMap<>();
        Map<String, Deque<Long>> errors = new HashMap<>();
        Map<String, Long> latestMinute = new HashMap<>();
        Map<String, Long> lastAlert = new HashMap<>();
        for (String record : records) {
            String[] row = record.split(",", -1);
            if (row.length != 4 || row[0].isBlank() || row[1].isBlank()) throw new IllegalArgumentException("Expected event_id,service,minute,severity");
            long minute = Long.parseLong(row[2]);
            if (minute < 0 || !(row[3].equals("INFO") || row[3].equals("ERROR"))) throw new IllegalArgumentException("Invalid minute or severity");
            String oldEvent = seen.putIfAbsent(row[0], record);
            if (oldEvent != null) {
                if (!oldEvent.equals(record)) throw new IllegalArgumentException("Conflicting duplicate event ID: " + row[0]);
                continue;
            }
            String service = row[1];
            if (minute < latestMinute.getOrDefault(service, -1L)) throw new IllegalArgumentException("Out-of-order service event");
            latestMinute.put(service, minute);
            Deque<Long> window = errors.computeIfAbsent(service, ignored -> new ArrayDeque<>());
            while (!window.isEmpty() && window.peekFirst() < minute - 4) window.removeFirst();
            if (row[3].equals("ERROR")) window.addLast(minute);
            Long previousAlert = lastAlert.get(service);
            if (window.size() >= 3 && (previousAlert == null || minute - previousAlert >= 10)) {
                output.add(service + "," + minute + "," + window.size());
                lastAlert.put(service, minute);
            }
        }
        return output;
    }

    public static void main(String[] args) throws Exception {
        List<String> records = Files.readAllLines(Path.of("fixtures/events.csv"), StandardCharsets.UTF_8).stream().filter(line -> !line.isBlank()).toList();
        if (args.length > 0) {
            if (!"--broker".equals(args[0]) || args.length < 2) throw new IllegalArgumentException("Usage: App [--broker bootstrap-server [topic-prefix]]");
            records = BrokerReplay.replay(records, args[1], args.length > 2 ? args[2] : "error-radar");
        }
        for (String line : process(records)) System.out.println(line);
    }
}
