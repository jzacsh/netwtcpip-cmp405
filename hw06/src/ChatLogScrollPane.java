import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.Color;
import java.util.concurrent.locks.Lock;
import java.util.List;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;

class ChatLogScrollPane extends JScrollPane implements Runnable {
  private static final long MAX_BLOCK_MILLIS = 25;
  private LockedList<Message> src;
  private int numMessagesSeen;
  private JTextArea textLog;

  public ChatLogScrollPane(int rows, int cols, LockedList<Message> logs) {
    super();
    this.src = logs;
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

    this.renderNewLogs(); // initial rendering of loaded history
  }

  private void renderNewLogs() {
    StringBuilder sb = new StringBuilder(this.textLog.getText());
    this.src.getPastIndex(this.numMessagesSeen, (Message m) -> {
      sb.append(ChatLogScrollPane.toHistoryLine(m));
    });
    this.textLog.setText(sb.toString());
    this.numMessagesSeen = this.src.size();

    // ensure as vertical scrollbars become necessary, we are *utilizing* them
    // https://stackoverflow.com/a/5150437; ie: behave like tail(1)
    JScrollBar vertical = this.getVerticalScrollBar();
    vertical.setValue(vertical.getMaximum());
  }

  public void run() {
    if (this.numMessagesSeen >= this.src.size()) {
      return;
    }
    this.renderNewLogs();
  }

  private static final String toHistoryLine(final Message m) {
    final String who = m.isReceived() ? "[them]" : "[you]";
    return String.format("%6s:  %s\n", who, m.getMessage());
  }
}
