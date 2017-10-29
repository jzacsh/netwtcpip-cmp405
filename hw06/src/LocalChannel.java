public interface LocalChannel extends Runnable {
  public LocalChannel report();

  public void stopChannel();

  public LocalChannel setLogLevel(final Logger.Level lvl);
  public boolean isActive();
  public boolean isFailed();
}
