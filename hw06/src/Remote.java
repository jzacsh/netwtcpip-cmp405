import java.net.InetAddress;
import java.net.UnknownHostException;

public class Remote {
  private static final int UNCHECKED_PORT = -1;

  /** Either username or raw host address. */
  private String rawDest = null;
  private String rawPort = null;
  private boolean isViaUserName = false;

  /** Whether the next two fields are populated and valid. */
  private boolean isChecked = false;
  private InetAddress host = null;
  private int port = UNCHECKED_PORT;

  public Remote(final String username, final InetAddress host, int port) {
    this.isChecked = true;
    this.isViaUserName = true;
    this.rawDest = username;
    this.host = host;
    this.port = port;
  }

  public Remote(final InetAddress host, int port) {
    this.isChecked = true;
    this.isViaUserName = false;
    this.host = host;
    this.port = port;
  }

  private Remote(String port) {
    this.rawPort = port;
    this.isChecked = false;
  }

  public static Remote viaUncheckedAddress(String host, String port) {
    Remote unchecked = new Remote(port);
    unchecked.rawDest = host;
    unchecked.isViaUserName = false;
    return unchecked;
  }

  public static Remote viaUncheckedName(String username, String port) {
    Remote unchecked = new Remote(port);
    unchecked.rawDest = username;
    unchecked.isViaUserName = true;
    return unchecked;
  }

  /** Whether this is remote host started via just username resolution protocol. */
  public boolean isViaNameProtocol() { return this.isViaUserName; }

  public InetAddress getHost() { return this.host; }
  public int getPort() { return this.port; }

  public final String toString() {
    return String.format(
        "'%s':'%s':[is%s username service]",
        this.rawDest, this.rawPort,
        this.isViaNameProtocol() ? "" : " not");
  }

  public boolean checkAddress() throws Exception {
    if (this.isViaNameProtocol()) {
      throw new IllegalStateException("checkAddress() called for a user-name target");
    }

    if (this.isChecked()) {
      return this.isValid();
    }
    this.isChecked = true;

    InetAddress hostChecked = null;
    try {
      hostChecked = InetAddress.getByName(this.rawDest);
    } catch (UnknownHostException e) {
      throw new Error(String.format("could not resolve host '%s'", this.rawDest));
    }

    int portChecked;
    try {
      portChecked = Integer.parseUnsignedInt(this.rawPort);
    } catch (NumberFormatException e) {
      throw new Error(String.format("'%s' is not a valid port number", this.rawPort));
    }

    if (!AssertNetwork.isValidPort(portChecked)) {
      throw new Error(String.format("%d is an invalid port number", portChecked));
    }

    this.host = hostChecked;
    this.port = portChecked;
    return this.isValid();
  }

  public boolean isChecked() { return this.isChecked; }
  public boolean isValid() {
    return this.isChecked() && this.host != null && this.port != UNCHECKED_PORT;
  }
}
