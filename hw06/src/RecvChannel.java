import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

public class RecvChannel implements LocalChannel {
  public static final int SOCKET_WAIT_MILLIS = 5;
  private static final Logger log = new Logger("recv'r");
  private static final int MAX_RECEIVE_BYTES = 1000;

  boolean stopped = false;
  Thread running = null;
  DatagramSocket inSock = null;
  public RecvChannel(DatagramSocket inSocket) {
    this.inSock = inSocket;
  }

  public RecvChannel setLogLevel(final Logger.Level lvl) {
    this.log.setLevel(lvl);
    return this;
  }

  public RecvChannel report() {
    this.log.printf(
        "READY to spawn thread consuming from local socket %s\n",
        this.inSock.getLocalSocketAddress());
    return this;
  }

  public RecvChannel listenInThread() {
    this.log.printf("spawning receiver thread... ");
    this.running = new Thread(this);
    this.running.setName("Receive Thread");
    this.running.start();
    System.out.printf("Done.\n");
    return this;
  }

  public Thread stop() {
    this.stopped = true;
    return this.running;
  }

  /** non-blocking receiver that accepts packets on inSocket. */
  public void run() {
    byte[] inBuffer = new byte[MAX_RECEIVE_BYTES];
    DatagramPacket inPacket = new DatagramPacket(inBuffer, inBuffer.length);

    try {
      this.inSock.setSoTimeout(SOCKET_WAIT_MILLIS);
    } catch (SocketException e) {
      this.log.errorf(e, "failed configuring socket timeout");
      this.stop();
      return;
    }

    this.log.printf("thread: waiting for input...\n");
    long receiptIndex = 0;
    int lenLastRecvd = inBuffer.length;
    while (true) {
      if (stopped) {
        return;
      }

      for (int i = 0; i < lenLastRecvd; ++i) {
        inBuffer[i] = ' '; // TODO(zacsh) find out why fakhouri does this
      }

      try {
        this.inSock.receive(inPacket);
      } catch (SocketTimeoutException e) {
        continue; // expected exception; just continue from the top, to remain responsive.
      } catch (Exception e) {
        this.log.errorf(e, ":thread failed receiving packet %03d", receiptIndex+1);
        System.exit(1);
      }
      receiptIndex++;

      this.log.printf(
          "thread: received #%03d: %s\n%s\n%s\n",
          receiptIndex, "\"\"\"", "\"\"\"",
          new String(inPacket.getData()));
      lenLastRecvd = inPacket.getLength();
    }
  }
}
