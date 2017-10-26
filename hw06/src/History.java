import java.net.DatagramSocket;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.function.Consumer;

public class History implements Runnable {
  private static final long MAX_BLOCK_MILLIS = 25;
  private static final String TAG = "history thrd";
  private static final Logger log = new Logger(TAG);
  public final DatagramSocket source;

  private LockedMapQueue<String, Message> receiptFIFOs = null;
  private LockedMapQueue<String, Message> sendingFIFOs = null;

  private Thread plumber;
  private boolean isPlumbing = false;

  private final Lock registryLock;
  private Map<String, List<Runnable>> registry;
  private Consumer defaultListener = null;

  private LockedMapList<String, Message> full = null;
  public History(final DatagramSocket source) {
    this.source = source;

    this.receiptFIFOs = new LockedMapQueue<String, Message>();
    this.sendingFIFOs = new LockedMapQueue<String, Message>();
    this.full = new LockedMapList<String, Message>();

    this.registryLock = new ReentrantLock();
    this.registry = new HashMap<String, List<Runnable>>();
  }

  public History setLogLevel(final Logger.Level lvl) {
    this.log.setLevel(lvl);
    return this;
  }

  public void registerDefaultListener(Consumer<Remote> listener) {
    this.defaultListener = listener;
  }

  /**
   * Runs {@code listener} if any history updates occur on the duplex with
   * remote host, {@code trigger}.
   */
  // TODO(zacsh) audit codebase, find anywhere else i'm naiively doing a
  // `while(alwaysTrueThreadLifeStatus)` CPU-killing loop, and replace those
  // with registry to something like this API
  public void registerRemoteListener(Remote trigger, Runnable listener) {
    RunLocked.safeRun(this.registryLock, () -> {
      final String triggerID = trigger.toString();
      List<Runnable> listeners;
      if (!this.registry.containsKey(triggerID)) {
        this.registry.put(triggerID, new ArrayList<Runnable>());
      }
      this.registry.get(triggerID).add(listener);
    });
  }

  private void notifyListenersUnsafe(final String remoteID) {
    this.log.debugf(
        "notifying listeners on activity for '%s' [have: listeners=%s, default=%s]\n",
        remoteID, this.registry.containsKey(remoteID), this.defaultListener != null);
    if (this.registry.containsKey(remoteID)) {
      this.registry.get(remoteID).forEach((Runnable listener) -> listener.run());
      return;
    }
    if (this.defaultListener == null) {
      return;
    }
    this.log.debugf("passing '%s' activity to default listener\n", remoteID);
    this.defaultListener.accept(this.determineRemote(remoteID));
  }

  private Remote determineRemote(String remoteID) {
    // NOTE: get(0) should always return, otherwise we would not have been triggered
    return this.full.getNonEmpty(remoteID).get(0).getRemote();
  }

  private static Queue<Message> getNonEmptyFIFO(Map<String, Queue<Message>> m, String key) {
    if (!m.containsKey(key)) {
      m.put(key, new LinkedList<Message>());
    }
    return m.get(key);
  }

  public void safeEnqueueReceived(final Remote r, final String message) {
    this.receiptFIFOs.add(r.toString(), new Message(r, message, true /*isReceived*/));
  }

  public void safeEnqueueSend(final Remote r, final String message) {
    this.sendingFIFOs.add(r.toString(), new Message(r, message, false /*isReceived*/));
  }

  public LockedList<Message> getHistoryWith(final Remote remote) {
    return this.full.getNonEmpty(remote.toString());
  }

  /** unsafe; calls should be wrapped in sendingLock.lock(). */
  private void flushSends() {
    this.sendingFIFOs.forEachNonEmpty((final String remoteID, LockedQ<Message> q) -> {
      if (!this.isPlumbing) { return; }

      LockedList<Message> chatHist = this.full.getNonEmpty(remoteID);
      q.drain((Message toSend) -> {
        try {
          this.source.send(toSend.toPacket());
        } catch(Exception e) {
          this.log.errorf(e, "sending %s message %03d", toSend.getRemote(), chatHist.size() - 1);
          this.stopPlumber();
          return;
        }

        chatHist.add(toSend);
        this.log.debugf(
            "socket.send()d: %s message %03d: '%s'\n",
            toSend.getRemote(), chatHist.size() - 1, toSend.getMessage());
      });

      RunLocked.safeRun(this.registryLock, () -> this.notifyListenersUnsafe(remoteID));
    });
  }

  /** unsafe; calls should be wrapped in receiptLock.lock(). */
  private void flushReceives() {
    this.receiptFIFOs.forEachNonEmpty((String remoteID, LockedQ<Message> q) -> {
      if (!this.isPlumbing) { return; } // fail a bit faster

      LockedList<Message> chatHist = this.full.getNonEmpty(remoteID);

      q.drain((Message received) -> {
        chatHist.add(received);
        this.log.debugf(
            "saved message #%03d from %s: '%s'\n",
            chatHist.size() - 1,
            received.getRemote(),
            received.getMessage());
      });

      RunLocked.safeRun(this.registryLock, () -> this.notifyListenersUnsafe(remoteID));
    });
  }

  public void run() {
    while (this.isPlumbing) {
      this.flushSends();
      this.flushReceives();
    }
  }

  public void startPlumber() {
    this.isPlumbing = true;
    this.plumber = new Thread(this);
    this.plumber.setName(TAG);
    this.plumber.start();
    this.log.printf("spawned \"%s\" thread: %s\n", TAG, this.plumber);
  }

  // Idempotent halter to the plumber's internal logic
  public Thread stopPlumber() {
    this.isPlumbing = false;
    return this.plumber;
  }
}
