
import java.net.*;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class SendReceiveSocket {
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

    /*
    InetAddress appleAddress = null;

    try {
    appleAddress = InetAddress.getByName("apple.com");
    } catch (Exception e) {
    e.printStackTrace();
    System.exit(-1);
    }

    System.out.println("Apple Address = " + appleAddress.getHostAddress());
    */

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

    for ( int i = 1 ; i <= 10 ; i++ ) {
      String message = prefix + i;
      buffer = message.getBytes();

      try {
        DatagramPacket packet = new DatagramPacket(buffer,
           message.length(),
           myAddress,
           64000);

        System.out.println("Sending message = " + message);
        outSocket.send(packet);
        TimeUnit.SECONDS.sleep(5);
      } catch (Exception e) {
        e.printStackTrace();
        System.exit(-1);
      }
    }

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
