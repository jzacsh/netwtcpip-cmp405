import java.util.concurrent.locks.Lock;
import java.util.concurrent.TimeUnit;

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
  public static boolean safeRunTry(long millis, Lock locker, Runnable toRun) {
    boolean isAcquired = false;
    try {
      isAcquired = locker.tryLock(millis, TimeUnit.MILLISECONDS);
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
