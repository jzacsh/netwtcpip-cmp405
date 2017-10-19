import java.net.InetAddress;
import java.net.DatagramSocket;
import java.util.Scanner;
import java.lang.InterruptedException;

public class Chatterbox {
  private static final Logger log = new Logger("chatter");
  private static final Logger.Level LOG_LEVEL = Logger.Level.DEBUG;

  private RecvChannel receiver = null;
  private SendChannel sender = null;
  public Chatterbox(final java.io.InputStream messages, final String destHostName, final int destPort) {
    final DatagramSocket outSock = AssertNetwork.mustOpenSocket(
        "[setup] failed opening socket to send [via %s] from port %d: %s\n");
    // TODO(zacsh) refactor split into SendChannel and ReceiveClient, and just have single-looper
    // that golang-esque selects on the current situation:
    // - an outgoing message is ready, so send()
    // -- internal data struct api to enqueue currently composed, out-bound messages
    //    (eg: windowing/UI-thread should be able to pass a new message over to cause this select
    //    case to trigger (when the next selection happens in some SOCKET_WAIT_MILLIS milliseconds
    // - an we've spent SOCKET_WAIT_MILLIS receive()ing messages
    final DatagramSocket inSocket = AssertNetwork.mustOpenSocket(
        "[setup] failed opening receiving socket on %s:%d: %s\n");
    final InetAddress destAddr = AssertNetwork.mustResolveHostName(
        destHostName, "[setup] failed resolving destination host '%s': %s\n");

    this.receiver = new RecvChannel(inSocket).setLogLevel(LOG_LEVEL);
    this.sender = new SendChannel(
        new Scanner(messages),
        destAddr,
        destPort, outSock).setLogLevel(LOG_LEVEL);
    Chatterbox.log.printf("setup: listener & sender setups complete.\n\n");
  }

  private static Chatterbox parseFromCli(String[] args) {
    final String usageDoc = "DESTINATION_HOST DEST_PORT";
    final int expectedArgs = 2;
    if (args.length != expectedArgs) {
      Chatterbox.log.printf(
          "Error: got %d argument(s), but expected %d...\nusage: %s\n",
          args.length, expectedArgs, usageDoc);
      System.exit(1);
    }

    final String destHostName = args[0].trim();
    final int destPort = AssertNetwork.mustParsePort(args[1], "DEST_PORT");

    return new Chatterbox(System.in, destHostName, destPort);
  }

  public static void main(String[] args) {
    Chatterbox chatter = Chatterbox.parseFromCli(args);

    chatter.receiver.report().start();
    chatter.sender.report().start();
    try {
      chatter.sender.thread().join(); // block on send channel's own exit
    } catch(InterruptedException e) {
      Chatterbox.log.errorf(e, "failed waiting on sender thread\n");
    }

    System.out.printf("\n");
    Chatterbox.log.printf("cleaning up recvr thread\n");
    try {
      chatter.receiver.stop().join(RecvChannel.SOCKET_WAIT_MILLIS * 2 /*millis*/);
    } catch(InterruptedException e) {
      Chatterbox.log.errorf(e, "problem stopping receiver");
    }

    if (!chatter.sender.isFailed()) {
      System.exit(1);
    }
  }
}
