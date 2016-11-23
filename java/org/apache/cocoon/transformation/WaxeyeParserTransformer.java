package org.apache.cocoon.transformation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.transform.OutputKeys;

import org.apache.avalon.framework.configuration.Configuration;
import org.apache.avalon.framework.configuration.ConfigurationException;
import org.apache.avalon.framework.context.Context;
import org.apache.avalon.framework.context.ContextException;
import org.apache.avalon.framework.context.Contextualizable;
import org.apache.avalon.framework.parameters.ParameterException;
import org.apache.avalon.framework.parameters.Parameterizable;
import org.apache.avalon.framework.parameters.Parameters;
import org.apache.cocoon.Constants;
import org.apache.cocoon.ProcessingException;
import org.apache.cocoon.components.LifecycleHelper;
import org.apache.cocoon.components.language.LanguageException;
import org.apache.cocoon.components.language.programming.java.ParserJavaLanguage;
import org.apache.cocoon.components.language.programming.java.ParserJavaProgram;
import org.apache.cocoon.environment.SourceResolver;
import org.apache.cocoon.xml.AttributesImpl;
import org.apache.commons.lang.StringUtils;
import org.apache.excalibur.source.Source;
import org.apache.excalibur.source.SourceException;
import org.waxeye.ast.IAST;
import org.waxeye.ast.IASTVisitor;
import org.waxeye.ast.IChar;
import org.waxeye.ast.IEmpty;
import org.waxeye.ast.Position;
import org.waxeye.input.InputBuffer;
import org.waxeye.parser.ParseError;
import org.waxeye.parser.ParseResult;
import org.waxeye.parser.Parser;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import com.rakensi.AsciiUtils;

/**
 * A transformer that parses its input using Waxeye.
 * 
 * The transformer takes the following configuration parameters:
 * <ul>
 *   <li>waxeye.bin The location of the Waxeye binary.</li>
 * </ul>
 * 
 * The grammar is given as the src attribute of the map:transform element.
 * 
 * The transformer takes the following parameters:
 * <ul>
 *   <li>namespaceURI The namespace of both the trigger element and the generated (non-terminal) elements.</li>
 *   <li>parseElementTag The name of the trigger element.</li>
 *   <li>modular Set to true if the grammar is modular. (Default is false.)</li>
 *   <li>completeMatch Set to true if the complete input text must be parsed as one matched fragment. (Default is false.)</li>
 *   <li>adjacentMatches Set to true if the complete input must be consumed as adjacent matched fragments. (Default is false.)</li>
 *   <li>parseErrors Set to true to include errors in the output and not trigger an exception. (Default is false.)</li>
 *   <li>showParseTree Set to true to show the parse tree in an XML comment in the output. (Default is false.)</li>
 *   <li>keepXML If true, the XML in the trigger element is serialized and passed as the input to the scanner.
 *       Otherwise only the text from parsed fragments is kept.</li>
 *   <li>toASCII Set to true if characters in the input must be converted to low ASCII characters, removing diacrites and ligatures.
 *       (Default is false.)</li>
 * </ul>
 * If `completeMatch` is true, `adjacentMatches` is ignored because there must be only one match.
 * If `adjacentMatches` is true, there may be multiple adjacent matched fragments, but no unmatched text.
 * If both `completeMatch` and `adjacentMatches` are false,
 * the result is a mix of unmatched text and an arbitrary number of matched fragments.
 * In this case, no parsing errors will be generated, and `parseErrors` is ignored.
 * 
 * @author Rakensi
 * This code is part of the "Link eXtractor", a project of the Publications Office of the Netherlands.
 */

