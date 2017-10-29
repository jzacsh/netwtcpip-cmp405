import java.net.InetAddress;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;

public class OneToOneChannel implements LocalChannel {
  private static final String TAG = "1:1 thrd";
  private static final Logger log = new Logger(TAG);
  private static final String senderUXInstruction =
      "\tType messages & [enter] to send\n\t[enter] twice to exit.\n";

  private boolean isOk = true;
  private boolean stopped = false;

  private final Remote remote;
  private Scanner msgSrc;
  private History hist;
  private Thread running;

  public OneToOneChannel(final Scanner src, final Remote remote, History hist) {
    this.msgSrc = src;
    this.remote = remote;
    this.hist = hist;
  }

  public OneToOneChannel setLogLevel(final Logger.Level lvl) {
    this.log.setLevel(lvl);
    return this;
  }

  public OneToOneChannel startChannel() {
    this.stopped = false;
    this.running = new Thread(this);
    this.running.setName(TAG);
    this.running.start();
    this.log.printf("spawned \"%s\" thread: %s\n", TAG, this.running);
    return this;
  }

  public OneToOneChannel report() {
    this.log.printf("READY to capture messages\n\tbound for %s\n", this.remote.toString());
    return this;
  }

  public boolean isActive() { return !this.stopped; }
  public boolean isFailed() { return !this.isOk; }
  public Thread thread() { return this.running; }

  public Thread stopChannel() {
    this.stopped = true;
    return this.running;
  }

  private void fatalf(Exception e, String format, Object... args) {
    this.log.errorf(e, format, args);
    this.stopChannel();
    this.isOk = false;
  }

  public void run() {
    DatagramPacket packet;
    String message = null; // NOTE: no explicit consideration given to charset

    this.log.printf("CLI usage instructions:\n%s", senderUXInstruction);
    boolean isPrevEmpty = false;
    long msgIndex = 0;
    while (!this.stopped) {
      try {
        message = this.msgSrc.nextLine().trim();
      } catch (NoSuchElementException e) {
        this.log.printf("caught EOF, exiting...\n");
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

      this.hist.safeEnqueueSend(this.remote, message);
      this.log.printf("enqueued message #%03d: '%s'...\n", msgIndex, message);
    }

    this.msgSrc.close();
  }
}
