import java.net.InetAddress;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class SendReceiveSocket {
  private static final String usageDoc = "RECEIPT_PORT DESTINATION_HOST DEST_PORT";
  private static final int outSourcePort = 63000;

  public static void main(String[] args) {
    final int expectedArgs = 3;
    if (args.length != expectedArgs) {
      System.err.printf(
          "Error: got %d argument(s), but expected %d...\nusage: %s\n",
          args.length, expectedArgs, usageDoc);
      System.exit(1);
    }

    final int receiptPort = BrittleNetwork.mustParsePort(args[0], "RECEIPT_PORT");
    final String destHostName = args[1].trim();
    final int destPort = BrittleNetwork.mustParsePort(args[2], "DEST_PORT");


    InetAddress destIP = null;
    try {
      destIP = InetAddress.getByName(destHostName);
    } catch (UnknownHostException e) {
      System.out.printf("[setup] failed resolving destination host '%s': %s\n", destHostName, e);
      System.exit(1);
    }
    System.out.printf("[setup] successfully resolved DESTINATION_HOST: %s\n", destIP);


    InetAddress receiptHost = null;
    try {
      receiptHost = InetAddress.getLocalHost();
    } catch (UnknownHostException e) {
      System.out.printf("[setup] failed finding current host address: %s\n", e);
      System.exit(1);
    }

    final DatagramSocket outSock = BrittleNetwork.mustOpenSocket(
        receiptHost, outSourcePort,
        "[setup] failed to open a sending socket [via %s] on port %d: %s\n");

    final DatagramSocket inSocket = BrittleNetwork.mustOpenSocket(
        receiptHost, receiptPort,
        "[setup] failed opening receiving socket on %s:%d: %s\n");
    System.out.printf("[setup] successfully resolved current host as: %s\n", receiptHost);

    System.out.printf("[setup] listener & sender setups complete.\n\n");
    new RecvClient(inSocket).listenInThread();
    new SendClient(destIP, destPort, outSock).sendMessagePerLine(new Scanner(System.in));
  }
}

class SendClient {
  private static final String LOG_TAG = "recv'r";
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

  public void sendMessagePerLine(Scanner ui) {
    DatagramPacket packet;
    String message;
    byte[] buffer = new byte[100];

    System.out.printf("[%s] usage instructions:\n%s", LOG_TAG, senderUXInstruction);
    boolean isPrevEmpty = false;
    int msgIndex = 0;
    while (true) {
      message = ui.nextLine().trim();
      if (message.length() == 0) {
        if (isPrevEmpty) {
          System.out.printf("[%s] caught two empty messages, exiting....\n", LOG_TAG);
          break;
        }
        isPrevEmpty = true;
        System.out.printf("[%s] press enter again to exit normally.\n", LOG_TAG);
        continue;
      }
      msgIndex++;

      buffer = message.getBytes();
      packet = new DatagramPacket(buffer, message.length(), destIP, destPort);

      System.out.printf("[%s] sending message #%03d: '%s'\n", LOG_TAG, msgIndex, message);
      try {
        this.socket.send(packet);
      } catch (Exception e) {
        System.err.printf("[%s] failed sending '%s':\n%s\n", LOG_TAG, message, e);
        System.exit(1);
      }
    }
  }
}

class RecvClient implements Runnable {
  private static final String LOG_TAG = "recv'r";

  DatagramSocket inSock = null;
  public RecvClient(DatagramSocket inSocket) {
    this.inSock = inSocket;
  }

  public void listenInThread() {
    Thread recvrThred = new Thread(this);
    recvrThred.setName("Receive Thread");
    recvrThred.start();
  }

  /** blocking receiver that accepts packets on inSocket. */
  public void run() {
    byte[] inBuffer = new byte[100];
    DatagramPacket inPacket = new DatagramPacket(inBuffer, inBuffer.length);

    int receiptIndex = 0;
    while (true) {
      for ( int i = 0 ; i < inBuffer.length ; i++ ) {
        inBuffer[i] = ' '; // TODO(zacsh) find out why fakhouri does this
      }

      System.out.printf("[%s] waiting for input...\n", LOG_TAG);
      try {
        this.inSock.receive(inPacket);
      } catch (Exception e) {
        System.err.printf("[%s] failed receiving packet %03d: %s\n", LOG_TAG, receiptIndex+1, e);
        System.exit(1);
      }
      receiptIndex++;

      System.out.printf(
          "[%s] received #%03d: %s\n%s\n%s\n",
          LOG_TAG, receiptIndex, "\"\"\"", "\"\"\"",
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
}
