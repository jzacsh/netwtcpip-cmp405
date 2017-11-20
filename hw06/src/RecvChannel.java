import java.net.DatagramPacket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

public class RecvChannel implements LocalChannel {
  public static final int SOCKET_WAIT_MILLIS = 30;
  private static final String TAG = "recv'r thrd";
  private static final Logger log = new Logger(TAG);
  private static final int MAX_RECEIVE_BYTES = 1000;

  private History hist;

  private boolean stopped = false;
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

  public void stopChannel() { this.stopped = true; }

  public boolean isActive() { return !this.stopped; }
  public boolean isFailed() { return !this.isOk; }

  private void fatalf(Exception e, String format, Object... args) {
    this.log.errorf(e, format, args);
    this.stopChannel();
    this.isOk = false;
  }

  /** non-blocking receiver that accepts packets on inSocket. */
  public void run() {
    this.stopped = false;
    this.log.printf(
        "spawned \"%s\" thread: %s\n",
        TAG, Thread.currentThread().getName());

    byte[] inBuffer = new byte[MAX_RECEIVE_BYTES];
    DatagramPacket inPacket = new DatagramPacket(inBuffer, inBuffer.length);

    try {
      this.hist.source.setSoTimeout(SOCKET_WAIT_MILLIS);
    } catch (SocketException e) {
      this.fatalf(e, "failed configuring socket timeout");
      return;
    }

    // Really only used in if username services is enabled, but doesn't hurt to
    // have this run in all modes.
    try {
      this.hist.source.setBroadcast(true);
      if (!this.hist.source.getBroadcast()) {
        this.log.errorf("failed enabling broadcasting");
        return;
      }
    } catch (SocketException e) {
      this.fatalf(e, "failed enabling broadcasting");
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

      message  = new String(inPacket.getData(), StandardCharsets.UTF_8);

      this.log.printf(
          "handling received %s #%03d [%03d chars]: %s%s%s\n",
          AssertNetwork.isBroadcast(inPacket.getAddress()) ? "message" : "broadcast",
          receiptIndex - 1,
          message.length(), "\"\"\"", message, "\"\"\"");

      if (AssertNetwork.isBroadcast(inPacket.getAddress())) {
        this.hist.handleBroadcast(new Remote(inPacket.getAddress(), inPacket.getPort()), message);
      } else {
        this.hist.safeEnqueueReceived(new Remote(inPacket.getAddress(), inPacket.getPort()), message);
      }

      for (int i = 0; i < inPacket.getLength(); ++i) { // erase any trace of usage
        inBuffer[i] = 0 /*default nil-value of a byte array*/;
      }
    }
  }
}
