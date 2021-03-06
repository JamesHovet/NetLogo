// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.prim.etc;

import org.nlogo.api.LogoException;
import org.nlogo.core.LogoList;
import org.nlogo.core.Syntax;
import org.nlogo.nvm.Context;
import org.nlogo.nvm.Reporter;

public final strictfp class _extractrgb
    extends Reporter {


  @Override
  public Object report(final org.nlogo.nvm.Context context) throws LogoException {

    return report_1(context, argEvalDoubleValue(context, 0));
  }

  public LogoList report_1(final Context context, double color) {
    if (color < 0 || color >= 140)  //out of bounds
    {
      color = org.nlogo.api.Color.modulateDouble(color);  //modulate the color
    }
    return org.nlogo.api.Color.getRGBListByARGB
        (org.nlogo.api.Color.getARGBbyPremodulatedColorNumber(color));
  }
}
