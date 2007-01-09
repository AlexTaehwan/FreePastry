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
package rice.email.proxy.smtp.commands;

import rice.email.proxy.mail.MailAddress;
import rice.email.proxy.mail.MalformedAddressException;

import rice.email.proxy.smtp.SmtpConnection;
import rice.email.proxy.smtp.SmtpState;
import rice.email.proxy.smtp.manager.SmtpManager;

import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * RCPT command.
 * 
 * <p>
 * The spec is at <a
 * href="http://asg.web.cmu.edu/rfc/rfc2821.html#sec-4.1.1.3">
 * http://asg.web.cmu.edu/rfc/rfc2821.html#sec-4.1.1.3</a>.
 * </p>
 */
public class RcptCommand extends SmtpCommand {
  static Pattern param = Pattern.compile("RCPT TO:\\s?<([^>]+)>", Pattern.CASE_INSENSITIVE);
  
  public boolean authenticationRequired() { return true; }
  
  public void execute(SmtpConnection conn, SmtpState state, SmtpManager manager, String commandLine) {
    Matcher m = param.matcher(commandLine);
    
    try {
      if (m.matches()) {
        if (state.getMessage().getReturnPath() != null) {
          String to = m.group(1);
          
          MailAddress toAddr = new MailAddress(to);
          
          String err = manager.checkRecipient(conn, state, toAddr);
          
          if (err != null) {
            conn.println("554 Error: " + err);
            conn.quit();
            return;
          }
          
          state.getMessage().addRecipient(toAddr);
          conn.println("250 " + to + "... Recipient OK");
        } else {
          conn.println("503 MAIL must come before RCPT");
        }
      } else {
        conn.println("501 Required syntax: 'RCPT TO:<email@host>'");
      }
    } catch (MalformedAddressException e) {
      conn.println("501 Malformed email address. Use form email@host");
    }
  }
}