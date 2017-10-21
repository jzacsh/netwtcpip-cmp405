import java.net.InetAddress;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;

public class SendChannel implements LocalChannel {
  private static final String TAG = "sender thrd";
  private static final Logger log = new Logger(TAG);
  private static final String senderUXInstruction =
      "\tType messages & [enter] to send\n\t[enter] twice to exit.\n";

  private boolean isOk = true;
  private boolean stopped = false;

  private InetAddress destIP;
  private int destPort;
  private Scanner msgSrc;
  private DatagramSocket socket;
  private Thread running;
  public SendChannel(
      final Scanner src,
      final InetAddress destIP,
      final int destPort,
      DatagramSocket outSock) {
    this.msgSrc = src;
    this.destIP = destIP;
    this.destPort = destPort;
    this.socket = outSock;
  }

  public SendChannel setLogLevel(final Logger.Level lvl) {
    this.log.setLevel(lvl);
    return this;
  }

  public SendChannel start() {
    this.stopped = false;
    this.running = new Thread(this);
    this.running.setName(TAG);
    this.running.start();
    this.log.printf("spawned \"%s\" thread: %s\n", TAG, this.running);
    return this;
  }

  public SendChannel report() {
    this.log.printf(
        "READY to capture messages\n\tbound for %s on port %s\n\tvia socket: %s\n",
        this.destIP,
        this.destPort,
        this.socket.getLocalSocketAddress());
    return this;
  }

  public boolean isActive() { return !this.stopped; }
  public boolean isFailed() { return !this.isOk; }
  public Thread thread() { return this.running; }

  public Thread stop() {
    this.stopped = true;
    return this.running;
  }

  private void fatalf(Exception e, String format, Object... args) {
    this.log.errorf(e, format, args);
    this.stop();
    this.isOk = false;
  }

  public void run() {
    DatagramPacket packet;
    String rawMsg = null;
    String message = null;

    this.log.printf("usage instructions:\n%s", senderUXInstruction);
    boolean isPrevEmpty = false;
    long msgIndex = 0;
    while (true) {
      if (this.stopped) {
        break;
      }

      try {
        rawMsg = this.msgSrc.nextLine().trim();
        message = URLEncoder.encode(rawMsg, "UTF-8");
      } catch (NoSuchElementException e) {
        this.log.printf("caught EOF, exiting...\n");
        break;
      } catch (UnsupportedEncodingException e) {
        this.fatalf(e, "failed encoding message %03d '%s'", rawMsg);
        break;
      }

      if (message.length() == 0) {
        if (isPrevEmpty) {
          this.log.printf("caught two empty messages, exiting.... ");
          break;
        }
        isPrevEmpty = true;
        this.log.printf("press enter again to exit normally.\n");
        continue;
      }
      isPrevEmpty = false;
      msgIndex++;

      packet = new DatagramPacket(message.getBytes(), message.length(), destIP, destPort);

      this.log.printf("sending message #%03d: '%s'...", msgIndex, message);
      try {
        this.socket.send(packet);
        System.out.printf(" Done.\n");
      } catch (Exception e) {
        System.out.printf("\n");
        this.fatalf(e, "\nfailed sending '%s'", message);
        break;
      }
    }

    this.msgSrc.close();
  }
}
