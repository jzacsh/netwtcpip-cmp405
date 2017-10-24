import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.InetAddress;
import java.net.DatagramSocket;
import java.util.Scanner;
import java.lang.InterruptedException;

public class Chatterbox {
  private static final int MAX_THREAD_GRACE_MILLIS = RecvChannel.SOCKET_WAIT_MILLIS * 2;
  private static boolean FORUM_MODE = true;
  private static final Logger log = new Logger("chatter");
  private static final Logger.Level LOG_LEVEL = Logger.Level.DEBUG;

  private History hist = null;
  private RecvChannel receiver = null;
  private SendChannel sender = null;

  public Chatterbox() {
    DatagramSocket sock = AssertNetwork.mustOpenSocket(
        "setup: failed opening receiving socket on %s:%d: %s\n");
    this.hist = new History(sock).setLogLevel(LOG_LEVEL);
    this.receiver = new RecvChannel(this.hist).setLogLevel(LOG_LEVEL);
  }

  public Chatterbox(
      final java.io.InputStream messages,
      final String destHostName,
      final int destPort) {
    this();
    if (FORUM_MODE) {
      throw new Error("only no-arg constructor designed for forum-mode");
    }
    final DatagramSocket outSock = AssertNetwork.mustOpenSocket(
        "setup: failed opening socket to send [via %s] from port %d: %s\n");
    // TODO(zacsh) refactor split into SendChannel and ReceiveClient, and just have single-looper
    // that golang-esque selects on the current situation:
    // - an outgoing message is ready, so send()
    // -- internal data struct api to enqueue currently composed, out-bound messages
    //    (eg: windowing/UI-thread should be able to pass a new message over to cause this select
    //    case to trigger (when the next selection happens in some SOCKET_WAIT_MILLIS milliseconds
    // - an we've spent SOCKET_WAIT_MILLIS receive()ing messages
    final InetAddress destAddr = AssertNetwork.mustResolveHostName(
        destHostName, "setup: failed resolving destination host '%s': %s\n");

    this.sender = new SendChannel(
        new Scanner(messages),
        destAddr,
        destPort, outSock).setLogLevel(LOG_LEVEL);
    this.log.printf("setup: listener & sender setups complete.\n\n");
  }

  private static Chatterbox parseFromCli(String[] args) {
    if (FORUM_MODE) {
      return new Chatterbox();
    }

    final String usageDoc = "DESTINATION_HOST DEST_PORT";
    final int expectedArgs = 2;
    if (args.length != expectedArgs) {
      Chatterbox.log.errorf(
          "got %d argument(s), but expected %d...\nusage: %s\n",
          args.length, expectedArgs, usageDoc);
      System.exit(1);
    }

    final String destHostName = args[0].trim();
    final int destPort = AssertNetwork.mustParsePort(args[1], "DEST_PORT");

    return new Chatterbox(System.in, destHostName, destPort);
  }

  public boolean waitOnDirectText() {
    try {
      // block on user interaction to exit
      this.sender.report().startChannel().thread().join();
    } catch(InterruptedException e) {
      this.log.errorf(e, "failed waiting on UX to exit\n");
      return true;
    }

    return this.sender.isFailed();
  }

  public boolean teardown() {
    try {
      this.receiver.stop().join(Chatterbox.MAX_THREAD_GRACE_MILLIS);
    } catch(InterruptedException e) {
      this.log.errorf(e, "problem stopping receiver");
      return false;
    }

    return true;
  }

  public static void main(String[] args) {
    Chatterbox chatter = Chatterbox.parseFromCli(args);

    chatter.receiver.report().startChannel();
    chatter.log.printf("children spawned, continuing with user task\n");

    if (!FORUM_MODE) {
      System.exit(chatter.waitOnDirectText() & chatter.teardown() ? 1 : 0);
    }

    // TODO(zacsh) fix to either:
    // 1) properly block on swing gui to exit
    // 2) or shutdown gui from here if chatter.receiver thread fails
    ChatterJFrame.startDisplay("Chatterbox", chatter.hist, new WindowAdapter() {
      @Override public void windowClosing(WindowEvent e) {
        chatter.log.printf("handling window closing: %s\n", e);
        chatter.teardown();
      }
    });
  }
}
