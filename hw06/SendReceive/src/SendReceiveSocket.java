
import java.net.InetAddress;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.UnknownHostException;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class SendReceiveSocket {
  private static final String usageDoc = "DESTINATION_HOST DEST_PORT";

  private static InetAddress myAddress = null;

  public static void receiveMethod() {

    DatagramSocket inSocket = null;
    byte[] inBuffer = new byte[100];
    DatagramPacket inPacket = new DatagramPacket(inBuffer, inBuffer.length);


    try {
      inSocket = new DatagramSocket(64000, myAddress);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(-1);
    }

    do {
      for ( int i = 0 ; i < inBuffer.length ; i++ ) {
        inBuffer[i] = ' ';
      }

      try {
        // this thread will block in the receive call
        // until a message is received
        System.out.println("Waiting for input...");
        inSocket.receive(inPacket);
      } catch (Exception e) {
        e.printStackTrace();
        System.exit(-1);
      }

      String message = new String(inPacket.getData());
      System.out.println("Received message = " + message);

    } while(true);
  }

  public static void main(String[] args) {
    if (args.length != 2) {
      System.err.printf(
          "Error: got %d argument(s), but expected 2:\nusage: %s\n",
          args.length, usageDoc);
      System.exit(1);
    }

    final String destHostName = args[0];
    final int destPort = Integer.parseInt(args[1]); // eg: 64000
    if (destPort < 0 || destPort > 0xFFFF) {
      System.err.printf("DEST_PORT must be an unsigned 2-byte integer; got %d\n", destPort);
      System.exit(1);
    }


    InetAddress destIP = null;
    try {
      destIP = InetAddress.getByName(destHostName);
    } catch (UnknownHostException e) {
      System.out.printf("failed to find host at, '%s'\n%s\n", destHostName, e);
      System.exit(1);
    }
    System.out.printf("successfully resolved DESTINATION_HOST: %s\n", destIP);


    try {
      myAddress = InetAddress.getLocalHost();
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(-1);
    }

    System.out.println("My Address = " + myAddress.getHostAddress());

    DatagramSocket outSocket = null;

    try {
      outSocket = new DatagramSocket(63000, myAddress);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(-1);
    }

    Thread receiveThread = new Thread(new Runnable () {
      public void run() {
        receiveMethod();
      }
    });
    receiveThread.setName("Receive Thread");
    receiveThread.start();

    Scanner scnr = new Scanner(System.in);
    System.out.println("Start Sending? Press Enter...");
    scnr.nextLine();
    scnr.close();

    String prefix = "Message number ";
    byte[] buffer = new byte[100];

//  for ( int i = 1 ; i <= 10 ; i++ ) { // TODO remove this
      String message = "jon zacsh here\n";
      buffer = message.getBytes();

      try {
        DatagramPacket packet = new DatagramPacket(buffer,
           message.length(),
           destIP,
           destPort);

        System.out.println("Sending message = " + message);
        outSocket.send(packet);
        TimeUnit.SECONDS.sleep(5);
      } catch (Exception e) {
        e.printStackTrace();
        System.exit(-1);
      }
//  }

    try {
      TimeUnit.MINUTES.sleep(1);
    } catch (Exception e) {
      e.printStackTrace();
      System.exit(-1);
    }

    System.out.println("Main method exiting.... Bye Bye....");
    System.exit(0);
  }
}
