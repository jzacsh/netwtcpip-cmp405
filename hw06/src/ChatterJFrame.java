import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.net.DatagramSocket;
import java.util.Map;
import java.util.HashMap;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.text.JTextComponent;

public class ChatterJFrame extends JFrame implements ActionListener {
  private static final String TAG = "ChatterJFrame";
  private static final Logger log = new Logger(TAG);

  private static final String DASH_DEFAULT_MESSAGE = "all fields required";
  private static final String DASH_PROCESSING_MESSAGE = "loading...";

  private UsrNamesChannel userResolver = null;

  private JButton startChatBtn;
  private JLabel dashMessaging;
  private JTextField destName;
  private JTextField destPort;

  private String lastDashFailure = null;

  private static final String ACTION_DASHBRD_START = "ACTION_DASHBRD_START";

  private Map<String, MessagingJFrame> chats;

  private History hist;
  private DatagramSocket sock;

  private final int defaultRemotePort;
  public ChatterJFrame(String title, int defaultRemotePort, History hist, UsrNamesChannel userResolver) {
    super(title);
    this.defaultRemotePort = defaultRemotePort;
    this.hist = hist;
    this.setLayout(new BorderLayout());
    this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    this.userResolver = userResolver;

    this.startChatBtn = new JButton("start chat");
    this.startChatBtn.addActionListener(this);
    this.startChatBtn.setActionCommand(ACTION_DASHBRD_START);

    this.dashMessaging = new JLabel(DASH_DEFAULT_MESSAGE);
    this.dashMessaging.setFont(new Font("courier", Font.PLAIN, 16));

    JPanel txtPanel = new JPanel();
    txtPanel.add(
        this.addLabeled(
            this.destName = new JTextField(30),
            this.isUserNameMode() ? "destination" : "username",
            ACTION_DASHBRD_START),
        BorderLayout.LINE_START);
    txtPanel.add(
        this.addLabeled(
            this.destPort = new JTextField(6),
            "port", ACTION_DASHBRD_START),
        BorderLayout.CENTER);
    this.destPort.setEnabled(this.userResolver == null);
    this.getContentPane().add(txtPanel, BorderLayout.PAGE_START);
    this.getContentPane().add(this.dashMessaging, BorderLayout.CENTER);
    this.getContentPane().add(this.startChatBtn, BorderLayout.LINE_END);

    //this.setSize(200, 600); // DEBUGGING: in case `pack`ing is broken...
    this.pack();

    this.resetDashBrds();

    this.setLocationRelativeTo(null);
    this.setVisible(true);

    this.hist.registerDefaultListener((Remote r) -> this.launchCachedChat(true /*isRecvd*/, r));

    this.chats = new HashMap<>();
  }

  private boolean isUserNameMode() { return this.userResolver != null; }

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
    return String.join("|", this.destName.getText(), this.destPort.getText());
  }

  /** Whether chat-launch form has been validly populated. */
  private boolean isValidFormUsage() {
    final String host = this.destName.getText().trim();
    final String port = this.destPort.getText().trim();
    return host.length() != 0 && port.length() != 0;
  }

  private void markDashProcessing() {
    this.dashMessaging.setText(DASH_PROCESSING_MESSAGE);
  }

  private void resetDashBrds() {
    this.lastDashFailure = null;
    this.dashMessaging.setText(DASH_DEFAULT_MESSAGE);
    this.dashMessaging.setForeground(Color.black);
    this.destName.setText("");
    this.destPort.setText(String.valueOf(this.defaultRemotePort));
  }

  private void dashNoteFailure(final String currentID, final String reason) {
    this.lastDashFailure = currentID;
    this.dashMessaging.setText(reason);
    this.dashMessaging.setForeground(Color.red);
  }

  public void actionPerformed(ActionEvent e) {
    switch (e.getActionCommand()) {
      case ACTION_DASHBRD_START:
        this.markDashProcessing();

        this.log.printf(
            "validating [dest: '%s', port: '%s']; is failure=%s\n",
            this.destName.getText(), this.destPort.getText(), !this.isValidFormUsage());
        if (!this.isValidFormUsage()) {
          final String currentID = this.dashBrdID();
          if (this.lastDashFailure != null && currentID.equals(this.lastDashFailure)) {
            this.resetDashBrds();
            return;
          }

          this.dashNoteFailure(currentID, "destination & port required");
          this.lastDashFailure = currentID;
          return;
        }
        this.log.debugf(
            "starting chat with dest: '%s', port: '%s'...\n",
            this.destName.getText(), this.destPort.getText());

        final Remote target = this.buildUncheckedRemote(this.destName, this.destPort);
        this.launchCachedChat(false /*isRecvd*/, target);

        this.resetDashBrds();
        break;
      default:
        this.log.printf(
            "WARNING: unrecognized action command: %s\n",
            e.getActionCommand());
    }
  }

  private Remote buildUncheckedRemote(JTextComponent jDest, JTextComponent jPort) {
    final String destRaw = jDest.getText().trim();
    final String portRaw = jPort.getText().trim();
    return this.isUserNameMode()
        ? Remote.viaUncheckedName(destRaw, portRaw)
        : Remote.viaUncheckedAddress(destRaw, portRaw);
  }

  private void launchCachedChat(boolean isRecvd, final Remote unchecked) {
    final String launchID = unchecked.toString();
    if (!this.chats.containsKey(launchID)) {
      this.chats.put(launchID, this.launchChat(isRecvd, unchecked));
      return;
    }

    // TODO ensure remove() from this.chats, when window closes
    MessagingJFrame existing = this.chats.get(launchID);
    existing.setVisible(true);
    existing.setFocusable(true);
    existing.requestFocus();
  }

  private MessagingJFrame launchChat(boolean isRecvd, final Remote target) {
    return new MessagingJFrame(isRecvd ? "received" : "launched", this.hist, target, this.userResolver);
  }
}
