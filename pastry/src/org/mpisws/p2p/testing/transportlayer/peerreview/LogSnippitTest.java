/*******************************************************************************

"FreePastry" Peer-to-Peer Application Development Substrate

Copyright 2002-2007, Rice University. Copyright 2006-2007, Max Planck Institute 
for Software Systems.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are
met:

- Redistributions of source code must retain the above copyright
notice, this list of conditions and the following disclaimer.

- Redistributions in binary form must reproduce the above copyright
notice, this list of conditions and the following disclaimer in the
documentation and/or other materials provided with the distribution.

- Neither the name of Rice  University (RICE), Max Planck Institute for Software 
Systems (MPI-SWS) nor the names of its contributors may be used to endorse or 
promote products derived from this software without specific prior written 
permission.

This software is provided by RICE, MPI-SWS and the contributors on an "as is" 
basis, without any representations or warranties of any kind, express or implied 
including, but not limited to, representations or warranties of 
non-infringement, merchantability or fitness for a particular purpose. In no 
event shall RICE, MPI-SWS or contributors be liable for any direct, indirect, 
incidental, special, exemplary, or consequential damages (including, but not 
limited to, procurement of substitute goods or services; loss of use, data, or 
profits; or business interruption) however caused and on any theory of 
liability, whether in contract, strict liability, or tort (including negligence
or otherwise) arising in any way out of the use of this software, even if 
advised of the possibility of such damage.

*******************************************************************************/ 
package org.mpisws.p2p.testing.transportlayer.peerreview;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Random;

import org.mpisws.p2p.transport.peerreview.audit.LogSnippit;
import org.mpisws.p2p.transport.peerreview.audit.SnippitEntry;
import org.mpisws.p2p.transport.util.Serializer;

import rice.p2p.commonapi.rawserialization.InputBuffer;
import rice.p2p.commonapi.rawserialization.OutputBuffer;
import rice.p2p.commonapi.rawserialization.RawSerializable;
import rice.p2p.util.MathUtils;
import rice.p2p.util.rawserialization.SimpleInputBuffer;
import rice.p2p.util.rawserialization.SimpleOutputBuffer;

public class LogSnippitTest {

  static class MyHandle implements RawSerializable {
    int id;

    public MyHandle(int i) {
      this.id = i;
    }
    
    public void serialize(OutputBuffer buf) throws IOException {
      buf.writeInt(id);
    }
    
    public boolean equals(Object o) {
      MyHandle that = (MyHandle)o;
      return this.id == that.id;
    }
    
  }
  
  static class MyHandleSerializer implements Serializer<MyHandle> {

    public MyHandle deserialize(InputBuffer buf) throws IOException {
      return new MyHandle(buf.readInt());
    }

    public void serialize(MyHandle i, OutputBuffer buf) throws IOException {
      i.serialize(buf);
    }
    
  }
  
  /**
   * @param args
   */
  public static void main(String[] args) throws Exception {
//    int foo = 253;
//    byte b = (byte)253;
//    System.out.println(MathUtils.uByteToInt(b));
//    System.out.println((b == (byte)0xFD));
//
//    if (true) return;
    
    
    Random r = new Random();
    int hashSize = r.nextInt(100)+1;

    ArrayList<SnippitEntry> list = new ArrayList<SnippitEntry>(100);
//    list.add(new SnippitEntry((byte)4,5L,false,new byte[117]));
//    list.add(new SnippitEntry((byte)4,6L,false,new byte[253]));
    
    long seq = Math.abs(r.nextLong());
    for (int i = 0; i < 400; i++) {
      boolean isHash = r.nextInt(10) == 1;
      byte[] content;
      if (isHash) {
        content = new byte[hashSize];
      } else {
        // pick a size
        switch(r.nextInt(15)) {
        case 9:
          content = new byte[r.nextInt(65535)];
          break;
        case 8:
          content = new byte[r.nextInt(Short.MAX_VALUE*10)];
          break;
        case 10:
          content = new byte[0xFF];
          break;
        case 11:
          content = new byte[0xFE];
          break;
        case 12:
          content = new byte[0xFD];
          break;
        case 13:
          content = new byte[0xFFFF];
          break;
        case 14:
          content = new byte[0xFFFE];
          break;
        default:
          content = new byte[r.nextInt(254)+1];
        }
      }
      r.nextBytes(content);
      
      int seqChoice = r.nextInt(10);
      if (seqChoice < 5) {
        seq++; // increment the index
      } else if (seqChoice < 8) {
        // increment it to the next round timestamp
        seq-= (seq%SnippitEntry.NUM_INDEXES);
        seq+= SnippitEntry.NUM_INDEXES;
      } else {
        seq+=r.nextInt(Integer.MAX_VALUE);
      }
      list.add(new SnippitEntry((byte)r.nextInt(256),seq,isHash,content));
    }
    
    byte[] baseHash = new byte[hashSize];
    r.nextBytes(baseHash);
    LogSnippit<MyHandle> ls = new LogSnippit<MyHandle>(new MyHandle(r.nextInt()),baseHash,list);
    
    SimpleOutputBuffer sob = new SimpleOutputBuffer();
    ls.serialize(sob);
    
    SimpleInputBuffer sib = new SimpleInputBuffer(sob.getBytes());
    LogSnippit<MyHandle> ls2 = new LogSnippit<MyHandle>(sib,new MyHandleSerializer(),hashSize);
    
    if (ls2.equals(ls)) {
      System.out.println("success");
    } else {
      System.out.println("failure");
    }
  }

}
