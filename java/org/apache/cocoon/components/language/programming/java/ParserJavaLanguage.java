package org.apache.cocoon.components.language.programming.java;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.avalon.framework.activity.Disposable;
import org.apache.avalon.framework.activity.Initializable;
import org.apache.avalon.framework.logger.LogEnabled;
import org.apache.avalon.framework.parameters.ParameterException;
import org.apache.avalon.framework.parameters.Parameters;
import org.apache.avalon.framework.service.ServiceException;
import org.apache.avalon.framework.service.ServiceManager;
import org.apache.avalon.framework.service.Serviceable;
import org.apache.avalon.framework.thread.ThreadSafe;
import org.apache.cocoon.components.classloader.ClassLoaderManager;
import org.apache.cocoon.components.language.LanguageException;
import org.apache.cocoon.components.language.markup.xsp.XSLTExtension;
import org.apache.cocoon.components.language.programming.CompiledProgrammingLanguage;
import org.apache.cocoon.components.language.programming.CompilerError;
import org.apache.cocoon.components.language.programming.LanguageCompiler;
import org.apache.cocoon.components.language.programming.Program;
import org.apache.cocoon.util.ClassUtils;
import org.apache.cocoon.util.IOUtils;
import org.apache.cocoon.util.JavaArchiveFilter;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;

