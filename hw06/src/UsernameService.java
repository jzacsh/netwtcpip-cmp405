import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.Map;
import java.util.Map.Entry;
import java.util.AbstractMap.SimpleEntry;
import java.util.HashMap;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map.Entry;

public class UsernameService {
  /**
   * Likely maximimum number of unique user resolutions we'll see in a runtime
   * Based on the lecture hall's size being about 30 students.
   */
  private static final int LIKELY_MAX_USERS = 100;

  private static final String TAG = "username-r";
  private static final Logger log = new Logger(TAG);
  private static final int MAX_RECEIVE_BYTES = 1000;

  private static final Throwable ILLEGAL_EXISTENTIAL_STATE_EXCEPTION =
      new IllegalStateException("local user trying to ask group about themselves");

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
  private Map<String, Remote> addrByUser;

  /**
   * Reverse map of resolved IP addresses. Inverse of {@link #addrByUser} but keyed by
   * dotted-decimal notation string value of remote host. Port is ignored, as username-service
   * protocol dictates a hardcoded port.
   */
  private Map<String, String> userByAddr;

  private final String identity;
  private final String selfDeclaration;
  private final InetAddress localAddr;
  private final InetAddress broadcastAddr;
  private int globalRemotePort;

  private final DatagramSocket sock;

  private long receiptIndex;
  public UsernameService(String identity, final DatagramSocket sock, int globalPort) {
    this.sock = sock;
    this.receiptIndex = 0;
    this.identity = identity;

    this.addrByUser = new HashMap<>(LIKELY_MAX_USERS /*initialCapacity*/);
    this.userByAddr = new HashMap<>(LIKELY_MAX_USERS /*initialCapacity*/);
    this.waitingOn = new HashMap<>(LIKELY_MAX_USERS /*initialCapacity*/);
    this.globalRemotePort = globalPort;

    Entry<InetAddress, String> results = UsernameResolution.mustBuildProtocolIdentity(this.identity, this.log);
    this.localAddr = results.getKey();
    this.selfDeclaration = results.getValue();
    this.broadcastAddr = AssertNetwork.mustResolveHostName("255.255.255.255", (Throwable e) -> {
      this.log.errorf("failed to load broadcast address");
      System.exit(1);
    });
  }

  /**
   * WARNING: calling this twice overwrites the last consumer; ie: used as the *unchecked* logic to
   * open a window will result in one window never resolving to a real chat (eg: local user launches
   * two distinct windows for a chat "whinnie the pooh", then finally we get whinnie's IP, and only
   * one window will be notified).
   */
  public void resolveName(String usrname, BiConsumer<Remote, Throwable> handler) {
    if (this.identity.equals(usrname)) {
      handler.accept(null /*remote*/, ILLEGAL_EXISTENTIAL_STATE_EXCEPTION);
      return;
    }

    if (this.addrByUser.containsKey(usrname)) {
      this.log.printf("utilizing cached resolution for username, '%s'\n", usrname);
      handler.accept(this.addrByUser.get(usrname), null /*throwable*/);
      return;
    }

    final String request = UsernameResolution.buildRequest(usrname, this.identity);
    try {
      this.sock.send(this.buildPacketFrom(request, this.broadcastAddr));
    } catch(IOException e) {
      handler.accept(null /*remote*/, e);
      return;
    }

    this.waitingOn.put(usrname, handler);
    this.log.printf("broadcast request for username, '%s'\n", usrname);
  }

  private DatagramPacket buildPacketFrom(final String src, InetAddress to) {
    return new DatagramPacket(
        src.getBytes(StandardCharsets.UTF_8),
        src.length(),
        to, this.sock.getLocalPort());
  }

  private static final Throwable badResolution(String userName, final String badResolution, Throwable e) {
    return new Throwable(String.format(
        "protocol error: got bad address, '%s' advertised for user '%s': %s",
        badResolution, userName, e));
  }

  private void handleRequest(final UsernameResolution protocol, final InetAddress requestor) {
    if (!protocol.isRequestFor(this.identity)) {
      this.log.printf(
          "dropping request from '%s', for '%s' (not for local user='%s')\n",
          protocol.whoAsked, protocol.user, this.identity);
      // TODO: find out HOW this is broken and triggering even when
      // this.identity == protocol.user
      return;
    }

    this.log.printf(
        "storing requestor's identity ('%s', '%s') as an aside...\n",
        protocol.whoAsked, requestor.getHostAddress());
    this.handleResolution(protocol.whoAsked, requestor, requestor.getHostAddress());

    try {
      this.sock.send(this.buildPacketFrom(this.selfDeclaration, requestor));
    } catch(IOException e) {
      this.log.errorf(e, "failed responding declaration request by '%s'", requestor.getHostAddress());
      return;
    }
    this.log.printf(
        "responded to identity request by '%s' ('%s')\n",
        protocol.whoAsked, requestor.getHostAddress());
  }

  private void handleResolution(final String userName, final String rawResolution) {
    this.log.printf("verifying raw resolution IP, '%s'...\n", rawResolution);
    final InetAddress addr = AssertNetwork.mustResolveHostName(rawResolution, (Throwable e) -> {
      this.log.errorf(badResolution(userName, rawResolution, e).toString());
    });
    this.handleResolution(userName, addr, rawResolution);
  }

  private void handleResolution(final String userName, final InetAddress resolved, final String rawResolution) {
    if (userName.equals(this.identity)) {
      this.log.printf("dropping (spoof?) declaration about current user being at '%s'\n", rawResolution);
      return;
    }

    BiConsumer<Remote, Throwable> deliverTo = this.waitingOn.get(userName);
    this.waitingOn.remove(userName); // only a one-time notification api

    if (resolved == null) {
      if (deliverTo != null) {
        deliverTo.accept(null /*resolvesTo*/, badResolution(userName, rawResolution, null));
      }
      return; // explicitly do NOT store protocol-violating messages
    }

    final Remote resolvedTo = new Remote(userName, resolved, this.globalRemotePort);
    if (deliverTo != null) {
      deliverTo.accept(resolvedTo, null /*throwable*/);
    }
    this.storeNameResolution(userName, resolvedTo);
    this.log.printf(
        "resolved, stored, & notified %d api-listener(s) of (user,ip)=('%s','%s')\n",
        deliverTo == null ? 0 : 1,
        userName, rawResolution);
  }

  private void storeNameResolution(final String user, final Remote r) {
    this.addrByUser.put(user, r);
    this.userByAddr.put(r.getHost().getHostAddress(), user);
  }

  public Entry<String, Remote> getUserBehind(final InetAddress addr) {
    final String ipAddr = addr.getHostAddress();
    if (!this.userByAddr.containsKey(ipAddr)) {
      return null;
    }

    final String usr = this.userByAddr.get(ipAddr);
    return new SimpleEntry<>(usr, this.addrByUser.get(usr));
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
