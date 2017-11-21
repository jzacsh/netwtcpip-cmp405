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

  private boolean hasUserService;

  public RecvChannel(History hist, boolean isUserServicePossible) {
    this.hist = hist;
    this.hasUserService = isUserServicePossible;
  }

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

    this.log.printf("listening for messages on %s:%d\n",
        this.hist.source.getLocalAddress().getHostAddress(),
        this.hist.source.getLocalPort());
    long receiptIndex = 0;
    String message = null;
    Remote sender;
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

      message  = new String(
          inPacket.getData()
          /*, StandardCharsets.UTF_8: TODO possibly the '.' in getHostAddress() from other person's machine is funky?*/);
      sender = new Remote(inPacket.getAddress(), inPacket.getPort());

      this.log.printf(
          "handling received msg #%03d from %s (from: '%s') [%03d chars]: %s%s%s\n",
          receiptIndex - 1,
          inPacket.getAddress(),
          sender.toString(),
          message.length(), "\"\"\"", message, "\"\"\"");

      // Protocol, per in-class explanation, is: we treat *all* messages as
      // potential protocol-format. We don't care if it was broadcast.
      if (this.hasUserService && UsernameResolution.isProtocolCompliant(message)) {
        this.log.printf("handling as user-name protocol-message\n");
        this.hist.handleBroadcast(sender, message);
      } else {
        this.log.printf("handling as human-to-human message\n");
        this.hist.safeEnqueueReceived(sender, message);
      }

      for (int i = 0; i < inPacket.getLength(); ++i) { // erase any trace of usage
        inBuffer[i] = 0 /*default nil-value of a byte array*/;
      }
    }
  }
}
