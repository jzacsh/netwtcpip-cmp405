import java.util.AbstractMap.SimpleEntry;
import java.util.Map.Entry;
import java.net.UnknownHostException;
import java.net.InetAddress;
import java.text.ParseException;

public class UsernameResolution {
  private static final String PROTOCOL_REQUEST_DELIMITER = "?????";
  private static final String PROTOCOL_DECLARATION_DELIMITER = "#####";

  /** Expected format: "????? USER ##### WHO_ASKED" */
  private static final int PROTOCOL_MIN_REQUEST_STR_LEN =
      PROTOCOL_REQUEST_DELIMITER.length()
      + PROTOCOL_DECLARATION_DELIMITER.length()
      + 3 /*spaces: 2 around USER, 1 before WHO_ASKED*/
      + 2 /*username + whoasked*/;

  /** Expected format: "##### USER ##### IP_ADDRESS" */
  private static final int PROTOCOL_MIN_DECLARATION_STR_LEN =
      PROTOCOL_DECLARATION_DELIMITER.length() * 2
      + 3 /*spaces: 2 around USER, 1 before IP_ADDRESS*/
      + 2 /*username + hostname*/;

  public final String src;
  public String user;
  public String destRaw;
  public InetAddress dest = null;
  public Throwable problem = null;

  public String whoAsked;

  private boolean isParsed;
  private boolean isValid;

  public UsernameResolution(final String message) {
    this.src = message;
    this.isParsed = false;
    this.isValid = false;
  }

  public boolean parse() {
    if (this.isParsed) {
      return this.isValid;
    }

    this.isParsed = true;
    this.isValid = false;
    if (!UsernameResolution.isProtocolCompliant(this.src)) {
      this.problem = new Exception("not a valid protocol message");
      return this.isValid;
    }

    if (UsernameResolution.isMaybeRequest(this.src)) {
      try {
        Entry<String, String> results = UsernameResolution.parseNameRequest(this.src);
        this.user = results.getKey();
        this.whoAsked = results.getValue();
        this.isValid = true;
      } catch (ParseException e) {
        this.problem = e;
      }
    } else if (UsernameResolution.isMaybeDeclaration(this.src)) {
      try {
        Entry<String, String> results = UsernameResolution.parseNameResolution(this.src);
        this.user = results.getKey();
        this.destRaw = results.getValue();
        this.isValid = true;
      } catch (ParseException e) {
        this.problem = e;
      }
    } else {
      System.err.printf(
          "CRITICAL BUG: compliance-logic updated but parsing logic not, triggered on message '%s'!\n",
          this.src);
    }
    return this.isValid;
  }

  public boolean isRequest() { return this.isParsed && this.isValid && this.destRaw == null; }

  public boolean isRequestFor(final String username) { return this.user.equals(username); }

  public static Entry<InetAddress, String> mustBuildProtocolIdentity(final String identity, final Logger log) {
    InetAddress localHost = null;
    try {
      localHost = InetAddress.getLocalHost();
    } catch (UnknownHostException e) {
      log.errorf(e, "failed determining local IP address");
      System.exit(1);
    }

    final String declaration = String.format("%s %s %s %s",
        PROTOCOL_DECLARATION_DELIMITER, identity,
        PROTOCOL_DECLARATION_DELIMITER, localHost.getHostAddress());

    return new SimpleEntry<>(localHost, declaration);
  }

  /** Expects format: "????? this.identity ##### whoAsked". */
  private static Entry<String, String> parseNameRequest(final String src) throws ParseException {
    final String seeking, whoAsked;

    String strippedOfPrefix = src.trim().substring(PROTOCOL_REQUEST_DELIMITER.length() + 1 /*single space*/);
    final int indexOfDelim = strippedOfPrefix.indexOf(PROTOCOL_DECLARATION_DELIMITER);

    seeking = strippedOfPrefix.substring(0, indexOfDelim -1);
    whoAsked = strippedOfPrefix.substring(
        indexOfDelim
        + PROTOCOL_DECLARATION_DELIMITER.length()
        + 1 /*single space*/);

    return new SimpleEntry<>(seeking, whoAsked);
  }

  /**
   * Expected format: "##### name of person ##### ww.xx.yy.zz"
   */
  // NOTE: doesn't *actualy* iterate over codepoints as it should (despite decoding as UTF string);
  // could certainly be fixed
  private static Entry<String, String> parseNameResolution(final String src) throws ParseException {
    if (!UsernameResolution.isMaybeDeclaration(src)) {
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
          throw new ParseException("expected post-delimiter(#1) space", i);
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
                throw new ParseException("expected post-delimiter(#2) space", i+1);
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

  public static String buildRequest(String usrname, String localUser) {
    return String.format(
        "%s %s %s %s",
        PROTOCOL_REQUEST_DELIMITER, usrname,
        PROTOCOL_DECLARATION_DELIMITER, localUser);
  }

  public static boolean isProtocolCompliant(String message) {
    return isMaybeRequest(message) || isMaybeDeclaration(message);
  }

  private static boolean isMaybeRequest(String message) {
    return (
      message.length() > PROTOCOL_MIN_REQUEST_STR_LEN &&
      message.codePointAt(0) == PROTOCOL_REQUEST_DELIMITER.codePointAt(0)
    );
  }

  private static boolean isMaybeDeclaration(String msg) {
    return (
      msg.length() > PROTOCOL_MIN_DECLARATION_STR_LEN &&
      msg.codePointAt(0) == PROTOCOL_DECLARATION_DELIMITER.codePointAt(0)
    );
  }

  /** Poor man's unit tests. */
  public static void main(String[] args) {
    UsernameResolution actual;
    String[][] gold = new String[][]{
      {"##### walrus koo koo ##### 1.2.3.4", "walrus koo koo", "1.2.3.4"},
      {"##### wal-rus ##### walrus.lan", "wal-rus", "walrus.lan"},
      {"##### 192.168.11.111 ##### walrus.lan", "192.168.11.111", "walrus.lan"},
      {"##### k ##### 192.168.11.111", "k", "192.168.11.111"}
    };

    int fails = 0;
    for (int i = 0; i < gold.length; ++i) {
      if (!(actual = new UsernameResolution(gold[i][0])).parse()) {
        System.err.printf(
            "FAIL[1:% 3d]: '%s' -> ('%s', '%s') but got exception:\n\t",
            i, gold[i][0], gold[i][1], gold[i][2]);
        actual.problem.printStackTrace(System.err);
        fails++;
        continue;
      }

      if (!actual.user.equals(gold[i][1]) ||
          !actual.destRaw.equals(gold[i][2])) {
        System.err.printf(
            "FAIL[1:% 3d]: '%s' -> ('%s', '%s') but got ('%s', '%s')\n",
            i, gold[i][0], gold[i][1], gold[i][2],
            actual.user, actual.destRaw);
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
      if ((actual = new UsernameResolution(coal[i])).parse()) {
        fails++;
        System.err.printf(
            "FAIL[2:% 3d]: invalid '%s' not caught; parsed as ('%s', '%s')\n",
            i, coal[i], actual.user, actual.destRaw);
      }

      System.err.printf(
          "PASS[2:% 3d]: invalid '%s' caught with: '%s'\n",
          i, coal[i], actual.problem.getMessage());
    }

    final int totalTestLen = gold.length + coal.length;
    if (fails == 0) {
      System.err.printf("PASS: all %d tests passed\n", totalTestLen);
      System.exit(0);
    }
    System.err.printf("FAIL: %d of %d tests failed\n", fails, totalTestLen);
    System.exit(1);
  }
}
