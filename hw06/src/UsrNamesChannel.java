import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.Map;
import java.util.HashMap;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map.Entry;

public class UsrNamesChannel implements LocalChannel {
  private static final int MULTICAST_PORT = 64001;
  private static final String MULTICAST_HOST = "228.5.6.7";
  // TODO(zacsh) find out what professor wants us to use; above details aren't in homework
  // instruction

  /**
   * Likely maximimum number of unique user resolutions we'll see in a runtime
   * Based on the lecture hall's size being about 30 students.
   */
  private static final int LIKELY_MAX_USERS = 100;

  public static final int SOCKET_WAIT_MILLIS = 30;
  private static final String TAG = "usrname reslv'r thrd";
  private static final Logger log = new Logger(TAG);
  private static final int MAX_RECEIVE_BYTES = 1000;

  private MulticastSocket namesSubscription;

  private boolean isAlive = false;
  private boolean isFailed = false;

  /**
   * Map of consumers waiting for resolution on a given (keyed) username.
   */
  private Map<String, BiConsumer<Remote, Throwable>> waitingOn;

  /**
   * Map of resolved usernames using protocol described by homework 9 instructions.
   * - Keys are username strings multicast to expected IP Class D addresses.
   * - Values are the IP address, portnumber, and username info associated in the most recently
   *   procesesd multicase under given username.
   */
  private Map<String, Remote> resolved;

  private final String identity;
  private final DatagramPacket declarationPacket;
  private final InetAddress subscriptionAddr;
  private int defaultRemotePort;

  public UsrNamesChannel(String identity, int defaultPort) {
    this.namesSubscription = AssertNetwork.mustJoinMulticastGroup(MULTICAST_HOST, MULTICAST_PORT, (Throwable e) -> {
      this.log.errorf(e, "failed configuring multicast subscription (open socket & 'join')");
      System.exit(1);
    });

    this.isAlive = false;
    this.identity = identity;
    this.resolved = new HashMap<>(LIKELY_MAX_USERS /*initialCapacity*/);
    this.waitingOn = new HashMap<>(LIKELY_MAX_USERS /*initialCapacity*/);
    this.defaultRemotePort = defaultPort;

    Entry<InetAddress, String> results = UsernameResolution.mustBuildProtocolIdentity(this.identity, this.log);
    this.subscriptionAddr = results.getKey();
    this.declarationPacket = this.buildPacketFrom(results.getValue());
  }

  /**
   * WARNING: calling this twice overwrites the last consumer; ie: used as the *unchecked* logic to
   * open a window will result in one window never resolving to a real chat (eg: local user launches
   * two distinct windows for a chat "whinnie the pooh", then finally we get whinnie's IP, and only
   * one window will be notified).
   */
  public void resolveName(String usrname, BiConsumer<Remote, Throwable> handler) {
    if (this.identity.equals(usrname)) {
      handler.accept(null /*remote*/, new IllegalStateException("local user trying to ask group about themselves"));
      return;
    }

    if (this.resolved.containsKey(usrname)) {
      this.log.printf("utilizing cached resolution for username, '%s'\n", usrname);
      handler.accept(this.resolved.get(usrname), null /*throwable*/);
      return;
    }

    final String request = UsernameResolution.buildRequest(usrname);
    try {
      this.namesSubscription.send(this.buildPacketFrom(request));
    } catch(IOException e) {
      handler.accept(null /*remote*/, e);
      return;
    }

    this.waitingOn.put(usrname, handler);
    this.log.printf("broadcast request for username, '%s'\n", usrname);
  }

  private DatagramPacket buildPacketFrom(final String src) {
    return new DatagramPacket(
        src.getBytes(StandardCharsets.UTF_8),
        src.length(),
        this.subscriptionAddr,
        this.namesSubscription.getLocalPort());
  }

  private static final Throwable badResolution(String userName, final String badResolution, Throwable e) {
    return new Throwable(String.format(
        "protocol error: got bad address, '%s' advertised for user '%s': %s",
        badResolution, userName, e));
  }

