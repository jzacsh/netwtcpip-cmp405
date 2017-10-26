import java.net.DatagramSocket;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

public class History implements Runnable {
  private static final long MAX_BLOCK_MILLIS = 25;
  private static final String TAG = "history thrd";
  private static final Logger log = new Logger(TAG);
  public final DatagramSocket source;

  public final Lock receiptLock;
  private Map<String, Queue<Message>> receiptFIFOs = null;

  public final Lock sendingLock;
  private Map<String, Queue<Message>> sendingFIFOs = null;

  private Thread plumber;
  private boolean isPlumbing = false;

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
    RunLocked.safeRun(this.receiptLock, () -> this.enqueueReceived(r, received));
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
    RunLocked.safeRun(this.sendingLock, () -> this.enqueueToSend(r, sending));
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

  /** unsafe; calls should be wrapped in sendingLock.lock(). */
  private void flushSends() {
    this.sendingFIFOs.forEach((final String remoteID, Queue<Message> q) -> {
      if (!this.isPlumbing) { return; }

      List<Message> chatHist = this.getNonEmptyRemoteHist(remoteID);
      Message toSend;
      while ((toSend = q.poll()) != null) {
        try {
          this.source.send(toSend.toPacket());
        } catch(Exception e) {
          this.log.errorf(e, "sending %s message %03d", toSend.getRemote(), chatHist.size() + 1);
          this.stopPlumber();
          return;
        }

        chatHist.add(toSend);
        this.log.debugf(
            "socket.send()d: %s message %03d: '%s'\n",
            toSend.getRemote(), chatHist.size(), toSend.getMessage());
      }
    });
  }

  /** unsafe; calls should be wrapped in receiptLock.lock(). */
  private void flushReceives() {
    this.receiptFIFOs.forEach((String remoteID, Queue<Message> q) -> {
      if (!this.isPlumbing) { return; }

      List<Message> chatHist = this.getNonEmptyRemoteHist(remoteID);
      while (!q.isEmpty()) {
        chatHist.add(q.poll());
        this.log.debugf(
            "saved message #%03d from %s: '%s'\n",
            chatHist.size() - 1,
            chatHist.get(chatHist.size() - 1).getRemote(),
            chatHist.get(chatHist.size() - 1).getMessage());
      }
    });
  }

  public void run() {
    while (this.isPlumbing) {
      RunLocked.safeRunTry(MAX_BLOCK_MILLIS, this.sendingLock, () -> this.flushSends());
      RunLocked.safeRunTry(MAX_BLOCK_MILLIS, this.receiptLock, () -> this.flushReceives());
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
