package rice.p2p.util;

import org.xmlpull.v1.*;
import java.io.*;
import java.util.*;

/** 
* This class is a memory-efficient implementation of most of the XML
* pull parsing API.
*/
public class XMLParser implements XmlPullParser {

  /**
   * The size of the internal buffer to allocate
   */
  public static final int BUFFER_SIZE = 32000;
  
  public static final char[] QUOTE = new char[] {'\'', '"'};
  public static final char[] TAG_END = new char[] {'>', '/', '?'};
  public static final char[] WHITESPACE = new char[] {' ', '\t', '\n', '\r'};
  public static final char[] WHITESPACE_OR_TAG_END = new char[] {' ', '\t', '\n', '\r', '>', '/', '?'};
  public static final char[] WHITESPACE_OR_EQUALS = new char[] {' ', '\t', '\n', '\r', '='};

  /**
   * The internal reader used to read data
   */
  protected Reader reader;
  
  /**
   * The internal buffer used to process data
   */
  protected char[] buffer;
  
  /**
   * Internal pointers into the buffer
   */
  protected int bufferPosition;
  protected int bufferLimit;
  
  /**
   * The StringCache used to reduce the memory requirements
   */
  protected StringCache cache;
  
  /**
   * The internal stack of tags which have been read
   */
  protected Stack tags;
  
  /**
   * If the tag parsed was a start/end, the name of the tag
   */
  protected String name;
  
  /**
   * If the tag parsed was text, the text
   */
  protected String text;
  
  /**
   * If the tag parsed was a start tag, the list of attribute-> value pairs
   */
  protected HashMap attributes;
  
  /**
   * Whether or not we are currently in a tag...
   */
  protected boolean inTag;
  
  /**
   * Internal variable which keeps track of the current mark
   */
  protected int mark;
  protected CharArrayBuffer marked;
  
  protected StringBuffer debug = new StringBuffer();
  
  /**
   * Constructor
   */
  public XMLParser() {
    this.buffer = new char[BUFFER_SIZE];
    this.bufferPosition = 0;
    this.bufferLimit = 0;
    this.mark = -1;
    this.tags = new Stack();
    this.cache = new StringCache();
    this.attributes = new HashMap();
    this.inTag = false;
  }
  
  /**
   * Set the input source for parser to the given reader and
   * resets the parser. The event type is set to the initial value
   * START_DOCUMENT.
   * Setting the reader to null will just stop parsing and
   * reset parser state,
   * allowing the parser to free internal resources
   * such as parsing buffers.
   */
  public void setInput(Reader in) throws XmlPullParserException {
    this.reader = in;
  }
  
  /**
   * Returns the text content of the current event as String.
   * The value returned depends on current event type,
   * for example for TEXT event it is element content
   * (this is typical case when next() is used).
   *
   * See description of nextToken() for detailed description of
   * possible returned values for different types of events.
   *
   * <p><strong>NOTE:</strong> in case of ENTITY_REF, this method returns
   * the entity replacement text (or null if not available). This is
   * the only case where
   * getText() and getTextCharacters() return different values.
   *
   * @see #getEventType
   * @see #next
   * @see #nextToken
   */
  public String getText() {
    return this.text;
  }
  
  /**
   * For START_TAG or END_TAG events, the (local) name of the current
   * element is returned when namespaces are enabled. When namespace
   * processing is disabled, the raw name is returned.
   * For ENTITY_REF events, the entity name is returned.
   * If the current event is not START_TAG, END_TAG, or ENTITY_REF,
   * null is returned.
   * <p><b>Please note:</b> To reconstruct the raw element name
   *  when namespaces are enabled and the prefix is not null,
   * you will need to  add the prefix and a colon to localName..
   *
   */
  public String getName() {
    return this.name;
  }
  
  /**
   * Returns the attributes value identified by namespace URI and namespace localName.
   * If namespaces are disabled namespace must be null.
   * If current event type is not START_TAG then IndexOutOfBoundsException will be thrown.
   *
   * <p><strong>NOTE:</strong> attribute value must be normalized
   * (including entity replacement text if PROCESS_DOCDECL is false) as described in
   * <a href="http://www.w3.org/TR/REC-xml#AVNormalize">XML 1.0 section
   * 3.3.3 Attribute-Value Normalization</a>
   *
   * @see #defineEntityReplacementText
   *
   * @param namespace Namespace of the attribute if namespaces are enabled otherwise must be null
   * @param name If namespaces enabled local name of attribute otherwise just attribute name
   * @return value of attribute or null if attribute with given name does not exist
   */
  public String getAttributeValue(String namespace, String name) {
    return (String) attributes.get(name);
  }
  