  /** Expects format: "????? LOCAL_USER" */
  private void handleRequest(final UsernameResolution protocol, final InetAddress requestor) {
    if (!protocol.isRequestFor(this.identity)) {
      this.log.printf("dropping request for '%s' (ie: not local user)\n", protocol.user);
      return;
    }

    try {
      this.namesSubscription.send(this.declarationPacket);
    } catch(IOException e) {
      this.log.errorf(e, "failed responding declaration request by '%s'", requestor.getCanonicalHostName());
      return;
    }
    this.log.printf("responded to identity request by '%s'\n", requestor.getCanonicalHostName());
  }

  private void handleResolution(final String userName, final String rawResolution) {
    if (userName.equals(this.identity)) {
      this.log.printf("dropping (spoof?) declaration about current user being at '%s'\n", rawResolution);
      return;
    }

    BiConsumer<Remote, Throwable> deliverTo = this.waitingOn.get(userName);
    this.waitingOn.remove(userName); // only a one-time notification api

    final InetAddress addr = AssertNetwork.mustResolveHostName(rawResolution, (Throwable e) -> {
      this.log.errorf(badResolution(userName, rawResolution, e).toString());
    });

    if (addr == null) {
      if (deliverTo != null) {
        deliverTo.accept(null /*resolvesTo*/, badResolution(userName, rawResolution, null));
      }
      return; // explicitly do NOT store protocol-violating messages
    }

    final Remote resolvedTo = new Remote(userName, addr, this.defaultRemotePort);
    if (deliverTo != null) {
      deliverTo.accept(resolvedTo, null /*throwable*/);
    }
    this.resolved.put(userName, resolvedTo);
    this.log.printf("resolved user '%s' to raw IP address '%s'\n", userName, rawResolution);
  }

  public UsrNamesChannel report() {
    this.log.printf(
        "participating in username resolution protocol, as user='%s'\n",
        this.identity);
    return this;
  }

  private void fatalf(Exception e, String format, Object... args) {
    this.log.errorf(e, format, args);
    this.stopChannel();
    this.isFailed = true;
  }

  /** blocking receiver that accepts packets on inSocket. */
  public void run() {
    this.isAlive = true;
    this.log.printf( "spawned thread: %s\n", Thread.currentThread().getName());

    byte[] inBuffer = new byte[MAX_RECEIVE_BYTES];
    DatagramPacket inPacket = new DatagramPacket(inBuffer, inBuffer.length);

    try {
      this.namesSubscription.setSoTimeout(SOCKET_WAIT_MILLIS);
    } catch (SocketException e) {
      this.fatalf(e, "failed configuring socket timeout");
      return;
    }

    this.log.printf("listening for name-resolution multi-casts...\n");
    long receiptIndex = 0;
    String message = null;
    while (true) {
      if (!this.isAlive) {
        break;
      }

      try {
        this.namesSubscription.receive(inPacket);
      } catch (SocketTimeoutException e) {
        continue; // expected exception; just continue from the top, to remain responsive.
      } catch (Exception e) {
        this.fatalf(e, "failed receiving multi-cast #%03d", receiptIndex);
        System.exit(1); // critical failure; must halt entire app
        break;
      }
      receiptIndex++;

      message = new String(inPacket.getData(), StandardCharsets.UTF_8).trim();

      this.log.printf(
          "parsing broadcast #%03d by %s:%d [%03d chars]: %s%s%s\n",
          receiptIndex - 1, inPacket.getAddress().getHostAddress(), inPacket.getPort(),
          message.length(), "\"\"\"", message, "\"\"\"");

      if (UsernameResolution.isProtocolCompliant(message)) {
        UsernameResolution protocol = new UsernameResolution(message);
        if (!protocol.parse()) {
          this.log.errorf(protocol.problem, "protocol parsing error with message %d", receiptIndex - 1);
        }

        if (protocol.isRequest()) {
          this.handleRequest(protocol, inPacket.getAddress());
        } else {
          this.handleResolution(protocol.user, protocol.destRaw);
        }
      } else {
        this.log.printf("dropping message %d, is not protocol-compliant request or response\n", receiptIndex - 1);
      }

      for (int i = 0; i < inPacket.getLength(); ++i) { // erase any trace of usage
        inBuffer[i] = 0 /*default nil-value of a byte array*/;
      }
    }
  }

  public UsrNamesChannel setLogLevel(final Logger.Level lvl) {
    this.log.setLevel(lvl);
    return this;
  }
  public void stopChannel() { this.isAlive = false; }
  public boolean isActive() { return this.isAlive; }
  public boolean isFailed() { return this.isFailed; }
}
