import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Font;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import javax.swing.GroupLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JOptionPane;

// TODO(zacsh) rename SendChannel to DirectTextChannel
// TODO(zacsh) morph ChatterJFrame into a `ForumGUIChannel implements LocalChannel` whose internal
// thread `run()` is the below `launch()`er
// TODO(zacsh) when basic swing api is down, decompose into mux problem:
// - Chatterbox acts owns lifecycle of:
// -- mutex lock to protect a `History<String, Array<Message<Timestamp, Author, String>>>`
//    eg: see java.util.concurrent.locks.ReentrantLock
// -- RecvChannel and ForumGUIChannel
// -- Chatterbox tearsDown universe when ForumGUIChannel exits (or when anyone fatally exits)
// - Chatterbox acts as multiplexer between RecvChannel & ForumGUIChannel:
// -- RecvChannel: wants to grab lock & push onto `History`
// -- ForumGUIChannel: wants to grab lock & push onto `History` ForumGUIChannel
public class ChatterJFrame extends JFrame implements ActionListener {
  private static final String TAG = "ChatterJFrame";
  private static final Logger log = new Logger(TAG);

  private static final String DASH_DEFAULT_MESSAGE = "all fields required";
  private static final String DASH_PROCESSING_MESSAGE = "loading...";

  private static final String DASH_DEFAULT_PORT = "6400";

  private JButton startChatBtn;
  private JLabel dashMessaging;
  private JTextField destAddr;
  private JTextField destPort;

  private String lastDashFailure = null;

  private static final String ACTION_DASHBRD_START = "ACTION_DASHBRD_START";

  private ChatStart start = null;

  private DatagramSocket sock;

  public ChatterJFrame(String title, DatagramSocket sock) {
    super(title);
    this.setLayout(new BorderLayout());
    this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    this.sock = sock;

    this.startChatBtn = new JButton("start chat");
    this.startChatBtn.addActionListener(this);
    this.startChatBtn.setActionCommand(ACTION_DASHBRD_START);

    this.dashMessaging = new JLabel(DASH_DEFAULT_MESSAGE);
    this.dashMessaging.setFont(new Font("courier", Font.PLAIN, 16));

    JPanel txtPanel = new JPanel();
    txtPanel.add(
        this.addLabeled(
            this.destAddr = new JTextField(30),
            "destination", ACTION_DASHBRD_START),
        BorderLayout.LINE_START);
    txtPanel.add(
        this.addLabeled(
            this.destPort = new JTextField(6),
            "port", ACTION_DASHBRD_START),
        BorderLayout.CENTER);
    this.getContentPane().add(txtPanel, BorderLayout.PAGE_START);
    this.getContentPane().add(this.dashMessaging, BorderLayout.CENTER);
    this.getContentPane().add(this.startChatBtn, BorderLayout.LINE_END);

    //this.setSize(200, 600); // DEBUGGING: in case `pack`ing is broken...
    this.pack();

    this.resetDashBrds();

    this.setLocationRelativeTo(null);
    this.setVisible(true);
  }

  private JPanel addLabeled(
      JTextField subject, String label,
      String cmd) {
    this.log.printf("label not yet handled: '%s'\n", label);

    JPanel labeledField = new JPanel();
    labeledField.add(new JLabel(label), BorderLayout.LINE_END);

    subject.addActionListener(this);
    subject.setActionCommand(cmd);
    labeledField.add(subject, BorderLayout.LINE_END);
    return labeledField;
  }

  public static ChatterJFrame startDisplay(
      String appTitle,
      DatagramSocket sock,
      WindowAdapter teardown) {
    ChatterJFrame w = new ChatterJFrame(appTitle, sock);
    w.addCleanupHandler(teardown);
    return w;
  }

  private String dashBrdID() {
    return String.join("|", this.destAddr.getText(), this.destPort.getText());
  }

  /** Returns a reason for failing validity, or null if valid. */
  private String isValidDashbrdStart() {
    final String host = this.destAddr.getText().trim();
    final String port = this.destPort.getText().trim();
    if (host.length() == 0 || port.length() == 0) {
      return "host & port required";
    }
    this.start = ChatStart.fromStrings(host, port);
    return this.start.isValid() ? null : this.start.getFailReason();
  }

  private void markDashProcessing() {
    this.dashMessaging.setText(DASH_PROCESSING_MESSAGE);
  }

  private void resetDashBrds() {
    this.lastDashFailure = null;
    this.dashMessaging.setText(DASH_DEFAULT_MESSAGE);
    this.dashMessaging.setForeground(Color.black);
    this.destAddr.setText("");
    this.destPort.setText(DASH_DEFAULT_PORT);
  }

  private void dashNoteFailure(final String currentID, final String reason) {
    this.lastDashFailure = currentID;
    this.dashMessaging.setText(reason);
    this.dashMessaging.setForeground(Color.red);
  }

  public void actionPerformed(ActionEvent e) {
    switch (e.getActionCommand()) {
      case ACTION_DASHBRD_START:
        final String validityFail = this.isValidDashbrdStart();
        this.markDashProcessing();
        this.log.printf(
            "validating [dest: '%s', port: '%s']... fail:'%s'\n",
            this.destAddr.getText(), this.destPort.getText(), validityFail);
        if (validityFail != null) {
          final String currentID = this.dashBrdID();
          if (this.lastDashFailure != null && currentID.equals(this.lastDashFailure)) {
            this.resetDashBrds();
            return;
          }
          this.dashNoteFailure(currentID, validityFail);
          this.lastDashFailure = currentID;
          return;
        }

        this.start.launchChat(this.sock);
        // TODO(zacsh) still need mutexes and history store
        this.log.printf(
            "NOT YET IMPLEMENTED: starting valid chat\n\tdest: '%s', port: '%s'...\n",
            this.destAddr.getText(), this.destPort.getText());
        this.resetDashBrds();
        break;
      default:
        this.log.printf(
            "WARNING: unrecognized action command: %s\n",
            e.getActionCommand());
    }
  }

  public void addCleanupHandler(WindowAdapter cleanup) {
    this.addWindowListener(cleanup);
  }
}

class ChatStart {
  private InetAddress host;
  private int port;
  private String failure;

  private ChatStart(String failure) { this.failure = failure; }
  public ChatStart(final InetAddress host, int port) {
    this.host = host;
    this.port = port;
    this.failure = null;
  }
  public InetAddress getHost() { return this.host; }
  public int getPort() { return this.port; }

  public boolean isValid() { return this.failure == null; }
  public String getFailReason() { return this.failure; }
  public static ChatStart fromStrings(String hostRaw, String portRaw) {
    InetAddress host = null;
    try {
      host = InetAddress.getByName(hostRaw);
    } catch (UnknownHostException e) {
      return new ChatStart(e.toString());
    }
    int port;
    try {
      port = Integer.parseUnsignedInt(portRaw);
    } catch (NumberFormatException e) {
      return new ChatStart(e.toString());
    }
    return new ChatStart(host, port);
  }

  public void launchChat(DatagramSocket sock) {
    // TODO(zacsh) pass socket here instead
    new MessagingJFrame(sock, this.host, this.port);
  }
}
