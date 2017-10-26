import java.util.concurrent.locks.Lock;

public class RunLocked {
  private static void safeRun(Lock locker, Runnable toRun) {
    locker.lock();
    try {
      toRun.run();
    } finally {
      locker.unlock();
    }
  }

  private static boolean safeRunTry(Lock locker, Runnable toRun) {
    boolean isLocked = false;
    try {
      isLocked = locker.tryLock(MAX_BLOCK_MILLIS, TimeUnit.MILLISECONDS);
    } catch (InterruptedException e) {
      return false;
    }
    if (!isLocked) {
      return isLocked;
    }

    try {
      toRun.run();
    } finally {
      locker.unlock();
    }
    return true;
  }
}
