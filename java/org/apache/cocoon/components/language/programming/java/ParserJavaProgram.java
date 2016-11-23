package org.apache.cocoon.components.language.programming.java;

import org.apache.avalon.excalibur.component.ComponentHandler;
import org.apache.avalon.excalibur.component.LogkitLoggerManager;
import org.apache.avalon.excalibur.component.RoleManager;
import org.apache.avalon.framework.component.ComponentManager;
import org.apache.avalon.framework.configuration.DefaultConfiguration;
import org.apache.avalon.framework.context.Context;
import org.apache.cocoon.components.language.generator.CompiledComponent;
import org.apache.cocoon.components.language.programming.Program;

public class ParserJavaProgram implements Program {

  protected Class program;

  public ParserJavaProgram(Class program) {
      this.program = program;
  }

  public String getName() {
      return program.getName();
  }

  public ComponentHandler getHandler(ComponentManager manager,
                                     Context context,
                                     RoleManager roles,
                                     LogkitLoggerManager logKitManager)
          throws Exception {

      return ComponentHandler.getComponentHandler(
              program,
              new DefaultConfiguration("", "GeneratorSelector"),
              manager, context, roles, logKitManager, null, "N/A");
  }

  public CompiledComponent newInstance() throws Exception {
      return (CompiledComponent)this.program.newInstance();
  }
  
  public Class getProgram() {
    return this.program;
  }
}
