import java.net.DatagramSocket;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.Consumer;
import java.util.function.BiConsumer;

public class History implements Runnable {
  private static final long FLUSH_CYCLE_MILLIS = 200;
  private static final long MAX_BLOCK_MILLIS = 25;
  private static final String TAG = "history thrd";
  private static final Logger log = new Logger(TAG);
  public final DatagramSocket source;

  private LockedMapQueue<String, Message> receiptFIFOs = null;
  private LockedMapQueue<String, Message> sendingFIFOs = null;

  protected boolean isPlumbing = false;

  private final Lock registryLock;
  private Map<String, List<Runnable>> registry;
  private Consumer<Remote> defaultListener = null;

  private BiConsumer<Remote, String> broadcastHandler = null;

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
  public void registerRemoteListener(Remote trigger, Runnable listener) {
    RunLocked.safeRun(this.registryLock, () -> {
      final String triggerID = trigger.toTcpIpAppID();
      List<Runnable> listeners;
      if (!this.registry.containsKey(triggerID)) {
        this.registry.put(triggerID, new ArrayList<Runnable>());
      }
      this.registry.get(triggerID).add(listener);
    });
  }

  public void setBroadcastListener(BiConsumer<Remote, String> listener) { this.broadcastHandler = listener; }
  public void handleBroadcast(final Remote from, final String message) {
    if (this.broadcastHandler == null) {
      this.log.printf("no broadcast handlers, so dropping message '%s'\n", message);
      return;
    }

    this.broadcastHandler.accept(from, message);
  }

  private void notifyListenersUnsafe(final String remoteID, final String logTag) {
    this.log.printf(
        "notifying listeners of %s for '%s' [have: listeners=%s, default=%s]\n",
        logTag, remoteID, this.registry.containsKey(remoteID), this.defaultListener != null);
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
    this.receiptFIFOs.add(r.toTcpIpAppID(), new Message(r, message, true /*isReceived*/));
  }

  public void safeEnqueueSend(final Remote r, final String message) {
    this.sendingFIFOs.add(r.toTcpIpAppID(), new Message(r, message, false /*isReceived*/));
  }

  public LockedList<Message> getHistoryWith(final Remote remote) {
    return this.full.getNonEmpty(remote.toTcpIpAppID());
  }

  /** unsafe; calls should be wrapped in sendingLock.lock(). */
  protected void flushSends() {
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
            toSend.getRemote().toTcpIpAppID(),
            chatHist.size() - 1,
            toSend.getMessage());
      });

      RunLocked.safeRun(this.registryLock, () -> this.notifyListenersUnsafe(remoteID, "SENDS"));
    });
  }

  /** unsafe; calls should be wrapped in receiptLock.lock(). */
  protected void flushReceives() {
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

      RunLocked.safeRun(this.registryLock, () -> this.notifyListenersUnsafe(remoteID, "RECVS"));
    });
  }

  public void run() {
    this.isPlumbing = true;
    this.log.printf("spawned \"%s\": %s\n", TAG, Thread.currentThread().getName());

    new Timer().scheduleAtFixedRate(
        new HistoryTimer(this),
        0L /*delay*/,
        History.FLUSH_CYCLE_MILLIS /*period*/);
  }

  // Idempotent halter to the plumber's internal logic
  public void stopPlumber() { this.isPlumbing = false; }
}

class HistoryTimer extends TimerTask {
  private History h;
  public HistoryTimer(History h) { this.h = h; }
  @Override public void run() {
    if (!this.h.isPlumbing) {
      this.cancel();
      return;
    }

    this.h.flushSends();
    this.h.flushReceives();
  }
}
