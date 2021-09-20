package rice.tutorial.max_min;

import rice.p2p.commonapi.Id;
import rice.p2p.commonapi.Message;

public class MyMsg implements Message {
  /**
   * Where the Message came from.
   */
  Id from;
  /**
   * Where the Message is going.
   */
  Id to;
  int rand_int;
  /**
   * Constructor.
   */
  public MyMsg(Id from, Id to, Integer rand_int) {
    this.from = from;
    this.to = to;
    this.rand_int = rand_int;
  }
  
  public String toString() {
    return "MyMsg from "+from+" to "+to;
  }

  public int getInt(){
  	return this.rand_int;
  }
  /**
   * Use low priority to prevent interference with overlay maintenance traffic.
   */
  public int getPriority() {
    return Message.LOW_PRIORITY;
  }
}