  /**
   * Returns the type of the current event (START_TAG, END_TAG, TEXT, etc.)
   *
   * @see #next()
   * @see #nextToken()
   */
  public int getEventType() throws XmlPullParserException {
    return 0;
  }
  
  /**
   * Get next parsing event - element content wil be coalesced and only one
   * TEXT event must be returned for whole element content
   * (comments and processing instructions will be ignored and emtity references
   * must be expanded or exception mus be thrown if entity reerence can not be exapnded).
   * If element content is empty (content is "") then no TEXT event will be reported.
   *
   * <p><b>NOTE:</b> empty element (such as &lt;tag/>) will be reported
   *  with  two separate events: START_TAG, END_TAG - it must be so to preserve
   *   parsing equivalency of empty element to &lt;tag>&lt;/tag>.
   *  (see isEmptyElementTag ())
   *
   * @see #isEmptyElementTag
   * @see #START_TAG
   * @see #TEXT
   * @see #END_TAG
   * @see #END_DOCUMENT
   */
  public int next() throws XmlPullParserException, IOException {
    char next = 0;
    
    try {
      next = current();
    } catch (EOFException e) {
      return END_DOCUMENT;
    }

    switch (next) {
      case '<':
        int result = parseTag();
        
        if (result == START_DOCUMENT)
          return next();
        else
          return result;
      case '/':
        if (this.inTag)
          return parseEndTag((String) tags.pop());
        else
          return parseText();
      default:
        return parseText();
    }
  }
  
  /**
   * Checks whether the current TEXT event contains only whitespace
   * characters.
   * For IGNORABLE_WHITESPACE, this is always true.
   * For TEXT and CDSECT, false is returned when the current event text
   * contains at least one non-white space character. For any other
   * event type an exception is thrown.
   *
   * <p><b>Please note:</b> non-validating parsers are not
   * able to distinguish whitespace and ignorable whitespace,
   * except from whitespace outside the root element. Ignorable
   * whitespace is reported as separate event, which is exposed
   * via nextToken only.
   *
   */
  public boolean isWhitespace() throws XmlPullParserException {
    return isWhitespace(text);
  }
  
  /**
   *   ----- INTERNAL PARSING METHODS -----
   */
  
  /**
   * Internal method which actually fills the buffer
   */
  protected void fillBuffer() throws IOException {
    // process the mark
    if (marked != null)
      this.marked.append(buffer, 0, bufferPosition);
    else if (mark != -1)
      this.marked = new CharArrayBuffer(buffer, mark, bufferPosition - mark);
    
    int read = reader.read(buffer);
    
    if (read > 0) {
      bufferLimit = read;
      bufferPosition = 0;
    }
  }
  
  /**
   * Method which returns the current char in the buffer
   *
   * @return The current char
   */
  protected char current() throws IOException {
    if (bufferPosition == bufferLimit) fillBuffer();
    if (bufferPosition == bufferLimit) throw new EOFException();
    
    return buffer[bufferPosition];
  }
  
  /**
   * Method which steps forward in the buffer
   */
  protected void step() {
    debug.append(buffer[bufferPosition]);
    bufferPosition++;
  }
  
  /**
   * Sets the mark
   */
  protected void mark() {
    this.mark = bufferPosition;
  }
  
  /**
   * Unsets the mark
   */
  protected String unmark() {
    try {    
      if (this.marked != null) {
        this.marked.append(buffer, 0, bufferPosition);
        return cache.get(marked.getBuffer(), 0, marked.getLength());
      } else {
        return cache.get(buffer, mark, bufferPosition-mark);
      }
    } finally {
      this.mark = -1;
      this.marked = null;
    }
  }
  
  /**
   * Internal method which clears the list of attributes
   */
  protected void clearAttributes() {
    this.attributes.clear();
  }
  
  /**
   * Internal method which adds an attributes
   */
  protected void addAttribute(String key, String value) {
    this.attributes.put(key, value);
  }
  
  /**
   * An assertion method
   *
   * @param the expected char
   */
  protected void expect(char c) throws XmlPullParserException, IOException {
    if (current() != c) {
      System.out.println("DOC SO FAR!\n'" + debug + current() + "'");
      
      throw new XmlPullParserException("Expected character '" + c + "' got '" + current() + "'");
    } else
      step();
  }
  
  /**
   * Internal method which checks for existence
   *
   * @param chars The chars to check for
   * @param char The char
   */
  public boolean isWhitespace(String text) {
    for (int i=0; i<text.length(); i++)
      if (! contains(WHITESPACE, text.charAt(i)))
        return false;
    
    return true;
  }
  
