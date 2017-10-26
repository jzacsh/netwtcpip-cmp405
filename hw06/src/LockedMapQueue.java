import java.util.concurrent.locks.Lock;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.HashMap;
import java.util.function.BiConsumer;

public class LockedMapQueue<K, V> {
  private Lock lock;
  private Map<K, LockedQ<V>> store;

  public LockedMapQueue() {
    this.lock = new ReentrantLock();
    this.store = new HashMap<K, LockedQ<V>>();
  }

  /**
   * Blocking get() on internal map, given a {@code key}.
   *
   * However, mutations on the returned Queue should be accessed via
   */
  public LockedQ<V> getNonEmpty(K key) {
    RunLocked.safeRun(this.lock, () -> {
      if (!this.store.containsKey(key)) {
        this.store.put(key, new LockedQ<V>());
      }
    });
    return this.store.get(key);
  }

  public void add(K key, V val) {
    this.getNonEmpty(key).add(val);
  }

  public void forEachNonEmpty(BiConsumer<K, LockedQ<V>> handler) {
    RunLocked.safeRun(this.lock, () -> {
      this.store.forEach((K key, LockedQ<V> q) -> {
        if (q.isEmpty()) {
          return;
        }
        handler.accept(key, q);
      });
    });
  }
}
