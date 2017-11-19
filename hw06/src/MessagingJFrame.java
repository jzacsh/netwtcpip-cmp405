import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.Color;
import java.awt.BorderLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;

public class MessagingJFrame extends JFrame implements ActionListener {
  private static final String TAG = "MessagingJFrame";
  private static final Logger log = new Logger(TAG);

  private static final int DEFAULT_COLUMN_WIDTH = 40;

  private JTextField composeField;
  private JButton sendBtn;

  private History hist;
  private final Remote remote;

  public MessagingJFrame(final String readable, History hist, final Remote r) {
    super(String.format("chat [%s] with %s", readable, r.toString()));
    this.hist = hist;
    this.remote = r;
    this.setLayout(new BorderLayout());
    this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

    ChatLogScrollPane chatLog = new ChatLogScrollPane(
        20 /*rows*/,
        DEFAULT_COLUMN_WIDTH /*cols*/,
        this.hist.getHistoryWith(this.remote) /*warning: blocking*/);
    this.hist.registerRemoteListener(this.remote, chatLog);
    this.getContentPane().add(chatLog, BorderLayout.CENTER);

    JPanel composePanel = new JPanel();
    this.composeField = new JTextField(DEFAULT_COLUMN_WIDTH);
    this.composeField.addActionListener(this);
    composePanel.add(this.composeField, BorderLayout.LINE_START);

    this.sendBtn = new JButton("send");
    this.sendBtn.addActionListener(this);
    composePanel.add(this.sendBtn, BorderLayout.LINE_END);

    this.getContentPane().add(composePanel, BorderLayout.PAGE_END);

    //this.setSize(200, 600); // DEBUGGING: in case `pack`ing is broken...
    this.pack();

    this.setVisible(true);
    this.composeField.requestFocus();
    this.setLocationRelativeTo(null);

    // TODO(zacsh) implement lazy-loading of remote and meta-log into chat window, and wire results
    // to handleResolvedRemote()
    this.composeField.setDisabledTextColor(Color.RED);
    this.composeField.setText("[loading] please wait...");
    this.composeField.setEnabled(false);
    this.sendBtn.setEnabled(false);
  }

  private void handleResolvedRemote() {
    if (this.remote.isValid()) {
      this.composeField.setText("");
    }
    this.composeField.setEnabled(this.remote.isValid());
    this.sendBtn.setEnabled(this.remote.isValid());
  }

  public void actionPerformed(ActionEvent e) {
    final String rawMsg = this.composeField.getText();
    if (rawMsg.trim().length() == 0) {
      return;
    }

    this.hist.safeEnqueueSend(this.remote, rawMsg);
    this.log.debugf("enqueued message '%s' to send to %s\n", rawMsg, this.remote);
    this.composeField.setText("");
  }
}
