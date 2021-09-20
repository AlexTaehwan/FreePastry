package rice.tutorial.max_min;

import rice.p2p.commonapi.NodeHandle;
import rice.p2p.scribe.ScribeContent;

public class MyScribeContent implements ScribeContent {
	NodeHandle from;

	int seq;
	int int_random;

	public MyScribeContent(NodeHandle from, int seq){
		this.from = from;
		this.seq = seq;
	}

	public String toString(){
		return "MyScribeContent #"+seq+" from "+from+". Random_int = "+int_random;
	}
}