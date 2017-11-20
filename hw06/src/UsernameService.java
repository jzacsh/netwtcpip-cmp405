import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.Map;
import java.util.HashMap;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map.Entry;

public class UsernameService {
  /**
   * Likely maximimum number of unique user resolutions we'll see in a runtime
   * Based on the lecture hall's size being about 30 students.
   */
  private static final int LIKELY_MAX_USERS = 100;

  private static final String TAG = "usrname reslv'r thrd";
  private static final Logger log = new Logger(TAG);
  private static final int MAX_RECEIVE_BYTES = 1000;

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
  private int globalRemotePort;

  private final DatagramSocket broadcastTo;

  private long receiptIndex;
  public UsernameService(String identity, final DatagramSocket broadcastTo, int globalPort) {
    this.broadcastTo = broadcastTo;
    this.receiptIndex = 0;
    this.identity = identity;
    this.resolved = new HashMap<>(LIKELY_MAX_USERS /*initialCapacity*/);
    this.waitingOn = new HashMap<>(LIKELY_MAX_USERS /*initialCapacity*/);
    this.globalRemotePort = globalPort;

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
      this.broadcastTo.send(this.buildPacketFrom(request));
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
        this.broadcastTo.getLocalPort());
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
      this.broadcastTo.send(this.declarationPacket);
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

    final Remote resolvedTo = new Remote(userName, addr, this.globalRemotePort);
    if (deliverTo != null) {
      deliverTo.accept(resolvedTo, null /*throwable*/);
    }
    this.resolved.put(userName, resolvedTo);
    this.log.printf("resolved user '%s' to raw IP address '%s'\n", userName, rawResolution);
  }

  public UsernameService report() {
    this.log.printf(
        "participating in username resolution protocol, as user='%s'\n",
        this.identity);
    return this;
  }

  public void broadcastHandler(final String message, final Remote from) {
    this.receiptIndex++;
    this.log.printf(
        "parsing broadcast #%03d by %s:%d [%03d chars]: %s%s%s\n",
        this.receiptIndex - 1, from.getHost().getHostAddress(), from.getPort(),
        message.length(), "\"\"\"", message, "\"\"\"");

    if (!UsernameResolution.isProtocolCompliant(message)) {
      this.log.printf(
          "dropping message %d, is not protocol-compliant request or response\n",
          this.receiptIndex - 1);
      return;
    }

    UsernameResolution protocol = new UsernameResolution(message);
    if (!protocol.parse()) {
      this.log.errorf(
          protocol.problem, "protocol parsing error with message %d",
          this.receiptIndex - 1);
    }

    if (protocol.isRequest()) {
      this.handleRequest(protocol, from.getHost());
    } else {
      this.handleResolution(protocol.user, protocol.destRaw);
    }
  }

  public UsernameService setLogLevel(final Logger.Level lvl) {
    this.log.setLevel(lvl);
    return this;
  }
}