public class ParserJavaLanguage extends CompiledProgrammingLanguage implements
    Initializable, ThreadSafe, Serviceable, Disposable {

  /** The class loader */
  private ClassLoaderManager classLoaderManager;

  /** The service manager */
  protected ServiceManager manager = null;

  /** Classpath */
  private String classpath;

  /** The Class Loader Class Name */
  private String classLoaderClass;

  /** Source code version */
  private int compilerComplianceLevel;

  /**
   * Return the language's canonical source file extension.
   * 
   * @return The source file extension
   */
  public String getSourceExtension() {
    return "java";
  }

  /**
   * Return the language's canonical object file extension.
   * 
   * @return The object file extension
   */
  public String getObjectExtension() {
    return "class";
  }

  /**
   * Set the configuration parameters. This method instantiates the
   * sitemap-specified <code>ClassLoaderManager</code>
   * 
   * @param params
   *          The configuration parameters
   * @throws ParameterException
   *           If the class loader manager cannot be instantiated or looked up.
   */
  public void parameterize(Parameters params) throws ParameterException {
    params.setParameter("compiler", "org.apache.cocoon.components.language.programming.java.ParserJavaCompiler");
    super.parameterize(params);
    this.classLoaderClass = params.getParameter("class-loader", "org.apache.cocoon.components.classloader.ClassLoaderManagerImpl");
    if (this.classLoaderClass != null) {
      try {
        this.classLoaderManager = (ClassLoaderManager) ClassUtils.newInstance(this.classLoaderClass);
      } catch (Exception e) {
        throw new ParameterException("Unable to load class loader: " + this.classLoaderClass, e);
      }
    } else {
      try {
        getLogger().debug("Looking up " + ClassLoaderManager.ROLE);
        this.classLoaderManager = (ClassLoaderManager) manager
            .lookup(ClassLoaderManager.ROLE);
      } catch (ServiceException e) {
        throw new ParameterException("Lookup of ClassLoaderManager failed", e);
      }
    }
    // Get the compiler compliance level (source Code version)
    String sourceVer = params.getParameter("compiler-compliance-level", "auto");
    if (sourceVer.equalsIgnoreCase("auto")) {
      this.compilerComplianceLevel = SystemUtils.JAVA_VERSION_INT;
    } else {
      try {
        compilerComplianceLevel = new Float(Float.parseFloat(sourceVer) * 100)
            .intValue();
      } catch (NumberFormatException e) {
        throw new ParameterException(
            "XSP: compiler-compliance-level parameter value not valid!", e);
      }
    }
  }

  /**
   * Set the global service manager.
   * 
   * @param manager
   *          The global service manager
   */
  public void service(ServiceManager manager) throws ServiceException {
    this.manager = manager;
  }

  public void initialize() throws Exception {

    // Initialize the classpath
    String systemBootClasspath = System.getProperty("sun.boot.class.path");
    String systemClasspath = SystemUtils.JAVA_CLASS_PATH;
    String systemExtDirs = SystemUtils.JAVA_EXT_DIRS;
    String systemExtClasspath = null;

    try {
      systemExtClasspath = expandDirs(systemExtDirs);
    } catch (Exception e) {
      getLogger().warn("Could not expand Directory:" + systemExtDirs, e);
    }

    this.classpath = ((super.classpath != null) ? File.pathSeparator
        + super.classpath : "")
        + ((systemBootClasspath != null) ? File.pathSeparator
            + systemBootClasspath : "")
        + ((systemClasspath != null) ? File.pathSeparator + systemClasspath
            : "")
        + ((systemExtClasspath != null) ? File.pathSeparator
            + systemExtClasspath : "");
  }
  
  public void addClasspath(String path) {
    File pathFile = new File(path);
    if (pathFile.isFile()) {
      this.classpath += File.pathSeparator + path;
    } else if (pathFile.isDirectory()) {
      this.classpath += File.pathSeparator + expandDirs(path);
    }
  }

  /**
   * Actually load an object program from a class file.
   * 
   * @param name
   *          The object program base file name
   * @param baseDirectory
   *          The directory containing the object program file
   * @return The loaded object program
   * @exception LanguageException
   *              If an error occurs during loading
   */
  protected Class loadProgram(String name, File baseDirectory)
      throws LanguageException {
    try {
      this.classLoaderManager.addDirectory(baseDirectory);
      return this.classLoaderManager.loadClass(name.replace(File.separatorChar,
          '.'));
    } catch (Exception e) {
      throw new LanguageException("Could not load class for program '" + name
          + "' due to a " + e.getClass().getName() + ": " + e.getMessage());
    }
  }


  @Override
  protected void compile(String filename, File baseDirectory, String encoding)
      throws LanguageException {
    compile(new String[] {filename}, baseDirectory, encoding);
  }

  /**
   * Compile a source file yielding a loadable class file.
   * 
   * @param names
   *          The object programs base file names
   * @param baseDirectory
   *          The directory containing the object program file
   * @param encoding
   *          The encoding expected in the source file or <code>null</code> if
   *          it is the platform's default encoding
   * @exception LanguageException
   *              If an error occurs during compilation
   */
  protected void compile(String[] names, File baseDirectory, String encoding)
      throws LanguageException {

    try {
      ParserJavaCompiler compiler = new ParserJavaCompiler();
      // AbstractJavaCompiler is LogEnabled
      if (compiler instanceof LogEnabled) {
        ((LogEnabled) compiler).enableLogging(getLogger());
      }
      if (compiler instanceof Serviceable) {
        ((Serviceable) compiler).service(this.manager);
      }

      final String basePath = baseDirectory.getCanonicalPath();
      String[] filepaths = new String[names.length];
      for (int i = 0; i < names.length; ++i)
        filepaths[i] = basePath + File.separator + names[i] + "." + getSourceExtension();

      compiler.setFiles(filepaths);
      compiler.setSource(basePath);
      compiler.setDestination(basePath);
      compiler.setClasspath(basePath + this.classpath);
      compiler.setCompilerComplianceLevel(compilerComplianceLevel);

      if (encoding != null) {
        compiler.setEncoding(encoding);
      }

      if (getLogger().isDebugEnabled()) {
        getLogger().debug("Compiling " + StringUtils.join(filepaths, ", "));
      }
      if (!compiler.compile()) {
        StringBuffer message = new StringBuffer("Error compiling ");
        List errors = compiler.getErrors();
        CompilerError[] compilerErrors = new CompilerError[errors.size()];
        errors.toArray(compilerErrors);

        throw new LanguageException(message.toString(), StringUtils.join(filepaths, ", "),
            compilerErrors);
      }

    } catch (IOException e) {
      getLogger().warn("Error during compilation", e);
      throw new LanguageException("Error during compilation: " + e.getMessage());
    } catch (ServiceException e) {
      getLogger().warn("Could not initialize the compiler", e);
      throw new LanguageException("Could not initialize the compiler: "
          + e.getMessage());
    }
  }

  /**
   * Unload a previously loaded class. This method simply reinstantiates the
   * class loader to ensure that a new version of the same class will be
   * correctly loaded in a future loading operation
   * 
   * @param program
   *          A previously loaded class
   * @exception LanguageException
   *              If an error occurs during unloading
   */
  public void doUnload(Object program) throws LanguageException {
    this.classLoaderManager.reinstantiate();
  }

  /**
   * Escape a <code>String</code> according to the Java string constant encoding
   * rules.
   * 
   * @param constant
   *          The string to be escaped
   * @return The escaped string
   */
  public String quoteString(String constant) {
    return XSLTExtension.escapeJavaString(constant);
  }

  /**
   * Expand a directory path or list of directory paths (File.pathSeparator
   * delimited) into a list of file paths of all the jar files in those
   * directories.
   * 
   * @param dirPaths
   *          The string containing the directory path or list of directory
   *          paths.
   * @return The file paths of the jar files in the directories. This is an
   *         empty string if no files were found, and is terminated by an
   *         additional pathSeparator in all other cases.
   */
  private String expandDirs(String dirPaths) {
    StringTokenizer st = new StringTokenizer(dirPaths, File.pathSeparator);
    StringBuffer buffer = new StringBuffer();
    while (st.hasMoreTokens()) {
      String d = st.nextToken();
      File dir = new File(d);
      if (!dir.isDirectory()) {
        // The absence of a listed directory may not be an error.
        if (getLogger().isWarnEnabled()) {
          getLogger().warn(
              "Attempted to retrieve directory listing of non-directory "
                  + dir.toString());
        }
      } else {
        File[] files = dir.listFiles(new JavaArchiveFilter());
        for (int i = 0; i < files.length; i++) {
          buffer.append(files[i]).append(File.pathSeparator);
        }
      }
    }
    return buffer.toString();
  }

  /**
   * dispose
   */
  public void dispose() {
    if (this.classLoaderClass == null && this.classLoaderManager != null) {
      manager.release(this.classLoaderManager);
      this.classLoaderManager = null;
    }
  }

  /* From CompiledProgrammingLanguage. */

  /**
   * Load object programs from files.
   * This method compiles the corresponding source files if necessary.
   *
   * @param filenames The object programs base file names
   * @param baseDirectory The directory containing the object program file
   * @param encoding The encoding expected in the source file or <code>null</code> if it is the platform's default encoding
   * @return The loaded object program corresponding to the last file name in filenames
   * @exception LanguageException If an error occurs during compilation
   */
  public ParserJavaProgram load(String[] filenames, File baseDirectory, String encoding) throws LanguageException {

    for (int i = 0; i < filenames.length; ++i) {
      String filename = filenames[i];
      // Does source file exist?
      File sourceFile = new File(baseDirectory, filename + "." + this.getSourceExtension());
      if (!sourceFile.exists()) {
          throw new LanguageException("Can't load program - File doesn't exist: " + IOUtils.getFullFilename(sourceFile));
      }
      if (!sourceFile.isFile()) {
          throw new LanguageException("Can't load program - File is not a normal file: " + IOUtils.getFullFilename(sourceFile));
      }
      if (!sourceFile.canRead()) {
          throw new LanguageException("Can't load program - File cannot be read: " + IOUtils.getFullFilename(sourceFile));
      }
    }
    
    this.compile(filenames, baseDirectory, encoding);
    String filename = null;
    Class program = null;
    
    for (int i = 0; i < filenames.length; ++i) {
      filename = filenames[i];
      if (this.deleteSources) {
        new File(baseDirectory, filename + "." + this.getSourceExtension()).delete();
      }
      program = this.loadProgram(filename, baseDirectory);
    }

    // Try to instantiate once to ensure there are no exceptions thrown in the constructor
    try {
      // Create and discard test instance
      program.newInstance();
    } catch(IllegalAccessException iae) {
      getLogger().debug("No public constructor for class " + program.getName());
    } catch(Exception e) {
      // Unload class and delete the object file, or it won't be recompiled
      // (leave the source file to allow examination).
      this.doUnload(program);
      new File(baseDirectory, filename + "." + this.getObjectExtension()).delete();
      String message = "Error while instantiating " + filename;
      getLogger().debug(message, e);
      throw new LanguageException(message, e);
    }

    return new ParserJavaProgram(program);
  }
  
}