public class WaxeyeParserTransformer extends AbstractSAXPipelineTransformer 
  implements Contextualizable, Parameterizable
{
  
  public static final String LINKEXTRACTOR_NAMESPACE_URI = "http://linkeddata.overheid.nl/lx/";
  public static final String WAXEYE_BIN_PARAMETER_NAME = "waxeye.bin";
  public static final String DEFAULT_PARSE_ELEMENT_TAG = "parse";
  public static final String PARSE_ELEMENT_TAG_PARAMETER_NAME = "parseElementTag";
  public static final String COMPLETE_MATCH_PARAMETER_NAME = "completeMatch";
  public static final String ADJACENT_MATCHES_PARAMETER_NAME = "adjacentMatches";
  public static final String PARSE_ERRORS_PARAMETER_NAME = "parseErrors";
  public static final String SHOW_PARSE_TREE_PARAMETER_NAME = "showParseTree";
  public static final String MODULAR_PARAMETER_NAME = "modular";
  public static final String KEEP_XML_PARAMETER_NAME = "keepXML";
  public static final String TO_ASCII_PARAMETER_NAME = "toASCII";
  public static final String ERROR_ELEMENT_TAG = "ERROR";
  public static final String MARKER_ELEMENT_TAG = "ERROR_POSITION";

  // The global Parser store, mapping grammars to Parsers.
  private static Map<String, Parser<?>> parserStore = new HashMap<String, Parser<?>>();

  private String parseElementTag = null;
  private boolean modular;
  private boolean completeMatch;
  private boolean adjacentMatches;
  private boolean parseErrors;
  private boolean showParseTree;
  private boolean keepXML = false;
  private boolean toASCII = false;
  private String grammar = null;
  private Source grammarSource = null;
  private String waxeyePath; /* Path to the Waxeye executable. */
  private File workDir; /* The working directory. */
  private File javaCodeDir; /* Directory to store Java sources and classes. */
  private Parser<?> parser;
  private int isParsing;
  
  private Configuration configuration;
  private Context context;
    
  /*
   * Construct an instance of this transformer.
   */
  public WaxeyeParserTransformer() {
    this.defaultNamespaceURI = LINKEXTRACTOR_NAMESPACE_URI;
  }

  /* (non-Javadoc)
   * @see org.apache.cocoon.transformation.AbstractSAXTransformer#configure(org.apache.avalon.framework.configuration.Configuration)
   */
  @Override
  public void configure(Configuration configuration) throws ConfigurationException {
    super.configure(configuration);
    this.configuration = configuration;
  }

  /**
   * Provide component with parameters.
   * @param parameters the parameters
   * @throws ParameterException if parameters are invalid
   */
  public void parameterize(Parameters parameters) throws ParameterException {
    this.waxeyePath = interpolateModules(parameters.getParameter(WAXEYE_BIN_PARAMETER_NAME));
  }

  /** Contextualize this class */
  @Override
  public void contextualize(Context context) throws ContextException {
    if (this.workDir == null) {
      this.workDir = (File) context.get(Constants.CONTEXT_WORK_DIR);
    }
  }

  /* (non-Javadoc)
   * @see org.apache.cocoon.transformation.AbstractSAXTransformer#setup(org.apache.cocoon.environment.SourceResolver, java.util.Map, java.lang.String, org.apache.avalon.framework.parameters.Parameters)
   */
  @Override
  public void setup(SourceResolver resolver, Map objectModel, String src, Parameters params)
  throws ProcessingException, SAXException, IOException {
    super.setup(resolver, objectModel, src, params);
    this.parseElementTag = params.getParameter(PARSE_ELEMENT_TAG_PARAMETER_NAME, DEFAULT_PARSE_ELEMENT_TAG);
    this.modular = params.getParameterAsBoolean(MODULAR_PARAMETER_NAME, false);
    this.completeMatch = params.getParameterAsBoolean(COMPLETE_MATCH_PARAMETER_NAME, false);
    this.adjacentMatches = params.getParameterAsBoolean(ADJACENT_MATCHES_PARAMETER_NAME, false);
    this.parseErrors = params.getParameterAsBoolean(PARSE_ERRORS_PARAMETER_NAME, false);
    this.showParseTree = params.getParameterAsBoolean(SHOW_PARSE_TREE_PARAMETER_NAME, false);
    this.keepXML = params.getParameterAsBoolean(KEEP_XML_PARAMETER_NAME, false);
    this.toASCII = params.getParameterAsBoolean(TO_ASCII_PARAMETER_NAME, false);
    this.isParsing = 0;
    /* Determine where the grammar is located. */
    this.grammar = src;
    try {
      this.grammarSource = this.resolver.resolveURI(this.grammar);
    } catch (SourceException se) {
      throw new ProcessingException("Error during resolving of '"+src+"'.", se);
    }
    setupParser();
  }

  /**
   * Get the parser for this grammar in this.parser.
   * This may involve generating the parser from the grammar.
   * @throws ProcessingException
   * @throws IOException
   * @throws MalformedURLException
   */
  private void setupParser() throws ProcessingException, IOException, MalformedURLException {
    /* Determine grammar location. */
    String grammarFilePath = this.grammarSource.getURI();
    if (!grammarFilePath.startsWith("file://")) {
      throw new ProcessingException("At the moment a grammar can only be a file, with a file URI, but you gave me "+grammarFilePath);
    }
    if (grammarFilePath.matches("file:///[A-Za-z]:/.*")) { // Windoze: file:///C:/path => C:/path
      grammarFilePath = grammarFilePath.substring("file:///".length());
    } else { // OSuX: file:///path => /path
      grammarFilePath = grammarFilePath.substring("file://".length());
    }
    /* Make a readable file object for the grammar. */
    File grammarFile = new File(grammarFilePath);
    if (!grammarFile.canRead()) {
      throw new ProcessingException("The grammar file "+grammarFilePath+" cannot be read.");
    }
    /* Determine the directory for the generated Java code, and create it. */
    /* Drop the extension (.waxeye) from the grammar name and replace funny characters. */
    String javaDirName = this.grammar.replaceFirst("\\.[^./]*$", "").replaceAll("[^\\./_A-Za-z0-9]", "_");
    this.javaCodeDir = new File(this.workDir, javaDirName);
    /* Compile the grammar if one of the grammar files is newer than the code directory. */
    long grammarChanged = grammarChanged(grammarFile);
    long parserChanged = parserChanged(this.javaCodeDir);
    if (!this.javaCodeDir.exists() || grammarChanged > parserChanged) {
      this.getLogger().info("Parser code must be generated for waxeye grammar: "+grammarFilePath);
      long startTime = System.currentTimeMillis();
      compileGrammar(grammarFilePath);
      long elapsedTime = System.currentTimeMillis()-startTime;
      getLogger().info("Generating the parser for "+this.grammar+" took "+elapsedTime+" milliseconds.");
      parserStore.remove(grammar); // Force re-loading of the parser.
    } else {
      this.getLogger().info("Re-using generated parser code for waxeye grammar: "+grammarFilePath);
    }
    /* Load the parser for this WaxeyeParserTransformer from the cache or compile the generated Java code. */ 
    if (parserStore.containsKey(grammar)) {
      this.parser = parserStore.get(grammar);
      getLogger().info("The parser for "+this.grammar+" was already loaded.");
    } else {
      long startTime = System.currentTimeMillis();
      loadParser();
      long elapsedTime = System.currentTimeMillis()-startTime;
      getLogger().info("Loading the parser for "+this.grammar+" took "+elapsedTime+" milliseconds.");
      parserStore.put(grammar, this.parser);
    }
  }
  
  /* Determine when the grammar file or one of the sub-grammar files was changed.
   * @result Most recent timestamp of one of the files.
   */
  private long grammarChanged(File grammarFile) throws ProcessingException {
    long grammarChanged = grammarFile.lastModified();
    if (this.modular) {
      Pattern subGrammar = Pattern.compile("\"([^\"]+)\"");
      try {
        BufferedReader grammarFileReader = new BufferedReader(new FileReader(grammarFile));
        String line;
        while ((line = grammarFileReader.readLine()) != null) {
          line = line.replaceFirst(";;.*", "");
          Matcher subGr = subGrammar.matcher(line);
          while (subGr.find()) {
            String subGrFileName = subGr.group(1);
            File subGrFile = new File(grammarFile.getParentFile(), subGrFileName);
            long subGrMod = subGrFile.lastModified();
            if (subGrMod > grammarChanged) grammarChanged = subGrMod;
          }
        }
        grammarFileReader.close();
      } catch (Exception e) {
        throw new ProcessingException(e);
      }
    }
    return grammarChanged;
  }
  
  /* Determine when the parser file was changed.
   * @result Timestamp of the parser file.
   */
  private long parserChanged(File javaCodeDir) {
    File parserFile = new File(javaCodeDir, "Parser.java");
    if (parserFile.exists())
      return parserFile.lastModified();
    else
      return 0L;
  }

  /* Compile the Waxeye grammar into Java code using the Waxeye executable.
   * This produces .java source-code files.
   */
  private void compileGrammar(String grammarFilePath)
      throws IOException, ProcessingException, MalformedURLException {
    String javaCodeDirPath = javaCodeDir.getAbsolutePath();
    if (!javaCodeDir.mkdirs() && !javaCodeDir.exists()) {
      throw new ProcessingException("Unable to create directory ["+javaCodeDirPath+"] for Waxeye java files.");
    }
    /* Compile the grammar into Java code. */
    // The String[] waxeyeCommand must not contain empty strings, which will give an empty argument on OSX.
    String[] waxeyeCommand;
    if (modular)
      waxeyeCommand = new String[]{waxeyePath, "-g", "java", javaCodeDirPath, "-m", grammarFilePath};
    else
      waxeyeCommand = new String[]{waxeyePath, "-g", "java", javaCodeDirPath, grammarFilePath};
    this.getLogger().info("Compiling waxeye grammar: "+StringUtils.join(waxeyeCommand, " "));
    String waxeyeOutput = "";
    try {
      Process waxeye = new ProcessBuilder(waxeyeCommand).redirectErrorStream(true).start();
      BufferedReader waxeyeOutputReader = new BufferedReader(new InputStreamReader(waxeye.getInputStream()));
      for (String line = waxeyeOutputReader.readLine(); line != null; line = waxeyeOutputReader.readLine()) {
        waxeyeOutput += line+"\n";
      }
      waxeye.waitFor();
      if (waxeye.exitValue() != 0) {
        throw new ProcessingException("Waxeye process exited with error code: "+waxeye.exitValue());
      }
    } catch (Throwable ex) {
      throw new ProcessingException("Error compiling waxeye grammar: "+ex.getMessage()+"\n"+waxeyeOutput, ex);
    }
    this.getLogger().info(waxeyeOutput);
  }

  /* Compile the Java files for the grammar and loads the class-files.
   * It then makes an instance of the Parser class and puts it in this.parser.
   */
  private void loadParser() throws ProcessingException, MalformedURLException,
      IOException {
    /* Compile the Java files into a class. */
    LifecycleHelper lch = new LifecycleHelper(this.getLogger(), context, manager, configuration);
    ParserJavaLanguage java = new ParserJavaLanguage();
    try {
      lch.setupComponent(java);
    } catch (Exception e) {
      throw new ProcessingException("Error setting up the JavaLanguage component.", e);
    }
    java.addClasspath(this.resolver.resolveURI("context://WEB-INF/lib").getURI().replaceFirst("^file://", ""));
    ParserJavaProgram parserClass;
    try {
      // The load method compiles (if necessary) and loads.
      parserClass = java.load(new String[] {"Type", "Parser"}, javaCodeDir, "UTF-8");
    } catch (LanguageException e) {
      throw new ProcessingException("Error compiling or loading the parser.", e);
    }
    try {
      LifecycleHelper.decommission(java);
    } catch (Exception e) {
      throw new ProcessingException("Can't stop Java!", e);
    }
    /* Instantiate the parser. */
    try {
      this.parser = (Parser<?>)parserClass.getProgram().newInstance();
    } catch (Throwable e) {
      throw new ProcessingException("Error instantiating the generated Java Parser class.", e);
    }
  }

  /**
   * Recycle the transformer by removing references.
   */
  public void recycle() {
    if (this.grammarSource != null) {
      this.resolver.release(this.grammarSource);
      this.grammarSource = null;
    }
    super.recycle();
  }

  /**
   * Properties for XML serialization of parsed fragments.
   */
  private static Properties propertiesForXML() {
    final Properties format = new Properties();
    format.put(OutputKeys.METHOD, "xml");
    format.put(OutputKeys.OMIT_XML_DECLARATION, "yes");
    format.put(OutputKeys.INDENT, "no");
    format.put(OutputKeys.ENCODING, "UTF-8");
    return format;
  }

  /*
   * @see org.apache.cocoon.transformation.AbstractSAXTransformer#startTransformingElement(java.lang.String, java.lang.String, java.lang.String, org.xml.sax.Attributes)
   */
  @Override
  public void startTransformingElement(String uri, String name, String raw, Attributes attr)
      throws ProcessingException, IOException, SAXException {
    if (name.equals(parseElementTag)) {
      sendStartElementEventNS(name, attr);
      if (isParsing++ == 0) {
        if (keepXML) {
          /* This will repeat some but not all namespace-prefix declarations.
             Therefore it causes weird bugs. Fix this in AbstractSAXPipelineTransformer. */
          startSerializedXMLRecording(propertiesForXML());
        } else {
          startTextRecording();
        }
      }
    } else if (ignoreEventsCount == 0) {
      contentHandler.startElement(uri, name, raw, attr);
    }
  }

  /*
   * @see org.apache.cocoon.transformation.AbstractSAXTransformer#endTransformingElement(java.lang.String, java.lang.String, java.lang.String)
   */
  @Override
  public void endTransformingElement(String uri, String name, String raw)
      throws ProcessingException, IOException, SAXException {
    if (name.equals(parseElementTag)) {
      if (--isParsing == 0) {
        String fragment = keepXML ? endSerializedXMLRecording() : endTextRecording();
        parseFragment(fragment);
      }
      sendEndElementEventNS(name);
    } else if (ignoreEventsCount == 0) {
      contentHandler.endElement(uri, name, raw);
    }
  }

  private void parseFragment(String fragment) throws SAXException, ProcessingException {
    parser.setEofCheck(this.completeMatch);
    boolean allowUnmatchedText = !(this.completeMatch || this.adjacentMatches);
    final InputBuffer input;
    if (this.toASCII)
      input = new InputBuffer(AsciiUtils.normalize(fragment).toCharArray());
    else
      input = new InputBuffer(fragment.toCharArray());
    int start = 0;
    int end = fragment.length();
    StringBuilder unmatched = new StringBuilder(); // Collects unmatched characters, up to the next match.
    while (start < end) {
      // Skip spaces.
      if (allowUnmatchedText)
        while (start < end && Character.isWhitespace(fragment.charAt(start))) {
          unmatched.append(fragment.charAt(start++));
        }
      if (start < end) {
        // input[start] points to the start from where we will match.
        input.setPosition(start);
        final ParseResult<?> parseResult = parser.parse(input);
        // Parse errors are significant if completeMatch or adjacentMatches.
        if (!allowUnmatchedText && parseResult.getError() != null) {
          try {
            new XmlVisitor(this, parseResult, fragment, start); // This will throw an exception.
          } catch (ProcessingException pe) {
            if (parseErrors) return;
            else throw pe;
          }
        // Create XML for a non-empty match.
        } else if (parseResult.getAST() != null && parseResult.getAST().getChildren().size() > 0) {
          sendText(unmatched);
          if (showParseTree) sendCommentEvent(parseResult.toString());
          new XmlVisitor(this, parseResult, fragment, start);
          start = parseResult.getAST().getPosition().getEndIndex();
        // Skip unmatched text if there is an ignored error or empty match.
        } else if (allowUnmatchedText) {
          char unmatchedChar = fragment.charAt(start++);
          unmatched.append(unmatchedChar);
          // If the current character was part of a word, skip the rest of the word.
          if (Character.isLetterOrDigit(unmatchedChar)) {
            while (start < end && Character.isLetterOrDigit(fragment.charAt(start))) {
              unmatched.append(fragment.charAt(start++));
            }
          }
        // There is an empty match, apparently the grammar allows that.
        } else {
          throw new ProcessingException(this.source+": The grammar only matches an empty string, no parsing progress can be made.");
        }
      }
    }
    sendText(unmatched);
  }
  
  private void sendText(StringBuilder sb) throws SAXException {
    if (sb.length() > 0) {
      sendTextEvent(sb.toString());
      sb.delete(0, sb.length());
    }
  }
  
  public ContentHandler getContentHandler() {
    return contentHandler;
  }


  private class XmlVisitor implements IASTVisitor {
    
    private final WaxeyeParserTransformer xml;
    private final String fragment;
    private StringBuilder buf;
    
    public XmlVisitor(WaxeyeParserTransformer xml, ParseResult<?> parseResult, String fragment, int start)
        throws ProcessingException {
      this.xml = xml;
      this.fragment = fragment;
      if (parseResult.getAST() != null) {
        this.buf = new StringBuilder();
        parseResult.getAST().acceptASTVisitor(this);
      } else if (parseResult.getError() != null) {
        String message = "Parser error: "+parseResult.getError().toString()+"\n"+
                         "Parsing ["+fragment.substring(start, Math.min(fragment.length(), start+12))+"]";
        try {
          error(parseResult.getError(), fragment);
        } catch (SAXException e) {
          throw new ProcessingException(message, e);
        }
        throw new ProcessingException(message);
      } else {
        throw new ProcessingException("Unknown error occurred during parsing.");
      }
    }
    
    public void error(ParseError error, String fragment) throws SAXException {
      AttributesImpl attrs = new AttributesImpl();
      attrs.addCDATAAttribute("NT", error.getNT());
      attrs.addCDATAAttribute("line", ""+error.getLine());
      attrs.addCDATAAttribute("column", ""+error.getColumn());
      attrs.addCDATAAttribute("position", ""+error.getPosition());
      attrs.addCDATAAttribute("message", error.toString());
      this.xml.sendStartElementEventNS(ERROR_ELEMENT_TAG, attrs);
      this.xml.sendTextEvent(fragment.substring(0, error.getPosition()));
      this.xml.sendStartElementEventNS(MARKER_ELEMENT_TAG);
      this.xml.sendEndElementEventNS(MARKER_ELEMENT_TAG);
      this.xml.sendTextEvent(fragment.substring(error.getPosition()));
      this.xml.sendEndElementEventNS(ERROR_ELEMENT_TAG);
    }

    public void visitAST(IAST<?> tree) {
      try {
        outputChars();
        Position pos = tree.getPosition();
        AttributesImpl attrs = new AttributesImpl();
        attrs.addCDATAAttribute("start", ""+pos.getStartIndex());
        attrs.addCDATAAttribute("end", ""+pos.getEndIndex());
        this.xml.sendStartElementEventNS(tree.getType().toString(), attrs);
        for (IAST<?> child : tree.getChildren()) {
          child.acceptASTVisitor(this);
        }
        outputChars();
        this.xml.sendEndElementEventNS(tree.getType().toString());
      } catch (SAXException e) {
        getLogger().error(e.getMessage());
      }
    }

    public void visitEmpty(IEmpty tree) {
      outputChars();
    }

    public void visitChar(IChar tree) {
      int pos = tree.getPos() - 1;
      //if (!AsciiUtils.normalize(""+this.fragment.charAt(pos)).equals(""+tree.getValue()))
      //  throw new RuntimeException("Fragment "+this.fragment+" does not match "+tree.getValue()+" at position "+pos);
      this.buf.append(this.fragment.charAt(pos)); // was: tree.getValue()
    }
    
    private void outputChars() {
      if (buf.length() > 0) {
        try {
          this.xml.sendTextEvent(this.buf.toString());
        } catch (Exception e) {
          throw new RuntimeException(e);
        } finally {
          this.buf.delete(0, buf.length());
        }
      }
    }
    
  }
}
