import java.lang.InterruptedException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.DatagramSocket;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.ArrayList;
import java.util.List;

/** Loud-failing, tiny utils. */
public class AssertNetwork {
  public static final long MAX_HOST_RESOLUTION_MILLIS = 150L;

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

  public static boolean isBroadcast(final InetAddress packet) {
    final byte[] octets = packet.getAddress();
    return octets[0] == 255 &&
           octets[1] == 255 &&
           octets[2] == 255 &&
           octets[3] == 255;
  }

  public static final MulticastSocket mustJoinMulticastGroup(
      String addr, int port, Consumer<Throwable> failHandler) {
   InetAddress groupAddr = null;
   try {
     groupAddr = InetAddress.getByName(addr);
   } catch (UnknownHostException e) {
     failHandler.accept(e);
     return null;
   }

   MulticastSocket sock = null;
   try {
     sock = new MulticastSocket(port);
     sock.joinGroup(groupAddr);
   } catch (IOException e) {
     failHandler.accept(e);

     if (sock != null) {
       sock.close();
     }
     return null;
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
      Consumer<Throwable> failHandler) {
    InetAddress addr = null;
    try {
      addr = InetAddress.getByName(hostName);
    } catch (UnknownHostException e) {
      failHandler.accept(e);
    }
    return addr;
  }

  public static final InetAddress mustResolveHostName(
      final String hostName,
      ExecutorService threadPool,
      Consumer<Throwable> failHandler) {
    InetAddress resp = null;
    ArrayList<Callable<InetAddress>> resolvers = new ArrayList<>();
    resolvers.add(() -> mustResolveHostName(hostName, failHandler));
    try {
      List<Future<InetAddress>> results = threadPool.invokeAll(
          resolvers, MAX_HOST_RESOLUTION_MILLIS,
          TimeUnit.MILLISECONDS);
      resp = results.get(0).get();
    } catch (ExecutionException e) {
      failHandler.accept(e);
    } catch (InterruptedException e) {
      failHandler.accept(e);
    }
    return resp;
  }

  public static final boolean isValidPort(final int port) {
    return port > 0 && port <= MAX_POSSIBLE_PORT;
  }
}
