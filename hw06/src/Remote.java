import java.net.InetAddress;
import java.net.UnknownHostException;

public class Remote {
  private String userName = null;

  protected InetAddress host = null;
  protected int port = -1;

  public Remote(String userName, final InetAddress host, final int protocolPort) {
    this.userName = userName;
    this.host = host;
    this.port = protocolPort;
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

  public static Remote parseFrom(String hostRaw, String portRaw) throws Exception {
    InetAddress host = null;
    try {
      host = InetAddress.getByName(hostRaw);
    } catch (UnknownHostException e) {
      throw new Error(String.format("could not resolve host '%s'", hostRaw));
    }

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
}