  /**
   * Internal method which checks for existence
   *
   * @param chars The chars to check for
   * @param char The char
   */
  protected boolean contains(char[] chars, char c) {
    for (int i=0; i<chars.length; i++)
      if (chars[i] == c)
        return true;
    
    return false;
  }
  
  /**
   * Method which parses and returns up to the next token
   *
   * @return The token
   */
  protected String parseUntil(char[] chars) throws IOException {
    mark();
    
    while (true) {
      if (contains(chars, current()))
        break;
      else
        step();
    }
    
    return unmark();
  }
  
  /**
   * Method which parses up to the next token
   */
  protected void parseUntilNot(char[] chars) throws IOException {
    while (true) {
      if (! contains(chars, current()))
        break;
      else
        step();
    }    
  }
    
  /**
   * Method which parses an end tag of the form <tag />
   *
   * @param The name of the parsed tag
   */
  protected int parseEndTag(String tag) throws XmlPullParserException, IOException {
    expect('/');
    expect('>');

    this.name = tag;
    this.inTag = false;
    
//    System.out.println("Parsed default END tag '" + this.name + "'");
    
    return END_TAG;
  }
  
  /**
   * Internal method which parses a tag
   */
  protected int parseTag() throws XmlPullParserException, IOException {
    expect('<');

    switch (current()) {
      case '/':
        return parseEndTag();
      case '?':
        return parseDocumentTag();
      default:
        return parseStartTag();
    }    
  }
  
  /**
   * Method which parses an end tag of the form <tag />
   *
   * @param The name of the parsed tag
   */
  protected int parseEndTag() throws XmlPullParserException, IOException {
    expect('/');
    parseUntilNot(WHITESPACE);
 
    clearAttributes();
    this.name = parseUntil(WHITESPACE_OR_TAG_END);
    tags.pop();
    this.inTag = false;
    
    parseUntilNot(WHITESPACE);
    expect('>');

//    System.out.println("Parsed END tag '" + this.name + "'");
    
    return END_TAG;
  }
  
  /**
   * Method which parses a start tag
   */
  protected int parseStartTag() throws XmlPullParserException, IOException {
    parseUntilNot(WHITESPACE);
    
    this.name = parseUntil(WHITESPACE_OR_TAG_END);
    tags.push(this.name);
    
    parseUntilNot(WHITESPACE);

//    System.out.println("Parsed START tag '" + this.name + "'");
    
    parseAttributes();
    
    parseUntilNot(WHITESPACE);
        
    if (current() != '/') {
      expect('>');
      this.inTag = false;
    } else {
      this.inTag = true;
    }
    
    return START_TAG;
  }
  
  /**
   * Method which parses a document tag
   */
  protected int parseDocumentTag() throws XmlPullParserException, IOException {
    expect('?');
    
    parseUntilNot(WHITESPACE);
    
    String type = parseUntil(WHITESPACE_OR_TAG_END);

    if (! (type.toLowerCase().equals("xml")))
      throw new XmlPullParserException("This does not appear to be an XML document - found '" + type + "'!");
    
    parseUntilNot(WHITESPACE);
    
    parseAttributes();
    clearAttributes();
    
    parseUntilNot(WHITESPACE);
    this.inTag = false;
    
    expect('?');
    expect('>');
    
//    System.out.println("Parsed START DOCUMENT tag '" + type + "'");
    
    return START_DOCUMENT;
  }
  
  /**
   * Method which parses all of the attributes of a start tag
   */
  protected void parseAttributes() throws XmlPullParserException, IOException {
    clearAttributes();

    while (true) {
      parseUntilNot(WHITESPACE);
      
      if (contains(TAG_END, current())) {
        return;
      } else {
        String key = parseUntil(WHITESPACE_OR_EQUALS);
        parseUntilNot(WHITESPACE);
        expect('=');
        parseUntilNot(WHITESPACE);
        String value = null;
        char quote = current();
        
        if (contains(QUOTE, quote)) {
          expect(quote);
          value = parseUntil(new char[] {quote});
          expect(quote);
        } else {
          value = parseUntil(WHITESPACE_OR_TAG_END);
        }
        
//        System.out.println("Parsed ATTRIBUTE '" + key + "' -> '"  + value + "'");
        
        addAttribute(key, value);
      }
    }
  }
  
  /**
   * Method which parses an end tag of the form <tag />
   *
   * @param The name of the parsed tag
   */
  protected int parseText() throws XmlPullParserException, IOException {
    clearAttributes();
    this.text = parseUntil(new char[] {'<'});
    this.inTag = false;

    return TEXT;
  }
  
