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
/*
 * Created on Aug 9, 2005
 */
package rice.environment.processing;

import rice.*;
import rice.environment.logging.LogManager;
import rice.environment.time.TimeSource;
import rice.selector.SelectorManager;

/**
 * Provides a mechanism to do time consuming tasks off of FreePastry's selecto thread.
 * 
 * Usually acquired by calling environment.getProcessor().
 * 
 * @author Jeff Hoye
 */
public interface Processor extends Destructable {
  /**
   * Schedules a job for processing on the dedicated processing thread.  CPU intensive jobs, such
   * as encryption, erasure encoding, or bloom filter creation should never be done in the context
   * of the underlying node's thread, and should only be done via this method.  The continuation will
   * be called on the Selector thread.
   *
   * @param task The task to run on the processing thread
   * @param command The command to return the result to once it's done
   */
  public void process(Executable task, Continuation command, SelectorManager selector, TimeSource ts, LogManager log);

  /**
   * Schedules a different type of task.  This thread is for doing Disk IO that is required to be blocking.
   * 
   * @param request
   */
  public void processBlockingIO(WorkRequest request);
  
  /**
   * Shuts down the processing thread.
   */
  public void destroy();
}
