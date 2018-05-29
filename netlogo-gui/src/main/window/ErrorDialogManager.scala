// (C) Uri Wilensky. https://github.com/NetLogo/NetLogo

package org.nlogo.window

import java.awt.Component
import javax.swing.text.JTextComponent

import javax.swing.{ JButton, JCheckBox }

import org.nlogo.core.I18N
import org.nlogo.api.{ LogoException, Version }
import org.nlogo.nvm.{ Context, Instruction }
import org.nlogo.swing.{ BrowserLauncher, MessageDialog }
import org.nlogo.util.Utils
import org.nlogo.util.SysInfo

import scala.annotation.tailrec

case class ErrorInfo(var throwable: Throwable, var context: Option[Context] = None, var instruction: Option[Instruction] = None) {
  def ordinaryError: Boolean = throwable.isInstanceOf[LogoException]

  def hasKnownCause: Boolean = knownAncestorCause(throwable)

  def isOutOfMemory: Boolean = knownAncestorCause(throwable)

  def hasContext: Boolean = context.nonEmpty

  def errorMessage: Option[String] =
    context.flatMap(c => instruction.map(i => (c, i))).map {
      case (ctx, ins) => ctx.buildRuntimeErrorMessage(ins, throwable)
    } orElse (if (ordinaryError) Some(throwable.getMessage) else None)

  @tailrec
  private def knownAncestorCause(t: Throwable): Boolean =
    t.isInstanceOf[OutOfMemoryError] || (t.getCause != null && knownAncestorCause(t.getCause))
}

case class DebuggingInfo(var className: String, var threadName: String, var modelName: String, var eventTrace: String, var javaStackTrace: String) {
  def debugInfo =
    s"""|${Version.version}
        |main: $className
        |thread: $threadName
        |${SysInfo.getVMInfoString}
        |${SysInfo.getOSInfoString}
        |${SysInfo.getScalaVersionString}
        |${SysInfo.getJOGLInfoString}
        |${SysInfo.getGLInfoString}
        |model: $modelName""".stripMargin

  def detailedInformation: String = s"""|$javaStackTrace
                                        |$debugInfo
                                        |
                                        |$eventTrace""".stripMargin
}

class ErrorDialogManager(owner: Component) {
  private val debuggingInfo = DebuggingInfo("", "", "", "", "")
  private val errorInfo = ErrorInfo(null)
  private val unknownDialog = new UnknownErrorDialog(owner)
  private val logoDialog    = new LogoExceptionDialog(owner)
  private val memoryDialog  = new OutOfMemoryDialog(owner)

  debuggingInfo.className = owner.getClass.getName

  def setModelName(name: String): Unit = {
    debuggingInfo.modelName = name
  }

  def alreadyVisible: Boolean = {
    Seq(unknownDialog, logoDialog, memoryDialog).exists(_.isVisible)
  }

  def show(context: Context, instruction: Instruction, thread: Thread, throwable: Throwable): Unit = {
      debuggingInfo.threadName     = thread.getName
      debuggingInfo.eventTrace     = Event.recentEventTrace()
      debuggingInfo.javaStackTrace = Utils.getStackTrace(throwable)
      errorInfo.throwable   = throwable
      errorInfo.context     = Option(context)
      errorInfo.instruction = Option(instruction)
      throwable match {
        case l: LogoException             => logoDialog.doShow(errorInfo, debuggingInfo)
        case _ if errorInfo.isOutOfMemory => memoryDialog.doShow()
        case _                            => unknownDialog.doShow(errorInfo, debuggingInfo)
      }
  }

  // This was added to work around https://bugs.openjdk.java.net/browse/JDK-8198809,
  // which appears only in Java 8u162 and should be resolved in 8u172.
  // In general, this method should be used as a safety valve for non-fatal exceptions which
  // are Java's fault (this bug matches that description to a tee, but there are
  // many other bugs of this sort). - RG 3/2/18
  def safeToIgnore(t: Throwable): Boolean = {
    t match {
      case j: java.awt.IllegalComponentStateException =>
        val classAndMethodNames = Seq(
          "java.awt.Component"                                         -> "getLocationOnScreen_NoTreeLock",
          "java.awt.Component"                                         -> "getLocationOnScreen",
          "javax.swing.text.JTextComponent$InputMethodRequestsHandler" -> "getTextLocation",
          "sun.awt.im.InputMethodContext"                              -> "getTextLocation",
          "sun.awt.windows.WInputMethod$1"                             -> "run")
        val stackTraceClassAndMethodNames =
          j.getStackTrace.take(5).map(ste => ste.getClassName -> ste.getMethodName).toSeq
        classAndMethodNames == stackTraceClassAndMethodNames
      case _ => false
    }
  }
}

class CopyButton(textComp: JTextComponent) extends JButton(I18N.gui.get("menu.edit.copy")) {
  addActionListener { _ =>
    textComp.select(0, textComp.getText.length)
    textComp.copy()
    textComp.setCaretPosition(0)
  }
}

abstract class ErrorDialog(owner: Component, dialogTitle: String)
extends MessageDialog(owner, I18N.gui.get("common.buttons.dismiss")) {
  protected var message = ""
  protected var details = ""

  protected def doShow(showDetails: Boolean): Unit = {
    val text = if (showDetails) message + "\n\n" + details else message
    val lines = text.split('\n')
    val maxColumns = lines.maxBy(_.length).length

    val padding = 2
    val rows = lines.length.max(5).min(15)
    val columns = (maxColumns + padding).min(70)
    doShow(dialogTitle, text, rows, columns)
  }
}

class UnknownErrorDialog(owner: Component)
extends ErrorDialog(owner, "Internal Error") {
  private var suppressed = false

  message = I18N.gui.get("error.dialog.pleaseReport")

  def doShow(errorInfo: ErrorInfo, debugInfo: DebuggingInfo): Unit = if (!suppressed) {
    details = debugInfo.detailedInformation
    doShow(true)
  }

  override def makeButtons = {
    val suppressButton = new JButton(I18N.gui.get("error.dialog.suppress"))
    suppressButton.addActionListener { _ =>
      suppressed = true
      setVisible(false)
    }
    super.makeButtons ++ Seq(new CopyButton(textArea), suppressButton)
  }
}

class LogoExceptionDialog(owner: Component)
extends ErrorDialog(owner, I18N.gui.get("common.messages.error.runtimeError")) {
  private lazy val checkbox = {
    val b = new JCheckBox(I18N.gui.get("error.dialog.showInternals"))
    b.addItemListener(_ => doShow(b.isSelected))
    b
  }

  def doShow(errorInfo: ErrorInfo, debugInfo: DebuggingInfo): Unit = {
    message = errorInfo.errorMessage.getOrElse("")
    details = debugInfo.detailedInformation
    doShow(checkbox.isSelected)
  }

  override def makeButtons = super.makeButtons ++ Seq(new CopyButton(textArea), checkbox)
}

class OutOfMemoryDialog(owner: Component)
extends ErrorDialog(owner, I18N.gui.get("error.dialog.outOfMemory.title")) {
  message = I18N.gui.get("error.dialog.outOfMemory")

  def doShow(): Unit = {
    doShow(false)
  }

  override def makeButtons = {
    val openFAQ = new JButton(I18N.gui.get("error.dialog.openFAQ"))
    val baseFaqUrl = BrowserLauncher.docPath("faq.html")
    openFAQ.addActionListener(_ => BrowserLauncher.openPath(owner, baseFaqUrl, "howbig"))
    super.makeButtons :+ openFAQ
  }
}
