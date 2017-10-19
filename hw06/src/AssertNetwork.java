import java.net.InetAddress;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.UnknownHostException;

/** Fast-failing, program-exiting, loud, tiny utils. */
public class AssertNetwork {
  /**
   * failMessage should accept a host(%s), port (%d), and error (%s).
   */ // TODO(zacsh) see about java8's lambdas instead of failMessage's current API
  public static final DatagramSocket mustOpenSocket(final String failMessage) {
    DatagramSocket sock = null;
    try {
      sock = new DatagramSocket();
    } catch (SocketException e) {
      System.err.printf(failMessage, "[default]", "[default]", e);
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
