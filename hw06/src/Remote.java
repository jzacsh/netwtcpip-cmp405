import java.net.InetAddress;
import java.net.UnknownHostException;

public class Remote {
  /** Username resolution protocol dictates everyone use the same port. */
  private static final int DEFAULT_USERNAME_PROTOCOL_PORT = 64000;
  private String userName = null;

  protected InetAddress host = null;
  protected int port = -1;

  public Remote(String userName, final InetAddress host) {
    this.userName = userName;
    this.host = host;
    this.port = DEFAULT_USERNAME_PROTOCOL_PORT;
  }

  public Remote(final InetAddress host, int port) {
    this.host = host;
    this.port = port;
  }

  /** Whether this is remote host started via just username resolution protocol. */
  public boolean isViaNameProtocol() { return this.userName != null; }

  public InetAddress getHost() { return this.host; }
  public int getPort() { return this.port; }

  public final String toString() {
    return String.format("%s:%d", this.host.getHostAddress(), this.port);
  }

  private static InetAddress parseHost(String hostRaw) throws Exception {
    InetAddress host = null;
    try {
      host = InetAddress.getByName(hostRaw);
    } catch (UnknownHostException e) {
      throw new Error(String.format("could not resolve host '%s'", hostRaw));
    }
    return host;
  }

  public static Remote parseFrom(String hostRaw, String portRaw) throws Exception {
    InetAddress host = Remote.parseHost(hostRaw);
    int port;
    try {
      port = Integer.parseUnsignedInt(portRaw);
    } catch (NumberFormatException e) {
      throw new Error(String.format("'%s' is not a valid port number", portRaw));
    }

    if (!AssertNetwork.isValidPort(port)) {
      throw new Error(String.format("%d is an invalid port number", port));
    }
    return new Remote(host, port);
  }

  public static Remote parseFrom(String userName) throws Exception {
    String rawResolvedAddress = "192.168.8.111";
    /* TODO: replace ^ this with actual blocking work to find remote host via protocol */

    InetAddress resolvedAddress = null;
    try {
      resolvedAddress = Remote.parseHost(rawResolvedAddress);
    } catch (UnknownHostException e) {
      throw new Error("protocol error: %s", e);
    }

    return new Remote(userName, resolvedAddress);
  }
}
