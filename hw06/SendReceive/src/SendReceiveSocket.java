
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

  private static final String senderUXInstruction =
      "\tType messages & [enter] to send\n\t[enter] twice to exit.\n";

  /** blocking receiver that accepts packets on inSocket. */
  public static void receivePacketsSync(DatagramSocket inSocket) {
    byte[] inBuffer = new byte[100];
    DatagramPacket inPacket = new DatagramPacket(inBuffer, inBuffer.length);

    int receiptIndex = 0;
    while (true) {
      for ( int i = 0 ; i < inBuffer.length ; i++ ) {
        inBuffer[i] = ' '; // TODO(zacsh) find out why fakhouri does this
      }

      System.out.println("[recv'r] waiting for input...");
      try {
        inSocket.receive(inPacket);
      } catch (Exception e) {
        e.printStackTrace();
        System.exit(-1);
      }
      receiptIndex++;

      System.out.printf(
          "[recv'r] received #%03d: %s\n%s\n%s\n",
          receiptIndex, "\"\"\"", "\"\"\"",
          new String(inPacket.getData()));

    }
  }

  /**
   * failMessage should accept a host(%s), port (%d), and error (%s).
   */ // TODO(zacsh) see about java8's lambdas instead of failMessage's current API
  private static final DatagramSocket mustOpenSocket(
      final InetAddress host,
      final int port,
      final String failMessage) {
    DatagramSocket outSocket = null;
    try {
      outSocket = new DatagramSocket(port, host);
    } catch (SocketException e) {
      System.err.printf(failMessage, port, host, e);
      System.exit(1);
    }
    return outSocket;
  }

  private static final int mustParsePort(String portRaw, String label) {
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

  public static void main(String[] args) {
    final int expectedArgs = 3;
    if (args.length != expectedArgs) {
      System.err.printf(
          "Error: got %d argument(s), but expected %d...\nusage: %s\n",
          args.length, expectedArgs, usageDoc);
      System.exit(1);
    }

    final int receiptPort = mustParsePort(args[0], "RECEIPT_PORT");
    final String destHostName = args[1].trim();
    final int destPort = mustParsePort(args[2], "DEST_PORT");


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

    final DatagramSocket inSocket = mustOpenSocket(
        receiptHost, receiptPort,
        "[setup] failed opening receiving socket on %s:%d: %s\n");
    System.out.printf("[setup] successfully resolved current host as: %s\n", receiptHost);

    Thread receiveThread = new Thread(new Runnable () {
      public void run() {
        receivePacketsSync(inSocket);
      }
    });
    receiveThread.setName("Receive Thread");
    receiveThread.start();

    final DatagramSocket outSocket = mustOpenSocket(
        receiptHost, outSourcePort,
        "[setup] failed to open a sending socket [via %s] on port %d: %s\n");

    System.out.printf("[setup] listener & sender setups complete.\n\n");


    System.out.printf("[sender] usage instructions:\n%s", senderUXInstruction);
    boolean isPrevEmpty = false;
    String message;
    byte[] buffer = new byte[100];
    int msgIndex = 0;
    Scanner scnr = new Scanner(System.in);
    while (true) {
      message = scnr.nextLine().trim();
      if (message.length() == 0) {
        if (isPrevEmpty) {
          System.out.printf("[sender] caught two empty messages, exiting....\n");
          break;
        }

        isPrevEmpty = true;
        System.out.printf("[sender] press enter again to exit normally.\n");
      }
      msgIndex++;

      buffer = message.getBytes();
      DatagramPacket packet = new DatagramPacket(
          buffer, message.length(),
          destIP, destPort);

      System.out.printf("[sender] sending message #%03d: '%s'\n", msgIndex, message);
      try {
        outSocket.send(packet);
      } catch (Exception e) {
        System.err.printf("[sender] failed sending '%s':\n%s\n", message, e);
        System.exit(1);
      }
    }
  }
}
