import java.net.InetAddress;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.NoSuchElementException;
import java.util.Scanner;
import java.lang.InterruptedException;

public class Chatterbox {
  private static final Logger.Level LOG_LEVEL = Logger.Level.DEBUG;

  private RecvClient receiver = null;
  private SendClient sender = null;
  public Chatterbox(final String destHostName, final int destPort) {
    final DatagramSocket outSock = AssertNetwork.mustOpenSocket(
        "[setup] failed opening socket to send [via %s] from port %d: %s\n");
    // TODO(zacsh) refactor split into SendClient and ReceiveClient, and just have single-looper
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

    this.receiver = new RecvClient(inSocket).setLogLevel(LOG_LEVEL);
    this.sender = new SendClient(destAddr, destPort, outSock).setLogLevel(LOG_LEVEL);
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
    Chatterbox sendRecvClient = Chatterbox.parseFromCli(args);

    sendRecvClient.receiver.report().listenInThread();
    sendRecvClient.sender.report();
    boolean wasSendOk = sendRecvClient.sender.sendMessagePerLine(new Scanner(System.in));

    System.out.printf("\n...cleaning up\n");
    try {
      sendRecvClient.receiver.stop().join(RecvClient.SOCKET_WAIT_MILLIS * 2 /*millis*/);
    } catch(InterruptedException e) {
      System.err.printf("problem stopping receiver: %s\n", e);
    }

    if (!wasSendOk) {
      System.exit(1);
    }
  }
}

// TODO(zacsh) refactor to have both [Foo]Client classes "implement"
// `ClientChannel` that demands a report()

class SendClient {
  private static final Logger log = new Logger("sender");
  private static final String senderUXInstruction =
      "\tType messages & [enter] to send\n\t[enter] twice to exit.\n";

  private InetAddress destIP;
  private int destPort;
  private DatagramSocket socket = null;
  public SendClient(final InetAddress destIP, final int destPort, DatagramSocket outSock) {
    this.destIP = destIP;
    this.destPort = destPort;
    this.socket = outSock;
  }

  public SendClient setLogLevel(final Logger.Level lvl) {
    this.log.setLevel(lvl);
    return this;
  }

  public SendClient report() {
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

class RecvClient implements Runnable {
  public static final int SOCKET_WAIT_MILLIS = 5;
  private static final Logger log = new Logger("recv'r");
  private static final int MAX_RECEIVE_BYTES = 1000;

  boolean stopped = false;
  Thread running = null;
  DatagramSocket inSock = null;
  public RecvClient(DatagramSocket inSocket) {
    this.inSock = inSocket;
  }

  public RecvClient setLogLevel(final Logger.Level lvl) {
    this.log.setLevel(lvl);
    return this;
  }

  public RecvClient report() {
    this.log.printf(
        "READY to spawn thread consuming from local socket %s\n",
        this.inSock.getLocalSocketAddress());
    return this;
  }

  public RecvClient listenInThread() {
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
