public interface LocalChannel extends Runnable {
  public LocalChannel report();

  public LocalChannel startChannel();
  public Thread stopChannel();

  public LocalChannel setLogLevel(final Logger.Level lvl);
  public boolean isActive();
  public boolean isFailed();
  public Thread thread();
}
