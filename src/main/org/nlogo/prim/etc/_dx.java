package org.nlogo.prim.etc;

import org.nlogo.agent.Turtle;
import org.nlogo.api.Constants;
import org.nlogo.nvm.Context;
import org.nlogo.nvm.Reporter;
import org.nlogo.nvm.Syntax;

public final strictfp class _dx extends Reporter {
  @Override
  public Syntax syntax() {
    return Syntax.reporterSyntax
        (Syntax.TYPE_NUMBER, "-T--");
  }

  @Override
  public Object report(Context context) {
    return report_1(context);
  }

  public double report_1(Context context) {
    double value = ((Turtle) context.agent).dx();
    return (StrictMath.abs(value) < Constants.Infinitesimal())
        ? 0 : value;
  }
}
