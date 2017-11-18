import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.InetAddress;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Scanner;
import java.lang.InterruptedException;
import java.io.InputStream;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Consumer;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class Chatterbox {
  private static final String CLI_USAGE =
      "[--help] [-v*] [ -1:1 DEST[:PORT] | -username LOCAL_USER]\n"
      + "\t-username LOCAL_USER sets the current username, for name-resolution services in homework 9 mode\n"
      + "\t-1:1 Optional CLI-mode for direct chat with one other host (unavailbale in homework 9 mode)";

  private static final boolean IS_ONEONE_MODE_DEFAULT = false; // per -1:1 CLI flag
  private static final boolean IS_HW6_MODE_DEFAULT = true; // negated only by presence of -username CLI flag
  private static final int DEFAULT_UDP_PORT = 64000;
  private static final int MAX_THREAD_GRACE_MILLIS = RecvChannel.SOCKET_WAIT_MILLIS * 2;
  private static final Logger log = new Logger("chatter");
  private static final Logger.Level DEFAULT_LOG_LEVEL = Logger.Level.INFO;

  private List<Future<?>> tasks;
  private ExecutorService execService;

  private History hist = null;
  private RecvChannel receiver = null;
  private OneToOneChannel sender = null;

  private boolean oneToOneMode = false;

  /**
   * Indicates the username to respond with while participating in the distributed user-name service
   * protocol of homework 9.
   */
  private final String userName;

  private Chatterbox() { this(DEFAULT_UDP_PORT, DEFAULT_LOG_LEVEL); }

  /** Construction for normal homework 6 mode. */
  public Chatterbox(int baselinePort, Logger.Level lvl) {
    this(baselinePort, lvl, null /*userName*/);
  }

  /** Construction for homework 9 mode. All parameters are required. */
  public Chatterbox(int baselinePort, Logger.Level lvl, String userName) {
    DatagramSocket sock = AssertNetwork.mustOpenSocket(baselinePort, (SocketException e) -> {
      this.log.errorf(e, "setup: failed opening receiving socket on %d", baselinePort);
      System.exit(1);
    });
    this.log.setLevel(lvl);
    this.hist = new History(sock).setLogLevel(lvl);
    this.receiver = new RecvChannel(this.hist).setLogLevel(lvl);
    this.userName = userName;
    this.execService = Executors.newWorkStealingPool();
    this.tasks = new ArrayList<Future<?>>();
  }

  /** Construction for homework 6 mode's One-on-One debugging feature (enabled via -1:1 CLI flag). */
  private Chatterbox(
      final InputStream messages,
      final String destHostName,
      final int baselinePort,
      final Logger.Level lvl) {
    this(baselinePort, lvl);
    this.oneToOneMode = true;

    final InetAddress destAddr = AssertNetwork
        .mustResolveHostName(destHostName, execService, (Throwable e) -> {
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
    boolean isOneOneMode = IS_ONEONE_MODE_DEFAULT;
    boolean isHw6Mode = IS_HW6_MODE_DEFAULT;

    String userName = null; // if !isHw6Mode, then we require current user's name

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
        case "-username":
          isHw6Mode = false;
          i++;
          if (i >= args.length) {
            System.err.printf("missing username param for -user flag\n");
            System.exit(1);
          }
          userName = args[i].trim();
          break;
        case "-1:1":
          isOneOneMode = true;
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

    if (isHw6Mode) {
      return isOneOneMode
          ? new Chatterbox(System.in, destHostName, destPort, cliVerbosity)
          : new Chatterbox(destPort, cliVerbosity);
    }

    if (isOneOneMode) {
      Chatterbox.log.errorf("-1:1 mode only available in combination with -hw6 mode\n");
      System.exit(1);
    }

    return new Chatterbox(destPort, cliVerbosity, userName);
  }

  /** Whether currently running with user-name resolution protocol (ie: in homework #9 mode). */
  private boolean isUserProtocol() { return this.userName != null; }

  public void report() {
    this.log.printf("Running in %s mode [%s]\n",
        this.isOneToOne() ? "one-to-one (CLI)" : "forum (GUI)",
        this.isUserProtocol() ?
          "username protocol, advertising '" + this.userName + "'"
          : "hw6 mode");

    this.receiver.report();
    if (this.isOneToOne()) {
      this.sender.report();
    }
  }

  public boolean teardown() { return this.teardown(null /*ev*/); }

  public boolean teardown(WindowEvent ev) {
    if (ev == null) {
      this.log.printf("handling manual shutdown\n");
    } else {
      this.log.printf("handling window closing event: %s\n", ev);
    }

    this.hist.stopPlumber();
    this.receiver.stopChannel();
    try {
      this.execService.shutdown();
      this.execService.awaitTermination(Chatterbox.MAX_THREAD_GRACE_MILLIS, TimeUnit.MILLISECONDS);
    } catch(InterruptedException e) {
      this.log.errorf(e, "problem stopping receiver");
      if (!this.isOneToOne()) {
        System.exit(1);
      }
      return true;
    }

    if (!this.isOneToOne()) {
      System.exit(0);
    }
    return false;
  }

  private boolean patientlyWaitFor(Future<?> task) {
    this.log.debugf("awaiting single task to exit normally...\n", this.tasks.size());
    try {
      task.get(); // patiently block on `task` ...
    } catch(ExecutionException e) {
      this.log.errorf(e, "found problem with main tasks");
    } catch(InterruptedException e) {
      this.log.errorf(e, "found one of main task already stopped");
    }
    this.log.debugf("task finally exited. killing remaining %d tasks...\n", this.tasks.size() - 1);
    return this.teardown();
  }

  public Future<?> startTask(Runnable r) {
    Future<?> f = this.execService.submit(r);
    this.tasks.add(f);
    return f;
  }

  public void launch() {
    this.report();

    this.startTask(this.receiver);
    this.startTask(this.hist);
    this.log.printf("children spawned, continuing with user task\n");
    if (this.isOneToOne()) {
      Future<?> senderTask = this.startTask(this.sender);
      System.exit(this.patientlyWaitFor(senderTask) ? 1 : 0);
    }

    // TODO(zacsh) fix to either:
    // 1) properly block on swing gui to exit
    // 2) or shutdown gui from here if this.receiver thread fails
    new ChatterJFrame("Chatterbox", DEFAULT_UDP_PORT, this.hist, this.isUserProtocol()).
        addWindowListener(new TeardownHandler((WindowEvent ev) -> this.teardown(ev)));
  }

  public static void main(String[] args) {
    Chatterbox.parseFromCli(args).launch();
  }
}

class TeardownHandler extends WindowAdapter {
  private Consumer<WindowEvent> handler;
  public TeardownHandler(Consumer<WindowEvent> handler) {
    super();
    this.handler = handler;
  }
  @Override public void windowClosing(WindowEvent e) { this.handler.accept(e); }
}
