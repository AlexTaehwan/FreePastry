
package rice.p2p.util;

import java.io.*;
import org.xmlpull.v1.*;

/**
 * XMLWriter is a utility class used by XMLObjectOutputStream to perform the actual
 * XML writing.  This writing is based on the XML Pull-Parsing API, available online
 * at http://www.xmlpull.org.  Any of the provided serializer implementations will work 
 * with this reader.
 *
 * @version $Id$
 *
 * @author Alan Mislove
 */
public class XMLWriter {
  
  /**
   * The actual XML serializer, which does the writing
   */
  protected XmlSerializer serializer;
  
  /**
   * The underlying writer which the serializer uses
   */
  protected Writer writer;
  
  /**
   * Constructor which takes the provided writer and
   * builds a new XML writier to read XML from the writier.
   *
   * @param out The writer to base this XML writer off of
   * @throws IOException If an error occurs
   */
  public XMLWriter(OutputStream out) throws IOException {
    try {
      this.writer = new BufferedWriter(new OutputStreamWriter(out));
      XmlPullParserFactory factory = XmlPullParserFactory.newInstance(System.getProperty(XmlPullParserFactory.PROPERTY_NAME), null);
      serializer = factory.newSerializer();
      
      serializer.setOutput(this.writer);
      serializer.setProperty("http://xmlpull.org/v1/doc/properties.html#serializer-indentation", " ");
      serializer.setProperty("http://xmlpull.org/v1/doc/properties.html#serializer-line-separator", "\n");
      serializer.setFeature("http://xmlpull.org/v1/doc/features.html#serializer-attvalue-use-apostrophe", true);
    } catch (XmlPullParserException e) {
      throw new IOException("XML Exception thrown: " + e);
    }    
  }
  
  /**
   * Method which flushes all buffered data to the underlying writer
   *
   * @throws IOException If an error occurs
   */
  public void flush() throws IOException {
    serializer.flush();
  }
  
  /**
   * Method which flushes and closes the underlying writer, which will
   * cause future writer attempts to throw an IOException.
   *
   * @throws IOException If an error occurs
   */
  public void close() throws IOException {
    serializer.text("\n");
    serializer.flush();
    writer.close();
  }
  
  /**
   * Method which writes a sequence of base64 encoded bytes to the output stream
   *
   * @param bytes The bytes to write
   */
  public void writeBase64(byte[] bytes, int off, int len) throws IOException {
    flush();
    writer.write(Base64.encodeBytes(bytes, off, len));
  }

  /**
   * Method which writes the XML header to the writer.
   *
   * @throws IOException If an error occurs
   */
  public void writeHeader() throws IOException {
    serializer.startDocument(null, null);
    serializer.text("\n\n");
  }
  
  /**
   * Method which writes an attribute to the XML document.
   *
   * @param name The name of the attribute to write
   * @param value The value to write
   * @throws IOException If an error occurs
   */
  public void attribute(String name, int value) throws IOException {
    attribute(name, String.valueOf(value));
  }
  
  /**
   * Method which writes an attribute to the XML document.
   *
   * @param name The name of the attribute to write
   * @param value The value to write
   * @throws IOException If an error occurs
   */
  public void attribute(String name, double value) throws IOException {
    attribute(name, String.valueOf(value));
  }
  
  /**
   * Method which writes an attribute to the XML document.
   *
   * @param name The name of the attribute to write
   * @param value The value to write
   * @throws IOException If an error occurs
   */
  public void attribute(String name, float value) throws IOException {
    attribute(name, String.valueOf(value));
  }
  
  /**
   * Method which writes an attribute to the XML document.
   *
   * @param name The name of the attribute to write
   * @param value The value to write
   * @throws IOException If an error occurs
   */
  public void attribute(String name, long value) throws IOException {
    attribute(name, String.valueOf(value));
  }
  
  /**
   * Method which writes an attribute to the XML document.
   *
   * @param name The name of the attribute to write
   * @param value The value to write
   * @throws IOException If an error occurs
   */
  public void attribute(String name, char value) throws IOException {
    attribute(name, String.valueOf(value));
  }
  
  /**
   * Method which writes an attribute to the XML document.
   *
   * @param name The name of the attribute to write
   * @param value The value to write
   * @throws IOException If an error occurs
   */
  public void attribute(String name, boolean value) throws IOException {
    attribute(name, String.valueOf(value));
  }
  
  /**
   * Method which writes an attribute to the XML document.
   *
   * @param name The name of the attribute to write
   * @param value The value to write
   * @throws IOException If an error occurs
   */
  public void attribute(String name, Object value) throws IOException {
    if (value == null)
      return;
    
    attribute(name, value.toString());
  }
  
  /**
   * Method which writes an attribute to the XML document.
   *
   * @param name The name of the attribute to write
   * @param value The value to write
   * @throws IOException If an error occurs
   */
  protected void attribute(String name, String value) throws IOException {
    serializer.attribute(null, name, value);
  }
  
  /**
   * Method which starts the given tag name
   *
   * @param name The name of the tag to start
   * @throws IOException If an error occurs
   */
  public void start(String name) throws IOException {
    serializer.startTag(null, name);
  }
  
  /**
   * Method which ends the given tag name
   *
   * @param name The name of the tag to end
   * @throws IOException If an error occurs
   */
  public void end(String name) throws IOException {
    serializer.endTag(null, name);
  }
}