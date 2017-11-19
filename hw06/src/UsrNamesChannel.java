import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map;
import java.util.Map.Entry;
import java.util.HashMap;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.UnknownHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;

public class UsrNamesChannel implements LocalChannel {
  private static final String PROTOCOL_REQUEST_DELIMITER = "?????";
  private static final String PROTOCOL_DECLARATION_DELIMITER = "#####";

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
  private final DatagramPacket protocolIdentity;
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

    this.protocolIdentity = this.buildPacketFrom(
        UsrNamesChannel.buildProtocolIdentity(this.identity, this.log));
  }

  private static String buildProtocolIdentity(final String identity, final Logger log) {
    InetAddress localHost = null;
    try {
      localHost = InetAddress.getLocalHost();
    } catch (UnknownHostException e) {
      log.errorf(e, "failed determining local IP address");
      System.exit(1);
    }

    return String.format("%s %s %s %s",
        PROTOCOL_DECLARATION_DELIMITER, identity,
        PROTOCOL_DECLARATION_DELIMITER, localHost.getHostAddress());
  }

  /**
   * WARNING: calling this twice overwrites the last consumer; ie: used as the *unchecked* logic to
   * open a window will result in one window never resolving to a real chat (eg: local user launches
   * two distinct windows for a chat "whinnie the pooh", then finally we get whinnie's IP, and only
   * one window will be notified).
   */
  public void resolveName(String usrname, BiConsumer<Remote, Throwable> handler) {
    if (this.resolved.containsKey(usrname)) {
      handler.accept(this.resolved.get(usrname), null /*throwable*/);
      return;
    }

    final String request = String.format("%s %s", PROTOCOL_REQUEST_DELIMITER, usrname);
    try {
      this.namesSubscription.send(this.buildPacketFrom(request));
    } catch(IOException e) {
      handler.accept(null, e);
      return;
    }

    this.waitingOn.put(usrname, handler);
  }

  private DatagramPacket buildPacketFrom(final String src) {
    return new DatagramPacket(
        src.getBytes(StandardCharsets.UTF_8),
        src.length(),
        this.namesSubscription.getInetAddress(),
        this.namesSubscription.getLocalPort());
  }

  private static final Throwable badResolution(String userName, final String badResolution, Throwable e) {
    return new Throwable(String.format(
        "protocol error: got bad address, '%s' advertised for user '%s': %s",
        userName, badResolution, e));
  }

  /** Expects format: "????? LOCAL_USER" */
  private void handleRequest(final String protocolMessage, final InetAddress requestor) {
    if (!this.isRequestForCurrentUser(protocolMessage)) {
      return;
    }

    try {
      this.namesSubscription.send(this.protocolIdentity);
    } catch(IOException e) {
      this.log.errorf(e, "failed responding declaration request by '%s'", requestor.getCanonicalHostName());
      return;
    }
    this.log.printf("responded to identity request by '%s'\n", requestor.getCanonicalHostName());
  }

  private boolean isRequestForCurrentUser(final String request) {
    // Expects format: "????? this.identity"
    final String requestedUser = request.substring(
        PROTOCOL_REQUEST_DELIMITER.length() + 1 /*single space*/);
    return request.equals(this.identity);
  }

  /**
   * Expected format: "##### name of person ##### ww.xx.yy.zz"
   */
  // NOTE: doesn't *actualy* iterate over codepoints as it should (despite decoding as UTF string);
  // could certainly be fixed
  private static Entry<String, String> parseNameResolution(final String src) throws ParseException {
    if (!UsrNamesChannel.isMaybeDeclaration(src)) {
      throw new ParseException("got implausible message length & first bytes", 0);
    }

    int addrDelimEndsAt = -1;
    int delimTracking = -1;
    for (int i = 0; i < src.length(); ++i) {
      if (i >= src.length() - 1) {
        throw new ParseException("encountered EOF-decl before finding address", 0);
      }

      if (i < PROTOCOL_DECLARATION_DELIMITER.length()) {
        if (src.charAt(i) != PROTOCOL_DECLARATION_DELIMITER.charAt(i)) {
          throw new ParseException("expected analogous delimiter char", i);
        }
        continue;
      } else if (i == PROTOCOL_DECLARATION_DELIMITER.length()) {
        if (src.charAt(i) != ' ') {
          throw new ParseException("expected post-delimiter(#1) space at col %d", i);
        }
      } else {
        if (delimTracking == -1) {
          if (src.charAt(i) == PROTOCOL_DECLARATION_DELIMITER.charAt(0)) {
            delimTracking++;
          }
        } else {
          if (src.charAt(i) == PROTOCOL_DECLARATION_DELIMITER.charAt(delimTracking+1)) {
            delimTracking++;
            if (delimTracking == PROTOCOL_DECLARATION_DELIMITER.length() - 1) {
              addrDelimEndsAt = i;
              if (src.charAt(i + 1) != ' ') {
                throw new ParseException("expected post-delimiter(#2) space at col %d", i+1);
              }
              break;
            }
          } else {
            delimTracking = -1;
          }
        }
      }
    }

    if (addrDelimEndsAt == -1) {
      throw new ParseException("no hostname found", src.length() - 1);
    }

    final int usernameLeftBound = PROTOCOL_DECLARATION_DELIMITER.length()+1/*include space*/;
    final int usernameRightBound = addrDelimEndsAt - PROTOCOL_DECLARATION_DELIMITER.length();
    if (usernameLeftBound >= usernameRightBound) {
      throw new ParseException("no username found", addrDelimEndsAt);
    }
    final String usernameRaw = src.substring(
        usernameLeftBound,
        usernameRightBound);
    final String username = usernameRaw.trim();
    if (username.length() == 0) {
      throw new ParseException("empty username", usernameRightBound);
    }
    final String addrRaw = src.substring(addrDelimEndsAt + 2 /*+1 for space*/);
    final String addr = addrRaw.trim();
    if (addr.length() == 0) {
      throw new ParseException("empty address", src.length() - 1);
    }
    return new SimpleEntry<>(username, addr);
  }

  /** Poor man's unit tests. */
  public static void main(String[] args) {
    Entry<String, String> actual;
    String[][] gold = new String[][]{
      {"##### walrus koo koo ##### 1.2.3.4", "walrus koo koo", "1.2.3.4"},
      {"##### wal-rus ##### walrus.lan", "wal-rus", "walrus.lan"},
      {"##### 192.168.11.111 ##### walrus.lan", "192.168.11.111", "walrus.lan"},
      {"##### k ##### 192.168.11.111", "k", "192.168.11.111"}
    };

    int fails = 0;
    for (int i = 0; i < gold.length; ++i) {
      try {
        actual = parseNameResolution(gold[i][0]);
      } catch (Throwable e) {
        System.err.printf(
            "FAIL[1:% 3d]: '%s' -> ('%s', '%s') but got exception:\n\t",
            i, gold[i][0], gold[i][1], gold[i][2]);
        e.printStackTrace(System.err);
        fails++;
        continue;
      }

      if (!actual.getKey().equals(gold[i][1]) ||
          !actual.getValue().equals(gold[i][2])) {
        System.err.printf(
            "FAIL[1:% 3d]: '%s' -> ('%s', '%s') but got ('%s', '%s')\n",
            i, gold[i][0], gold[i][1], gold[i][2],
            actual.getKey(), actual.getValue());
        fails++;
        continue;
      }
      System.err.printf(
          "PASS[1:% 3d]: '%s' -> ('%s', '%s')\n",
          i, gold[i][0], gold[i][1], gold[i][2]);
    }

    String[] coal = new String[]{
      "", "#####", "192.168.11.111 #####",
      "#####\tk ##### 192.168.11.111",
      "##### 192.168.11.111",
      "#####   ##### 192.168.11.111",
      "#####     #####     ",
      "##### ##### 192.168.11.111",
      "##### k #### 192.168.11.111",
      "##### k##### 192.168.11.111",
      "##### k #####192.168.11.111",
      "##### k ##### ",
      "#### k ##### 192.168.11.111",
      "#####k ##### 192.168.11.111",
      "##### ##### 192.168.11.111",
      "########## 192.168.11.111",
    };
    for (int i = 0; i < coal.length; ++i) {
      try {
        actual = parseNameResolution(coal[i]);

        fails++;
        System.err.printf(
            "FAIL[2:% 3d]: invalid '%s' not caught; parsed as ('%s', '%s')\n",
            i, coal[i], actual.getKey(), actual.getValue());
      } catch (ParseException expected) {
        System.err.printf(
            "PASS[2:% 3d]: invalid '%s' caught with: '%s'\n",
            i, coal[i], expected.getMessage());
      } catch (Throwable unexpected) {
        fails++;
        System.err.printf(
            "FAIL[2:% 3d]: unexpected exception for '%s':\n\t",
            i, coal[i]);
        unexpected.printStackTrace(System.err);
      }
    }

    final int totalTestLen = gold.length + coal.length;
    if (fails == 0) {
      System.err.printf("PASS: all %d tests passed\n", totalTestLen);
      System.exit(0);
    }
    System.err.printf("FAIL: %d of %d tests failed\n", fails, totalTestLen);
    System.exit(1);
  }

  private void handleResolution(final String protocolMessage) {
    final Entry<String, String> declaration;
    try {
      declaration = parseNameResolution(protocolMessage);
    } catch (ParseException e) {
      this.log.errorf(e, "protocol parsing error");
      return;
    }

    final String userName = declaration.getKey();
    final String rawResolution = declaration.getValue();
    this.handleResolution(userName, rawResolution);
  }

  private void handleResolution(final String userName, final String rawResolution) {
    BiConsumer<Remote, Throwable> deliverTo = this.waitingOn.get(userName);
    this.waitingOn.remove(userName); // only a one-time notification api

    this.log.printf("resolved user '%s' to raw IP address '%s'\n", userName, rawResolution);
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

    this.log.printf("waiting for name-resolution multi-casts...\n");
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
          "parsing declaration by %s:%d #%03d [%03d chars]: %s%s%s\n",
          inPacket.getAddress().getHostAddress(), inPacket.getPort(),
          receiptIndex - 1, message.length(), "\"\"\"", message, "\"\"\"");

      if (!UsrNamesChannel.isProtocolCompliant(message)) {
        this.log.printf("dropping message %d, is not protocol-compliant request or response\n", receiptIndex - 1);
      } else if (UsrNamesChannel.isMaybeDeclaration(message)) {
        this.handleResolution(message);
      } else if (UsrNamesChannel.isMaybeRequest(message)) {
        this.handleRequest(message, inPacket.getAddress());
      } else {
        this.log.errorf(
            "CRITICAL BUG: compliance-logic updated but parsing logic not, triggered on message %d!\n",
            receiptIndex - 1);
      }

      for (int i = 0; i < inPacket.getLength(); ++i) { // erase any trace of usage
        inBuffer[i] = 0 /*default nil-value of a byte array*/;
      }
    }
  }

  private static boolean isProtocolCompliant(String message) {
    return isMaybeRequest(message) || isMaybeDeclaration(message);
  }

  private static boolean isMaybeRequest(String message) {
    return (
        message.length() > PROTOCOL_REQUEST_DELIMITER.length() + 1 /*1 space*/ &&
        message.codePointAt(0) == PROTOCOL_REQUEST_DELIMITER.codePointAt(0)
    );
  }
  private static boolean isMaybeDeclaration(String msg) {
    return (
      msg.length() > PROTOCOL_DECLARATION_DELIMITER.length() * 2 + 3 /*spaces*/ + 2 /*username + hostname*/ &&
      msg.codePointAt(0) == PROTOCOL_DECLARATION_DELIMITER.codePointAt(0)
    );
  }

  public UsrNamesChannel setLogLevel(final Logger.Level lvl) {
    this.log.setLevel(lvl);
    return this;
  }
  public void stopChannel() { this.isAlive = false; }
  public boolean isActive() { return this.isAlive; }
  public boolean isFailed() { return this.isFailed; }
}
