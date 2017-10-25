import java.net.DatagramSocket;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.LinkedList;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

public class History {
  private static final long MAX_BLOCK_MILLIS = 25;
  private static final String TAG = "history thrd";
  private static final Logger log = new Logger(TAG);
  public final DatagramSocket source;

  public final ReentrantLock receiptLock;
  private Map<String, Queue<Message>> receiptFIFOs = null;

  public final ReentrantLock sendingLock;
  private Map<String, Queue<Message>> sendingFIFOs = null;

  private Map<String, List<Message>> full = null;
  public History(final DatagramSocket source) {
    this.source = source;
    this.receiptLock = new ReentrantLock();
    this.receiptFIFOs = new HashMap<String, Queue<Message>>();

    this.sendingLock = new ReentrantLock();
    this.sendingFIFOs = new HashMap<String, Queue<Message>>();

    this.full = new HashMap<String, List<Message>>();
  }

  public History setLogLevel(final Logger.Level lvl) {
    this.log.setLevel(lvl);
    return this;
  }

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

  private static Queue<Message> getNonEmptyFIFO(Map<String, Queue<Message>> m, String key) {
    if (!m.containsKey(key)) {
      m.put(key, new LinkedList<Message>());
    }
    return m.get(key);
  }

  /** unsafe; calls should be wrapped in receiptLock.lock(). */
  private void enqueueReceived(Remote r, Message received) {
    Queue<Message> receiving = getNonEmptyFIFO(this.receiptFIFOs, r.toString());
    receiving.add(received);
  }

  /** safe version of {@link #enqueueReceived}. */
  public void safeEnqueueReceived(final Remote r, final Message received) {
    History.safeRun(this.receiptLock, () -> this.enqueueReceived(r, received));
  }

  /** unsafe; calls should be wrapped in receiptLock.lock(). */
  public Queue<Message> getRecvQueue(Remote r) {
    return this.receiptFIFOs.get(r.toString());
  }

  /** unsafe; calls should be wrapped in sendingLock.lock(). */
  private void enqueueToSend(Remote r, Message sending) {
    Queue<Message> sendingQueue = getNonEmptyFIFO(this.sendingFIFOs, r.toString());
    sendingQueue.add(sending);
  }

  /** safe version of {@link #enqueueToSend} */
  public void safeEnqueueSend(final Remote r, final Message sending) {
    History.safeRun(this.sendingLock, () -> this.enqueueToSend(r, sending));
  }

  /** unsafe; calls should be wrapped in sendingLock.lock(). */
  public Queue<Message> getSendQueue(Remote r) {
    return this.sendingFIFOs.get(r.toString());
  }

  private List<Message> getNonEmptyRemoteHist(final String remoteID) {
    if (!this.full.containsKey(remoteID)) {
      this.full.put(remoteID, new ArrayList<Message>());
    }
    return this.full.get(remoteID);
  }
}
