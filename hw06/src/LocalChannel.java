public interface LocalChannel extends Runnable {
  public LocalChannel report();
  public LocalChannel startChannel();
  public LocalChannel setLogLevel(final Logger.Level lvl);
  public boolean isActive();
  public boolean isFailed();
  public Thread stop();
  public Thread thread();
}
