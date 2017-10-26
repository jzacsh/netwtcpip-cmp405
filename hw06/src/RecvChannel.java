import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.URLDecoder;
import java.net.SocketTimeoutException;
import java.io.UnsupportedEncodingException;

public class RecvChannel implements LocalChannel {
  public static final int SOCKET_WAIT_MILLIS = 30;
  private static final String TAG = "recv'r thrd";
  private static final Logger log = new Logger(TAG);
  private static final int MAX_RECEIVE_BYTES = 1000;

  private History hist;

  private boolean stopped = false;
  private Thread running = null;
  private boolean isOk = true;

  public RecvChannel(History hist) { this.hist = hist; }

  public RecvChannel setLogLevel(final Logger.Level lvl) {
    this.log.setLevel(lvl);
    return this;
  }

  public RecvChannel report() {
    this.log.printf(
        "READY to spawn thread consuming from local socket %s\n",
        this.hist.source.getLocalSocketAddress());
    return this;
  }

  public RecvChannel startChannel() {
    this.stopped = false;
    this.running = new Thread(this);
    this.running.setName(TAG);
    this.running.start();
    this.log.printf("spawned \"%s\" thread: %s\n", TAG, this.running);
    return this;
  }

  public Thread stop() {
    this.stopped = true;
    return this.running;
  }

  public boolean isActive() { return !this.stopped; }
  public boolean isFailed() { return !this.isOk; }
  public Thread thread() { return this.running; }

  private void fatalf(Exception e, String format, Object... args) {
    this.log.errorf(e, format, args);
    this.stop();
    this.isOk = false;
  }

  /** non-blocking receiver that accepts packets on inSocket. */
  public void run() {
    byte[] inBuffer = new byte[MAX_RECEIVE_BYTES];
    DatagramPacket inPacket = new DatagramPacket(inBuffer, inBuffer.length);

    try {
      this.hist.source.setSoTimeout(SOCKET_WAIT_MILLIS);
    } catch (SocketException e) {
      this.fatalf(e, "failed configuring socket timeout");
      return;
    }

    this.log.printf("waiting for input...\n");
    long receiptIndex = 0;
    String message = null;
    while (true) {
      if (this.stopped) {
        break;
      }

      try {
        this.hist.source.receive(inPacket);
      } catch (SocketTimeoutException e) {
        continue; // expected exception; just continue from the top, to remain responsive.
      } catch (Exception e) {
        this.fatalf(e, "failed receiving packet %03d", receiptIndex);
        break;
      }
      receiptIndex++;

      // TODO(zacsh) add Logger#debugf to print rawMsg
      String rawMsg = new String(inPacket.getData());

      try {
        message = URLDecoder.decode(rawMsg, "UTF-8");
      } catch (UnsupportedEncodingException e) {
        this.fatalf(e, "protocol error: failed decoding message %03d", receiptIndex - 1);
        break;
      }

      this.log.printf(
          "enqueuing received #%03d [%03d chars]: %s%s%s\n",
          receiptIndex - 1, message.length(), "\"\"\"", message, "\"\"\"");

      this.hist.safeEnqueueReceived(new Remote(inPacket.getAddress(), inPacket.getPort()), message);

      for (int i = 0; i < inPacket.getLength(); ++i) {
        inBuffer[i] = ' '; // TODO(zacsh) find out why fakhouri does this
      }
    }
  }
}
