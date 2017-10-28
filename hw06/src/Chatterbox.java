import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.InetAddress;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Scanner;
import java.lang.InterruptedException;
import java.io.InputStream;

public class Chatterbox {
  private static final String CLI_USAGE =
      "[--help] [-v*] [ -1:1 DEST[:PORT] ]\n\t-1:1 is a CLI-mode for direct chat with one other host";

  private static final int DEFAULT_UDP_PORT = 6400;
  private static final int MAX_THREAD_GRACE_MILLIS = RecvChannel.SOCKET_WAIT_MILLIS * 2;
  private static final Logger log = new Logger("chatter");
  private static final Logger.Level DEFAULT_LOG_LEVEL = Logger.Level.INFO;

  private History hist = null;
  private RecvChannel receiver = null;
  private OneToOneChannel sender = null;

  private boolean oneToOneMode = false;

  public Chatterbox() { this(DEFAULT_UDP_PORT, DEFAULT_LOG_LEVEL); }

  public Chatterbox(int baselinePort, Logger.Level lvl) {
    DatagramSocket sock = AssertNetwork.mustOpenSocket(baselinePort, (SocketException e) -> {
      this.log.errorf(e, "setup: failed opening receiving socket on %d", baselinePort);
      System.exit(1);
    });
    this.hist = new History(sock).setLogLevel(lvl);
    this.receiver = new RecvChannel(this.hist).setLogLevel(lvl);
  }

  private Chatterbox(
      final InputStream messages,
      final String destHostName,
      final int baselinePort,
      final Logger.Level lvl) {
    this(baselinePort, lvl);
    this.oneToOneMode = true;

    final InetAddress destAddr = AssertNetwork
        .mustResolveHostName(destHostName, (UnknownHostException e) -> {
          this.log.errorf(e, "setup: failed resolving destination host '%s'", destHostName);
          System.exit(1);
        });

    this.sender = new OneToOneChannel(
        new Scanner(messages),
        new Remote(destAddr, baselinePort),
        this.hist);

    this.sender.setLogLevel(lvl);
    this.log.printf("setup: listener & sender setups complete.\n\n");
  }

  public boolean isOneToOne() { return this.oneToOneMode; }

  private static Chatterbox parseFromCli(String[] args) {
    if (args.length == 0) {
      return new Chatterbox();
    }

    String destHostName = null;
    Logger.Level cliVerbosity = Logger.Level.DEBUG;
    int destPort = DEFAULT_UDP_PORT;
    for (int i = 0; i < args.length; ++i) {
      switch (args[i]) {
        case "-h":
        case "-help":
        case "--help":
        case "help":
          System.out.printf("usage: %s\n", Chatterbox.CLI_USAGE);
          System.exit(0);
        case "-v":
          cliVerbosity = Logger.Level.INFO;
          break;
        case "-vv":
          cliVerbosity = Logger.Level.DEBUG;
          break;
        case "-1:1":
          i++;
          if (i >= args.length) {
            System.err.printf("missing parameter for flag -1:1 DEST[:PORT]\n");
            System.exit(1);
          }

          String[] hostPort = args[i].trim().split(":");
          destHostName = hostPort[0];
          if (hostPort.length > 1) {
            destPort = AssertNetwork.mustParsePort(hostPort[1], "DEST_PORT", (String err) -> {
              Chatterbox.log.errorf(err);
              System.exit(1);
            });
          }
          break;
        default:
          Chatterbox.log.errorf("unrecognized argument '%s'; see -h for usage\n", args[i]);
          System.exit(1);
      }
    }

    return destHostName == null
        ? new Chatterbox(destPort, cliVerbosity)
        : new Chatterbox(System.in, destHostName, destPort, cliVerbosity);
  }

  public void report() {
    this.log.printf(
        "Running in %s mode\n",
        this.isOneToOne() ? "one-to-one (CLI)" : "forum (GUI)");
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
      this.hist.stopPlumber().join(Chatterbox.MAX_THREAD_GRACE_MILLIS);
      this.receiver.stop().join(Chatterbox.MAX_THREAD_GRACE_MILLIS);
    } catch(InterruptedException e) {
      this.log.errorf(e, "problem stopping receiver");
      return true;
    }

    return false;
  }

  public static void main(String[] args) {
    Chatterbox chatter = Chatterbox.parseFromCli(args);
    chatter.report();

    chatter.receiver.report().startChannel();
    chatter.hist.startPlumber();

    chatter.log.printf("children spawned, continuing with user task\n");

    if (chatter.isOneToOne()) {
      System.exit(chatter.waitOnDirectText() | chatter.teardown() ? 1 : 0);
    }

    // TODO(zacsh) fix to either:
    // 1) properly block on swing gui to exit
    // 2) or shutdown gui from here if chatter.receiver thread fails
    new ChatterJFrame("Chatterbox", DEFAULT_UDP_PORT, chatter.hist)
        .addWindowListener(new WindowAdapter() {
          @Override public void windowClosing(WindowEvent e) {
            chatter.log.printf("handling window closing: %s\n", e);
            chatter.teardown();
          }
        });
  }
}
