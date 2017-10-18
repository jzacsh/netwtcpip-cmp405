import java.net.InetAddress;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.lang.InterruptedException;

public class Chatterbox {
  private static final Logger.Level LOG_LEVEL = Logger.Level.DEBUG;

  private RecvChannel receiver = null;
  private SendChannel sender = null;
  public Chatterbox(final String destHostName, final int destPort) {
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
    this.sender = new SendChannel(destAddr, destPort, outSock).setLogLevel(LOG_LEVEL);
    System.out.printf("[setup] listener & sender setups complete.\n\n");
  }

  private static Chatterbox parseFromCli(String[] args) {
    final String usageDoc = "DESTINATION_HOST DEST_PORT";
    final int expectedArgs = 2;
    if (args.length != expectedArgs) {
      System.err.printf(
          "Error: got %d argument(s), but expected %d...\nusage: %s\n",
          args.length, expectedArgs, usageDoc);
      System.exit(1);
    }

    final String destHostName = args[0].trim();
    final int destPort = AssertNetwork.mustParsePort(args[1], "DEST_PORT");

    return new Chatterbox(destHostName, destPort);
  }

  public static void main(String[] args) {
    Chatterbox sendRecvChannel = Chatterbox.parseFromCli(args);

    sendRecvChannel.receiver.report().listenInThread();
    sendRecvChannel.sender.report();
    boolean wasSendOk = sendRecvChannel.sender.sendMessagePerLine(new Scanner(System.in));

    System.out.printf("\n...cleaning up\n");
    try {
      sendRecvChannel.receiver.stop().join(RecvChannel.SOCKET_WAIT_MILLIS * 2 /*millis*/);
    } catch(InterruptedException e) {
      System.err.printf("problem stopping receiver: %s\n", e);
    }

    if (!wasSendOk) {
      System.exit(1);
    }
  }
}

// TODO(zacsh) refactor to have both [Foo]Client classes "implement" `ClientChannel` that demands a
// report(); eg: turn SendChannel#sendMessagePerLine into a Runnable#run() block of logic, and pass
// its requisite Scanner before hand.
// eg: see newly started LocalChannel.java
class SendChannel {
  private static final Logger log = new Logger("sender");
  private static final String senderUXInstruction =
      "\tType messages & [enter] to send\n\t[enter] twice to exit.\n";

  private InetAddress destIP;
  private int destPort;
  private DatagramSocket socket = null;
  public SendChannel(final InetAddress destIP, final int destPort, DatagramSocket outSock) {
    this.destIP = destIP;
    this.destPort = destPort;
    this.socket = outSock;
  }

  public SendChannel setLogLevel(final Logger.Level lvl) {
    this.log.setLevel(lvl);
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

  public boolean sendMessagePerLine(Scanner ui) {
    DatagramPacket packet;
    String message;

    this.log.printf("usage instructions:\n%s", senderUXInstruction);
    boolean isOk = true;
    boolean isPrevEmpty = false;
    long msgIndex = 0;
    while (true) {
      try {
        message = ui.nextLine().trim();
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

      packet = new DatagramPacket(message.getBytes(), message.length(), destIP, destPort);

      this.log.printf("sending message #%03d: '%s'...", msgIndex, message);
      try {
        this.socket.send(packet);
        System.out.printf(" Done.\n");
      } catch (Exception e) {
        System.out.printf("\n");
        this.log.errorf(e, "\nfailed sending '%s'", message);
        isOk = false;
        break;
      }
    }

    ui.close();
    return isOk;
  }
}
