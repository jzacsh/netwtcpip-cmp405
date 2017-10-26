
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Consumer;

public class LockedList<T> {
  private List<T> payload;
  private Lock lock;

  public LockedList() {
    this.lock = new ReentrantLock();
    this.payload = new ArrayList<T>();
  }

  public boolean isEmpty() { return this.payload.isEmpty(); }

  public void add(T element) {
    RunLocked.safeRun(this.lock, () -> this.payload.add(element));
  }

  public int size() { return this.payload.size(); }
  public T get(int i) { return this.payload.get(i); }

  /** {@Link List#forEach} esqueue API, but skips indices less than lowerBound. */
  public void getPastIndex(int lowerBound, Consumer<T> handler) {
    if (this.size() <= lowerBound) {
      return; // noop
    }
    RunLocked.safeRun(this.lock, () -> {
      for (int i = lowerBound; i < this.size(); ++i) {
        handler.accept(this.payload.get(i));
      }
    });
  }
}
