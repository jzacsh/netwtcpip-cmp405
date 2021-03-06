import java.awt.Font;
import java.awt.Color;
import java.util.function.Consumer;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;

class ChatLogScrollPane extends JScrollPane implements Runnable {
  private final Logger log = new Logger("ChatLogScrollPane");
  private static final long MAX_BLOCK_MILLIS = 25;
  private LockedList<Message> src = null;
  private int numMessagesSeen;
  private JTextArea textLog;

  public ChatLogScrollPane(int rows, int cols) {
    super();
    this.numMessagesSeen = 0;

    this.textLog = new JTextArea(rows, cols);
    this.textLog.setEnabled(false);

    this.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
    this.setAutoscrolls(true);
    this.setViewportView(this.textLog);

    this.textLog.setFont(new Font("courier", Font.PLAIN, 14));
    this.textLog.setDisabledTextColor(Color.BLACK);
    this.textLog.setLineWrap(true);
    this.textLog.setWrapStyleWord(true);
  }

  public void syncFrom(LockedList<Message> logs) { this.src = logs; }

  private void appendLogs(Consumer<StringBuilder> c) {
    StringBuilder sb = new StringBuilder(this.textLog.getText().trim());
    sb.append('\n');
    c.accept(sb);
    this.textLog.setText(sb.toString());

    // ensure as vertical scrollbars become necessary, we are *utilizing* them
    // https://stackoverflow.com/a/5150437; ie: behave like tail(1)
    JScrollBar vertical = this.getVerticalScrollBar();
    vertical.setValue(vertical.getMaximum());
  }

  private void renderNewLogs() {
    this.appendLogs((StringBuilder sb) -> {
      this.src.getPastIndex(this.numMessagesSeen, (Message m) -> {
        sb.append(ChatLogScrollPane.toHistoryLine(m));
      });
      this.numMessagesSeen = this.src.size();
    });
  }

  public void run() {
    if (this.numMessagesSeen >= this.src.size()) {
      return;
    }
    this.renderNewLogs();
  }

  public void metaLog(final String m) {
    this.appendLogs((StringBuilder sb) -> {
      sb.append("SYSTEM: ");
      sb.append(m);
    });
  }

  private static final String toHistoryLine(final Message m) {
    final String who = m.isReceived() ? "[them]" : "[you]";
    return String.format("%6s:  %s\n", who, m.getMessage());
  }
}
