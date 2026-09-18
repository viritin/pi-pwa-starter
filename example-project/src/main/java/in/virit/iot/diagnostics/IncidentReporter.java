package in.virit.iot.diagnostics;

import com.vaadin.flow.shared.Registration;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import io.quarkus.runtime.StartupEvent;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Collects failures without requiring every background service to know about Vaadin.
 * The output bridge deliberately keeps the original streams intact.
 */
@ApplicationScoped
public class IncidentReporter {
    private static final int MAX_INCIDENTS = 100;
    private static final int MAX_DETAILS = 32_000;
    private static final Pattern THROWABLE = Pattern.compile(
            "(?:^|\\s)([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*(?:Exception|Error|Throwable))(?:[:\\s]|$)");
    private static final Pattern STACK_FRAME = Pattern.compile("\\s*at\\s+.*|\\s*Caused by:.*|\\s*Suppressed:.*");
    private final ArrayDeque<Incident> incidents = new ArrayDeque<>();
    private final CopyOnWriteArrayList<Consumer<Incident>> listeners = new CopyOnWriteArrayList<>();
    private volatile boolean installed;

    public void start(@Observes StartupEvent ignored) {
        install();
    }

    public synchronized List<Incident> recent() {
        return List.copyOf(incidents);
    }

    public long pendingCount() {
        synchronized (this) {
            return incidents.size();
        }
    }

    public Registration addListener(Consumer<Incident> listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public void report(Throwable error, String source) {
        if (error == null) return;
        var text = stackTrace(error);
        report(source, error.getClass().getSimpleName() + ": " + safeMessage(error), text);
    }

    public void report(String source, String summary, String details) {
        var incident = new Incident(Instant.now(), source, Thread.currentThread().getName(),
                summary, limit(details));
        synchronized (this) {
            if (incidents.size() == MAX_INCIDENTS) incidents.removeFirst();
            incidents.addLast(incident);
        }
        for (var listener : listeners) {
            try {
                listener.accept(incident);
            } catch (RuntimeException ignored) {
                // Diagnostics must never become a second failure source.
            }
        }
    }

    private synchronized void install() {
        if (installed) return;
        installed = true;
        var oldOut = System.out;
        var oldErr = System.err;
        System.setOut(new PrintStream(new CapturingStream(oldOut, this, "stdout"), true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(new CapturingStream(oldErr, this, "stderr"), true, StandardCharsets.UTF_8));
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> report(error, "uncaught-thread"));
    }

    private static String safeMessage(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank() ? "No message" : error.getMessage();
    }

    private static String stackTrace(Throwable error) {
        var bytes = new ByteArrayOutputStream();
        error.printStackTrace(new PrintStream(bytes, true, StandardCharsets.UTF_8));
        return limit(bytes.toString(StandardCharsets.UTF_8));
    }

    private static String limit(String text) {
        if (text == null) return "";
        return text.length() <= MAX_DETAILS ? text : text.substring(0, MAX_DETAILS) + "\n[…truncated]";
    }

    private static final class CapturingStream extends java.io.OutputStream {
        private final PrintStream delegate;
        private final IncidentReporter reporter;
        private final String source;
        private final StringBuilder line = new StringBuilder();
        private final StringBuilder candidate = new StringBuilder();
        private int frames;

        private CapturingStream(PrintStream delegate, IncidentReporter reporter, String source) {
            this.delegate = delegate;
            this.reporter = reporter;
            this.source = source;
        }

        @Override
        public synchronized void write(int value) {
            delegate.write(value);
            if (value == '\n') consumeLine(line.toString());
            else if (line.length() < MAX_DETAILS) line.append((char) value);
        }

        @Override
        public synchronized void write(byte[] bytes, int offset, int length) {
            delegate.write(bytes, offset, length);
            var text = new String(bytes, offset, length, StandardCharsets.UTF_8);
            for (int i = 0; i < text.length(); i++) writeCaptured(text.charAt(i));
        }

        private void writeCaptured(char value) {
            if (value == '\n') consumeLine(line.toString());
            else if (line.length() < MAX_DETAILS) line.append(value);
        }

        private void consumeLine(String raw) {
            line.setLength(0);
            var text = raw.stripTrailing();
            if (candidate.length() > 0 && (STACK_FRAME.matcher(text).matches() || text.isBlank())) {
                append(text);
                if (STACK_FRAME.matcher(text).matches()) frames++;
                if (frames >= 1 && text.isBlank()) finish();
                return;
            }
            if (candidate.length() > 0 && frames >= 1) finish();
            var match = THROWABLE.matcher(text);
            if (match.find()) {
                candidate.setLength(0);
                candidate.append(text);
                frames = 0;
            }
        }

        private void append(String text) {
            if (candidate.length() < MAX_DETAILS) candidate.append('\n').append(text);
        }

        private void finish() {
            var details = candidate.toString();
            var first = details.indexOf('\n');
            var summary = first < 0 ? details : details.substring(0, first);
            reporter.report(source, summary, details);
            candidate.setLength(0);
            frames = 0;
        }
    }
}
