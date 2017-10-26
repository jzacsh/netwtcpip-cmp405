import java.util.concurrent.locks.Lock;

public class RunLocked {
  public static void safeRun(Lock locker, Runnable toRun) {
    locker.lock();
    try {
      toRun.run();
    } finally {
      locker.unlock();
    }
  }

  /** Returns if {@code toRun} was ever executed.  */
  public static boolean safeRunTry(Lock locker, Runnable toRun) {
    boolean isAcquired = false;
    try {
      isAcquired = locker.tryLock(MAX_BLOCK_MILLIS, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      return false;
    }
    if (!isAcquired) {
      return false;
    }

    try {
      toRun.run();
    } finally {
      locker.unlock();
    }
    return true;
  }
}
