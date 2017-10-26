import java.util.concurrent.locks.Lock;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.HashMap;
import java.util.function.BiConsumer;

public class LockedMapList<K, V> {
  private Lock lock;
  private Map<K, LockedList<V>> store;

  public LockedMapList() {
    this.lock = new ReentrantLock();
    this.store = new HashMap<K, LockedList<V>>();
  }

  /**
   * Blocking get() on internal map, given a {@code key}.
   *
   * However, mutations on the returned Queue should be accessed via
   */
  public LockedList<V> getNonEmpty(K key) {
    RunLocked.safeRun(this.lock, () -> {
      if (!this.store.containsKey(key)) {
        this.store.put(key, new LockedList<V>());
      }
    });
    return this.store.get(key);
  }

  public void add(K key, V val) {
    this.getNonEmpty(key).add(val);
  }

  public void forEachNonEmpty(BiConsumer<K, LockedList<V>> handler) {
    this.store.forEach((K key, LockedList<V> q) -> {
      if (q.isEmpty()) {
        return;
      }
      handler.accept(key, q);
    });
  }
}