  /**
   *   ----- INTERNAL CLASS -----
   */
  
  /**
    * This class implements a char array buffer
   */
  public class CharArrayBuffer {
    
    /**
    * The default initial capacity
     */
    public static final int DEFAULT_CAPACITY = 32;
    
    /**
    * The internal buffer
     */
    protected char[] buffer;
    
    /**
      * The markers
     */
    protected int length;
    
    /**
      * Constructor
     */
    public CharArrayBuffer(char[] chars, int length, int off) {
      this.buffer = new char[DEFAULT_CAPACITY];
      this.length = 0;
      
      append(chars, length, off);
    }
    
    /**
      * Returns the internal array
     *
     * @return The array
     */
    public char[] getBuffer() {
      return buffer;
    }
    
    /**
      * Returns the length
     *
     * @return The length
     */
    public int getLength() {
      return length;
    }
    
    /**
      * Appends some more chars!
     *
     * @param THe chars to append
     */
    public void append(char[] chars, int off, int len) {
      while (length + len > buffer.length)
        expandBuffer();
      
      System.arraycopy(chars, off, buffer, length, len);
      length+=len;
    }
    
    /**
      * Expands the buffer
     */
    protected void expandBuffer() {
      char[] newbuffer = new char[buffer.length * 2];
      
      System.arraycopy(buffer, 0, newbuffer, 0, length);
      this.buffer = newbuffer;
    }
  }
    
  /**
   *   ----- UNSUPPORTED METHODS -----
   */
  
  public void setFeature(String name, boolean state) throws XmlPullParserException {
    throw new UnsupportedOperationException();
  }
  
  public boolean getFeature(String name) {
    throw new UnsupportedOperationException();
  }
  
  public void setProperty(String name, Object value) throws XmlPullParserException {
    throw new UnsupportedOperationException();
  }
  
  public Object getProperty(String name) {
    throw new UnsupportedOperationException();
  }
  
  public void setInput(InputStream inputStream, String inputEncoding) throws XmlPullParserException {
    throw new UnsupportedOperationException();
  }
  
  public String getInputEncoding() {
    throw new UnsupportedOperationException();
  }
  
  public void defineEntityReplacementText(String entityName, String replacementText ) throws XmlPullParserException {
    throw new UnsupportedOperationException();
  }
  
  public int getNamespaceCount(int depth) throws XmlPullParserException {
    throw new UnsupportedOperationException();
  }
  
  public String getNamespacePrefix(int pos) throws XmlPullParserException  {
    throw new UnsupportedOperationException();
  }
  
  public String getNamespaceUri(int pos) throws XmlPullParserException {
    throw new UnsupportedOperationException();
  }
  
  public String getNamespace (String prefix) {
    throw new UnsupportedOperationException();
  }
  
  public int getDepth() {
    throw new UnsupportedOperationException();
  }
    
  public String getPositionDescription () {
    throw new UnsupportedOperationException();
  }
  
  public int getLineNumber() {
    throw new UnsupportedOperationException();
  }
  
  public int getColumnNumber() {
    throw new UnsupportedOperationException();
  }
    
  public char[] getTextCharacters(int [] holderForStartAndLength) {
    throw new UnsupportedOperationException();
  }
  
  public String getNamespace () {
    throw new UnsupportedOperationException();
  }
  
  public String getPrefix() {
    throw new UnsupportedOperationException();
  }
  
  public boolean isEmptyElementTag() throws XmlPullParserException {
    throw new UnsupportedOperationException();
  }
  
  public String getAttributeNamespace (int index) {
    throw new UnsupportedOperationException();
  }
  
  public String getAttributePrefix(int index) {
    throw new UnsupportedOperationException();
  }
  
  public String getAttributeType(int index) {
    throw new UnsupportedOperationException();
  }
  
  public boolean isAttributeDefault(int index) {
    throw new UnsupportedOperationException();
  }
  
  public int nextToken() throws XmlPullParserException, IOException {
    throw new UnsupportedOperationException();
  }
  
  public void require(int type, String namespace, String name) throws XmlPullParserException, IOException {
    throw new UnsupportedOperationException();
  }
  
  public String nextText() throws XmlPullParserException, IOException {
    throw new UnsupportedOperationException();
  }
  
  public int nextTag() throws XmlPullParserException, IOException {
    throw new UnsupportedOperationException();
  }
  
  public int getAttributeCount() {
    throw new UnsupportedOperationException();
  }
  
  public String getAttributeName(int index) {
    throw new UnsupportedOperationException();
  } 
  
  public String getAttributeValue(int index) {
    throw new UnsupportedOperationException();
  }
}

