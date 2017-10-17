import java.net.InetAddress;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.lang.InterruptedException;
import java.io.PrintStream;
import java.io.StringWriter;
import java.io.PrintWriter;

public class SendReceiveSocket {
  private static final String usageDoc = "RECEIPT_HOST RECEIPT_PORT DESTINATION_HOST DEST_PORT";
  private static final int outSourcePort = 63000;

  public static void main(String[] args) {
    final int expectedArgs = 4;
    if (args.length != expectedArgs) {
      System.err.printf(
          "Error: got %d argument(s), but expected %d...\nusage: %s\n",
          args.length, expectedArgs, usageDoc);
      System.exit(1);
    }

    final String receiptHost = args[0].trim();
    final int receiptPort = BrittleNetwork.mustParsePort(args[1], "RECEIPT_PORT");
    final String destHostName = args[2].trim();
    final int destPort = BrittleNetwork.mustParsePort(args[3], "DEST_PORT");

    final InetAddress destAddr = BrittleNetwork.mustResolveHostName(
        destHostName, "[setup] failed resolving destination host '%s': %s\n");

    final InetAddress receiptAddr = BrittleNetwork.mustResolveHostName(
        "localhost", "[setup] failed finding %s address: %s\n");

    final DatagramSocket outSock = BrittleNetwork.mustOpenSocket(
        receiptAddr, outSourcePort,
        "[setup] failed to open a sending socket [via %s] on port %d: %s\n");

    final DatagramSocket inSocket = BrittleNetwork.mustOpenSocket(
        receiptAddr, receiptPort, "[setup] failed opening receiving socket on %s:%d: %s\n");

    System.out.printf("[setup] listener & sender setups complete.\n\n");

    RecvClient receiver = new RecvClient(inSocket)
        .setLogLevel(Logger.Level.DEBUG)
        .report(receiptAddr.toString())
        .listenInThread();

    SendClient sender = new SendClient(destAddr, destPort, outSock)
        .setLogLevel(Logger.Level.DEBUG)
        .report();
    boolean wasSendOk = sender.sendMessagePerLine(new Scanner(System.in));

    System.out.printf("\n...cleaning up\n");
    try {
      receiver.stop().join(RecvClient.SOCKET_WAIT_MILLIS * 2 /*millis*/);
    } catch(InterruptedException e) {
      System.err.printf("problem stopping receiver: %s\n", e);
    }

    if (!wasSendOk) {
      System.exit(1);
    }
  }
}

class Logger {
  public enum Level { DEBUG, INFO, WARN, ERROR }

  private final String tag;
  private final Level DEFAULT_LEVEL = Level.INFO;

  private Level lvl = DEFAULT_LEVEL;
  public Logger(final String tag) { this.tag = tag; }

  private void fprintf(PrintStream out, String fmt, Object... args) {
    out.printf(String.format("[%s] %s", this.tag, fmt), args);
  }

  public void errorf(String fmt, Object... args) {
    this.fprintf(System.err, String.format("ERROR: %s", fmt), args);
  }

  /**
   * NOTE: this method appends its own newline to the end of all logs.
   */
  public void errorf(Exception e, String fmt, Object... args) {
    String error;
    if (this.lvl == Level.DEBUG) {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      e.printStackTrace(pw);
      error = sw.toString();
    } else {
      error = e.toString();
    }
    this.errorf(String.format("%s: %s\n", fmt, error), args);
  }

  public void printf(String fmt, Object... args) {
    if (this.lvl == Level.ERROR) { return; } // ignore
    this.fprintf(System.out, fmt, args);
  }

  public void setLevel(final Logger.Level lvl) { this.lvl = lvl; }
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
    byte[] buffer = new byte[100];

    this.log.printf("usage instructions:\n%s", senderUXInstruction);
    boolean isOk = true;
    boolean isPrevEmpty = false;
    int msgIndex = 0;
    while (true) {
      message = ui.nextLine().trim();
      if (message.length() == 0) {
        if (isPrevEmpty) {
          this.log.printf("caught two empty messages, exiting.... ");
          break;
        }
        isPrevEmpty = true;
        this.log.printf("press enter again to exit normally.\n");
        continue;
      }
      msgIndex++;

      buffer = message.getBytes();
      packet = new DatagramPacket(buffer, message.length(), destIP, destPort);

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

  public RecvClient report(String hostName) {
    this.log.printf(
        "READY to spawn thread on %s, consuming from socket %s\n",
        hostName, this.inSock.getLocalSocketAddress());
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
    byte[] inBuffer = new byte[100];
    DatagramPacket inPacket = new DatagramPacket(inBuffer, inBuffer.length);

    try {
      this.inSock.setSoTimeout(SOCKET_WAIT_MILLIS);
    } catch (SocketException e) {
      this.log.errorf(e, "failed configuring socket timeout");
      this.stop();
      return;
    }

    this.log.printf("thread: waiting for input...\n");
    int receiptIndex = 0;
    while (true) {
      if (stopped) {
        return;
      }

      for (int i = 0; i < inBuffer.length; ++i) {
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
    }
  }
}

/** Fast-failing, program-exiting, loud, tiny utils. */
class BrittleNetwork {
  /**
   * failMessage should accept a host(%s), port (%d), and error (%s).
   */ // TODO(zacsh) see about java8's lambdas instead of failMessage's current API
  public static final DatagramSocket mustOpenSocket(
      final InetAddress host,
      final int port,
      final String failMessage) {
    DatagramSocket sock = null;
    try {
      sock = new DatagramSocket(port, host);
    } catch (SocketException e) {
      System.err.printf(failMessage, port, host, e);
      System.exit(1);
    }
    return sock;
  }

  public static final int mustParsePort(String portRaw, String label) {
    final String errContext = String.format("%s must be an unsigned 2-byte integer", label);

    int port = -1;
    try {
      port = Integer.parseInt(portRaw.trim());
    } catch (NumberFormatException e) {
      System.err.printf(errContext + ", but got: %s\n", e);
      System.exit(1);
    }

    if (port < 0 || port > 0xFFFF) {
      System.err.printf(errContext + ", but got %d\n", port);
      System.exit(1);
    }

    return port;
  }

  /**
   * failMessage should accept a hostname(%s), and error (%s).
   */ // TODO(zacsh) see about java8's lambdas instead of failMessage's current API
  public static final InetAddress mustResolveHostName(
      final String hostName,
      final String failMessage) {
    InetAddress addr = null;
    try {
      addr = InetAddress.getByName(hostName);
    } catch (UnknownHostException e) {
      System.err.printf(failMessage, hostName, e);
      System.exit(1);
    }
    return addr;
  }
}
