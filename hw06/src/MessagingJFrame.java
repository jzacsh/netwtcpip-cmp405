import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.BorderLayout;
import java.net.DatagramSocket;
import java.net.InetAddress;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JTextArea;

public class MessagingJFrame extends JFrame implements ActionListener {
  private static final String TAG = "MessagingJFrame";
  private static final Logger log = new Logger(TAG);

  private static final int DEFAULT_COLUMN_WIDTH = 40;

  private JTextField composeField;
  private JButton sendBtn;
  private JTextArea chatLog;

  private History hist;
  private final Remote remote;

  public MessagingJFrame(History hist, final Remote r) {
    super(String.format("chat with %s", r.toString()));
    this.hist = hist;
    this.remote = r;
    this.setLayout(new BorderLayout());
    this.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

    this.chatLog = new JTextArea(20 /*rows*/, DEFAULT_COLUMN_WIDTH /*cols*/);
    this.chatLog.setEnabled(false);
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
  }

  public void actionPerformed(ActionEvent e) {
    final String rawMsg = this.composeField.getText();
    if (rawMsg.trim().length() == 0) {
      return;
    }

    Message m = new Message(this.remote, rawMsg, false /*isReceived*/);

    this.hist.safeEnqueueSend(this.remote, m);
    this.log.printf("enqueued message '%s' to send to %s\n", rawMsg, this.remote);
    this.composeField.setText("");
  }
}
