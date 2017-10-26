import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.Queue;
import java.util.LinkedList;
import java.util.function.Consumer;

public class LockedQ<T> {
  private Queue<T> payload;
  private Lock lock;

  public LockedQ() {
    this.lock = new ReentrantLock();
    this.payload = new LinkedList<T>();
  }

  public boolean isEmpty() { return this.payload.isEmpty(); }

  public void add(T element) {
    RunLocked.safeRun(this.lock, () -> this.payload.add(element));
  }

  public int size() { return this.payload.size(); }

  public void drain(Consumer<T> handler) {
    if (this.payload.isEmpty()) {
      return;
    }

    RunLocked.safeRun(this.lock, () -> {
      while (!this.payload.isEmpty()) {
        handler.accept(this.payload.poll());
      }
    });
  }
}
