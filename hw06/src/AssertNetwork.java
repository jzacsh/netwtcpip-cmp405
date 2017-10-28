import java.net.InetAddress;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.function.Consumer;

/** Loud-failing, tiny utils. */
public class AssertNetwork {
  /* Maximum possible port number bound by field size: two bytes. */
  public static final int MAX_POSSIBLE_PORT = 0xFFFF;

  public static final DatagramSocket mustOpenSocket(Consumer<SocketException> failHandler) {
    return mustOpenSocket(-1 /*port*/, failHandler);
  }

  public static final DatagramSocket mustOpenSocket(int port, Consumer<SocketException> failHandler) {
    DatagramSocket sock = null;
    try {
      sock = port == -1 ? new DatagramSocket() : new DatagramSocket(port);
    } catch (SocketException e) {
      failHandler.accept(e);
    }
    return sock;
  }

  public static final int mustParsePort(
      String portRaw, String label, Consumer<String> failHandler) {
    final String errContext = String.format("%s must be an unsigned 2-byte integer", label);

    int port = -1;
    try {
      port = Integer.parseInt(portRaw.trim());
    } catch (NumberFormatException e) {
      failHandler.accept(String.format("%s, but got: %s\n", errContext, e));
    }

    if (!AssertNetwork.isValidPort(port)) {
      failHandler.accept(String.format("%s, but got %d\n", errContext, port));
    }

    return port;
  }

  public static final InetAddress mustResolveHostName(
      final String hostName,
      Consumer<UnknownHostException> failHandler) {
    InetAddress addr = null;
    try {
      addr = InetAddress.getByName(hostName);
    } catch (UnknownHostException e) {
      failHandler.accept(e);
    }
    return addr;
  }

  public static final boolean isValidPort(final int port) {
    return port > 0 && port <= MAX_POSSIBLE_PORT;
  }
}
