import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Map;
import java.util.HashMap;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

public class ChatterJFrame extends JFrame implements ActionListener {
  private static final String TAG = "ChatterJFrame";
  private static final Logger log = new Logger(TAG);

  private static final String DASH_DEFAULT_MESSAGE = "all fields required";
  private static final String DASH_PROCESSING_MESSAGE = "loading...";

  private JButton startChatBtn;
  private JLabel dashMessaging;
  private JTextField destAddr;
  private JTextField destPort;

  private String lastDashFailure = null;

  private static final String ACTION_DASHBRD_START = "ACTION_DASHBRD_START";

  private ChatStart start = null;
  private Map<String, MessagingJFrame> chats;

  private History hist;
  private DatagramSocket sock;

  private final int defaultRemotePort;
  public ChatterJFrame(String title, int defaultRemotePort, History hist) {
    super(title);
    this.defaultRemotePort = defaultRemotePort;
    this.hist = hist;
    this.setLayout(new BorderLayout());
    this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

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

    this.hist.registerDefaultListener((Remote r) -> this.handleSolicitations(r));
    this.chats = new HashMap<>();
  }

  public ChatterJFrame enablePortChanges(boolean allowChanges) {
    this.destPort.setEnabled(allowChanges);
    return this;
  }

  private JPanel addLabeled(
      JTextField subject, String label,
      String cmd) {
    JPanel labeledField = new JPanel();
    labeledField.add(new JLabel(label), BorderLayout.LINE_END);

    subject.addActionListener(this);
    subject.setActionCommand(cmd);
    labeledField.add(subject, BorderLayout.LINE_END);
    return labeledField;
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
    this.start = ChatStart.parseFrom(host, port);
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

    if (this.destPort.isEnabled()) {
      this.destPort.setText(String.valueOf(this.defaultRemotePort));
    }
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
        this.launchCachedChat(this.start);
        this.log.debugf(
            "starting chat with dest: '%s', port: '%s'...\n",
            this.destAddr.getText(), this.destPort.getText());
        this.resetDashBrds();
        break;
      default:
        this.log.printf(
            "WARNING: unrecognized action command: %s\n",
            e.getActionCommand());
    }
  }

  private void launchCachedChat(ChatStart s) {
    final String chatID = s.toString();
    if (!this.chats.containsKey(chatID)) {
      this.chats.put(chatID, s.launchChat(this.hist));
      return;
    }

    MessagingJFrame existing = this.chats.get(chatID);
    existing.setVisible(true);
    existing.setFocusable(true);
    existing.requestFocus();
  }

  private void handleSolicitations(Remote r) {
    this.launchCachedChat(new ChatStart(r.getHost(), r.getPort()));
  }
}

class ChatStart extends Remote {
  private final String context;
  private String failure = null;

  public ChatStart(final InetAddress host, int port) {
    super(host, port);
    this.context = "recvd";
  }

  public ChatStart(
      final String rawHost, final String rawPort,
      final InetAddress host, int port) {
    super(host, port);
    this.context = String.format("started: %s:%s", rawHost, rawPort);
  }

  private ChatStart(String fail) {
    this(null /*host*/, -1 /*port*/);
    this.failure = fail;
  }

  public boolean isValid() { return this.failure == null; }
  public String getFailReason() { return this.failure; }

  public static ChatStart parseFrom(String hostRaw, String portRaw) {
    Remote r = null;
    try {
      r = Remote.parseFrom(hostRaw, portRaw);
    } catch (Throwable e) {
      return new ChatStart(e.getMessage());
    }
    return new ChatStart(hostRaw, portRaw, r.getHost(), r.getPort());
  }

  public MessagingJFrame launchChat(History hist) {
    return new MessagingJFrame(this.context, hist, this);
  }
}
