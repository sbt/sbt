/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import java.io.InputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import org.jline.nativ.Kernel32
import org.jline.utils.InfoCmp.Capability
import scala.annotation.tailrec
import Terminal.SimpleInputStream

private object WindowsSupport {
  def getConsoleMode = {
    val console = Kernel32.GetStdHandle(Kernel32.STD_INPUT_HANDLE);
    val mode = new Array[Int](1);
    if (Kernel32.GetConsoleMode(console, mode) == 0) -1 else mode.head
  }
  def setConsoleMode(mode: Int): Unit = {
    val console = Kernel32.GetStdHandle(Kernel32.STD_INPUT_HANDLE);
    Kernel32.SetConsoleMode(console, mode)
    ()
  }
  def readConsoleInput(count: Int) = {
    val console = Kernel32.GetStdHandle(Kernel32.STD_INPUT_HANDLE);
    Kernel32.readConsoleInputHelper(console, 1, false)
  }
}
/*
 * We need a special input stream for windows because special key events
 * like arrow keys are not reported by System.in. What makes this extra
 * tricky is that in canonical mode, ReadConsoleInput does not echo
 * characters, even when echo is set. The ReadConsole api does, but that
 * api isn't exposed by jansi so we can't use it without rolling our own
 * jni library or using the jna. That is more trouble than it's worth. To
 * work around the canonical mode issue, we can switch between reading using
 * ReadConsoleInput and just reading directly from the original System.in,
 * which does echo characters in canonical mode, when we enter and exit
 * raw mode.
 */
private[util] class WindowsInputStream(term: org.jline.terminal.Terminal, in: InputStream)
    extends SimpleInputStream {
  private val SHIFT_FLAG = 0x01;
  private val ALT_FLAG = 0x02;
  private val CTRL_FLAG = 0x04;

  private val RIGHT_ALT_PRESSED = 0x0001;
  private val LEFT_ALT_PRESSED = 0x0002;
  private val RIGHT_CTRL_PRESSED = 0x0004;
  private val LEFT_CTRL_PRESSED = 0x0008;
  private val SHIFT_PRESSED = 0x0010;
  private def getCapability(cap: Capability): String = term.getStringCapability(cap) match {
    case null => null
    case c    => c.replace("\\E", "\u001B")
  }
  /*
   * This function is a hybrid of jline 2 WindowsTerminal.readConsoleInput
   * and jline3 AbstractTerminal.getEscapeSequence.
   */
  private def readConsoleInput(): Array[Byte] = {
    WindowsSupport.readConsoleInput(1) match {
      case null => Array.empty
      case events =>
        val sb = new StringBuilder();
        events.foreach { event =>
          val keyEvent = event.keyEvent
          val controlKeyState = keyEvent.controlKeyState
          val isCtrl = (controlKeyState & (RIGHT_CTRL_PRESSED | LEFT_CTRL_PRESSED)) > 0;
          val isAlt = (controlKeyState & (RIGHT_ALT_PRESSED | LEFT_ALT_PRESSED)) > 0;
          val isShift = (controlKeyState & SHIFT_PRESSED) > 0;
          if (keyEvent.keyDown) {
            if (keyEvent.uchar > 0) {
              if (((keyEvent.uchar >= '@' && keyEvent.uchar <= '_') || (keyEvent.uchar >= 'a' && keyEvent.uchar <= 'z'))
                  && isAlt && !isCtrl) {
                sb.append('\u001B') // ESC
              }
              if (isShift && keyEvent.keyCode == 9) {
                getCapability(Capability.key_btab) match {
                  case null => sb.append(keyEvent.uchar)
                  case cap  => sb.append(cap)
                }
              } else {
                sb.append(keyEvent.uchar)
              }
            } else {
              // virtual keycodes: http://msdn.microsoft.com/en-us/library/windows/desktop/dd375731(v=vs.85).aspx
              // just add support for basic editing keys (no control state, no numpad keys)
              val escapeSequence = keyEvent.keyCode match {
                case 0x21 /* VK_PRIOR PageUp*/  => getCapability(Capability.key_ppage);
                case 0x22 /* VK_NEXT PageDown*/ => getCapability(Capability.key_npage);
                case 0x24 /* VK_HOME */         => getCapability(Capability.key_home)
                case 0x25 /* VK_LEFT */         => getCapability(Capability.key_left)
                case 0x26 /* VK_UP */           => getCapability(Capability.key_up)
                case 0x27 /* VK_RIGHT */        => getCapability(Capability.key_right)
                case 0x28 /* VK_DOWN */         => getCapability(Capability.key_down)
                case 0x70 /* VK_F1 */           => getCapability(Capability.key_f1)
                case 0x71 /* VK_F2 */           => getCapability(Capability.key_f2)
                case 0x72 /* VK_F3 */           => getCapability(Capability.key_f3)
                case 0x73 /* VK_F4 */           => getCapability(Capability.key_f4)
                case 0x74 /* VK_F5 */           => getCapability(Capability.key_f5)
                case 0x75 /* VK_F6 */           => getCapability(Capability.key_f6)
                case 0x76 /* VK_F7 */           => getCapability(Capability.key_f7)
                case 0x77 /* VK_F8 */           => getCapability(Capability.key_f8)
                case 0x78 /* VK_F9 */           => getCapability(Capability.key_f9)
                case 0x79 /* VK_F10 */          => getCapability(Capability.key_f10)
                case 0x7A /* VK_F11 */          => getCapability(Capability.key_f11)
                case 0x7B /* VK_F12 */          => getCapability(Capability.key_f12)
                // VK_END, VK_INSERT and VK_DELETE are not in the ansi key bindings so we
                // have to manually apply the the sequences here and in JLine3.wrap
                case 0x23 /* VK_END */ =>
                  Option(getCapability(Capability.key_end)).getOrElse("\u001B[4~")
                case 0x2D /* VK_INSERT */ =>
                  Option(getCapability(Capability.key_ic)).getOrElse("\u001B[2~")
                case 0x2E /* VK_DELETE */ =>
                  Option(getCapability(Capability.key_dc)).getOrElse("\u001B[3~")
                case _ => null
              }
              escapeSequence match {
                case null =>
                case es   => (0 until keyEvent.repeatCount.toInt).foreach(_ => sb.append(es))
              }
            }
          } else {
            // key up event
            // support ALT+NumPad input method
            if (keyEvent.keyCode == 0x12 /*VK_MENU ALT key*/ && keyEvent.uchar > 0) {
              sb.append(keyEvent.uchar);
            }
          }
        }
        sb.toString().getBytes()
    }
  }
  private[this] val raw: InputStream = new SimpleInputStream {
    val buffer = new LinkedBlockingQueue[Integer]
    @tailrec
    override def read(): Int = {
      buffer.poll match {
        case null =>
          readConsoleInput().foreach(b => buffer.put(b & 0xFF))
          if (!Thread.interrupted) read() else throw new InterruptedException
        case b => b
      }
    }
  }
  private[this] val isRaw = new AtomicBoolean(true)
  private[sbt] def setRawMode(toggle: Boolean): Unit = isRaw.set(toggle)
  override def read(): Int = if (isRaw.get) raw.read() else in.read()
}
