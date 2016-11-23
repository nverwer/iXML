package org.apache.cocoon.components.language.programming.java;

import java.util.List;

public class ParserJavaCompiler extends Javac {

  /**
   * Multiple source program filenames
   */
  protected String[] files;

  /**
   * Set the name of the files containing the source programs
   *
   * @param files The names of the files containing the source programs
   */
  public void setFiles(String[] files) {
      this.files = files;
  }

  /**
   * Set the name of the file containing the source program
   *
   * @param file The name of the file containing the source program
   */
  @Override
  public void setFile(String file) {
      this.file = file;
      this.files = new String[] {file};
  }

  /**
   * Copy arguments to a string array
   *
   * @param arguments The compiler arguments
   * @return A string array containing compilation arguments
   */
  @Override
  protected String[] toStringArray(List arguments) {
    int i;
    String[] args = new String[arguments.size() + files.length];
    for (i = 0; i < arguments.size(); i++) {
      args[i] = (String)arguments.get(i);
    }
    for (i = 0; i < files.length; ++i) {
      args[arguments.size()+i] = files[i];
    }
    return args;
  }

}
