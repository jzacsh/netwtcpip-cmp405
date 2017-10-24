import java.util.concurrent.locks.ReentrantLock;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.LinkedList;

public class History {
  public final ReentrantLock receiptLock;
  private Map<String, Queue<Message>> receiptFIFOs = null;

  public final ReentrantLock sendingLock;
  private Map<String, Queue<Message>> sendingFIFOs = null;

  public History() {
    this.receiptLock = new ReentrantLock();
    this.receiptFIFOs = new HashMap<String, Queue<Message>>();

    this.sendingLock = new ReentrantLock();
    this.sendingFIFOs = new HashMap<String, Queue<Message>>();
  }

  private static Queue<Message> getNonEmptyFIFO(Map<String, Queue<Message>> m, String key) {
    return m.containsKey(key) ? m.get(key) : new LinkedList<Message>();
  }

  /** unsafe; calls should be wrapped in receiptLock.lock(). */
  public void enqueueReceived(Remote r, Message received) {
    Queue<Message> receiving = getNonEmptyFIFO(this.receiptFIFOs, r.toString());
    receiving.add(received);
  }

  /** unsafe; calls should be wrapped in receiptLock.lock(). */
  public Queue<Message> getRecvQueue(Remote r) {
    return this.receiptFIFOs.get(r.toString());
  }

  /** unsafe; calls should be wrapped in sendingLock.lock(). */
  public void enqueueToSend(Remote r, Message sending) {
    Queue<Message> sendingQueue = getNonEmptyFIFO(this.sendingFIFOs, r.toString());
    sendingQueue.add(sending);
  }

  /** unsafe; calls should be wrapped in sendingLock.lock(). */
  public Queue<Message> getSendQueue(Remote r) {
    return this.sendingFIFOs.get(r.toString());
  }
}
