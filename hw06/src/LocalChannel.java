public interface LocalChannel extends Runnable {
  public LocalChannel report();
  public LocalChannel setLogLevel(final Logger.Level lvl);
}
