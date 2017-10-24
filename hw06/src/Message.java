import java.net.DatagramPacket;

public class Message {
  private Remote r = null;
  private String message;
  private boolean isReceived = false;
  public Message(Remote r, String message, boolean isReceived) {
    this.r = r;
    this.message = message;
    this.isReceived = isReceived;
  }
  public Remote getRemote() { return this.r; }
  public String getMessage() { return this.message; }
  public boolean isReceived() { return this.isReceived; }

  public DatagramPacket toPacket() {
    return new DatagramPacket(
        this.message.getBytes(),
        this.message.length(),
        this.r.getHost(),
        this.r.getPort());
  }
}
