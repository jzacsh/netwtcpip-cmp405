import java.io.PrintStream;
import java.io.StringWriter;
import java.io.PrintWriter;

public class Logger {
  public enum Level { DEBUG, INFO, WARN, ERROR }

  private final String tag;
  private final Level DEFAULT_LEVEL = Level.INFO;

  private Level lvl = DEFAULT_LEVEL;
  public Logger(final String tag) { this.tag = tag; }

  private void fprintf(PrintStream out, String fmt, Object... args) {
    out.printf(String.format("[%s] %s", this.tag, fmt), args);
  }

  public void errorf(String fmt, Object... args) {
    this.fprintf(System.err, String.format("ERROR: %s", fmt), args);
  }

  public void debugf(String fmt, Object... args) {
    if (this.lvl != Level.DEBUG) { return; } // ignore
    this.printf(fmt, args);
  }

  /**
   * NOTE: this method appends its own newline to the end of all logs.
   */
  public void errorf(Throwable e, String fmt, Object... args) {
    String error;
    if (this.lvl == Level.DEBUG) {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      e.printStackTrace(pw);
      error = sw.toString();
    } else {
      error = e.toString();
    }
    this.errorf(String.format("%s: %s\n", fmt, error), args);
  }

  public void printf(String fmt, Object... args) {
    if (this.lvl == Level.ERROR) { return; } // ignore
    this.fprintf(System.out, fmt, args);
  }

  public void setLevel(final Logger.Level lvl) { this.lvl = lvl; }
}
