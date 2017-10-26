import java.util.concurrent.locks.Lock;
import java.util.List;
import javax.swing.JTextArea;
import java.awt.Font;

class ChatLogTextArea extends JTextArea implements Runnable {
  private static final long MAX_BLOCK_MILLIS = 25;
  private Pair<Lock, List<Message>> src;
  private int numMessagesSeen;
  public ChatLogTextArea(int rows, int cols, Pair<Lock, List<Message>> logs) {
    super(rows, cols);
    this.src = logs;
    this.numMessagesSeen = 0;
    this.setFont(new Font("courier", Font.PLAIN, 14));
  }

  private void renderNewLogs() {
    StringBuilder sb = new StringBuilder(this.getText());
    for (int i = numMessagesSeen; i < this.src.right.size(); ++i) {
      sb.append(ChatLogTextArea.toHistoryLine(this.src.right.get(i)));
    }
    this.setText(sb.toString());
    this.numMessagesSeen = this.src.right.size();
  }

  public void run() {
    if (this.numMessagesSeen >= this.src.right.size()) {
      return;
    }
    RunLocked.safeRunTry(MAX_BLOCK_MILLIS, this.src.left, () -> this.renderNewLogs());
  }

  private static final String toHistoryLine(final Message m) {
    final String who = m.isReceived() ? "[them]" : "[you]";
    return String.format("%6s:\t%s\n", who, m.getMessage());
  }
}
