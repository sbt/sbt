/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import java.io.InputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import org.fusesource.jansi.internal.Kernel32.{ KEY_EVENT_RECORD }
import org.fusesource.jansi.internal.WindowsSupport
import org.jline.utils.InfoCmp.Capability
import scala.annotation.tailrec
import Terminal.SimpleInputStream

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
  private val NUMLOCK_ON = 0x0020;
  private val SCROLLLOCK_ON = 0x0040;
  private val CAPSLOCK_ON = 0x0080;
  private def getCapability(cap: Capability): String =
    term.getStringCapability(cap)
  private def readConsoleInput(): Array[Byte] = {
    WindowsSupport.readConsoleInput(1) match {
      case null => Array.empty
      case events =>
        val sb = new StringBuilder();
        events.foreach { event =>
          val keyEvent = event.keyEvent
          if (keyEvent.keyDown) {
            if (keyEvent.uchar > 0) {
              val altState = KEY_EVENT_RECORD.LEFT_ALT_PRESSED | KEY_EVENT_RECORD.RIGHT_ALT_PRESSED;
              val ctrlState = KEY_EVENT_RECORD.LEFT_CTRL_PRESSED | KEY_EVENT_RECORD.RIGHT_CTRL_PRESSED;
              if (((keyEvent.uchar >= '@' && keyEvent.uchar <= '_') || (keyEvent.uchar >= 'a' && keyEvent.uchar <= 'z'))
                  && ((keyEvent.controlKeyState & altState) != 0) && ((keyEvent.controlKeyState & ctrlState) == 0)) {
                sb.append('\u001B') // ESC
              }

              sb.append(keyEvent.uchar)
            } else {
              val keyState = keyEvent.controlKeyState
              // virtual keycodes: http://msdn.microsoft.com/en-us/library/windows/desktop/dd375731(v=vs.85).aspx
              // just add support for basic editing keys (no control state, no numpad keys)
              val escapeSequence: String = keyEvent.keyCode match {
                case 0x08 => // VK_BACK BackSpace
                  if ((keyState & ALT_FLAG) > 0) "\\E^H"
                  else getCapability(Capability.key_backspace)
                case 0x09 =>
                  if ((keyState & SHIFT_FLAG) > 0) getCapability(Capability.key_btab)
                  else null
                case 0x21 => // VK_PRIOR PageUp
                  getCapability(Capability.key_ppage);
                case 0x22 => // VK_NEXT PageDown
                  getCapability(Capability.key_npage);
                case 0x23 => // VK_END
                  if (keyState > 0) "\\E[1;%p1%dF" else getCapability(Capability.key_end)
                case 0x24 => // VK_HOME
                  if (keyState > 0) "\\E[1;%p1%dH" else getCapability(Capability.key_home)
                case 0x25 => // VK_LEFT
                  if (keyState > 0) "\\E[1;%p1%dD" else getCapability(Capability.key_left)
                case 0x26 => // VK_UP
                  if (keyState > 0) "\\E[1;%p1%dA" else getCapability(Capability.key_up)
                case 0x27 => // VK_RIGHT
                  if (keyState > 0) "\\E[1;%p1%dC" else getCapability(Capability.key_right)
                case 0x28 => // VK_DOWN
                  if (keyState > 0) "\\E[1;%p1%dB" else getCapability(Capability.key_down)
                case 0x2D => // VK_INSERT
                  getCapability(Capability.key_ic)
                case 0x2E => // VK_DELETE
                  getCapability(Capability.key_dc)
                case 0x70 => // VK_F1
                  if (keyState > 0) "\\E[1;%p1%dP" else getCapability(Capability.key_f1)
                case 0x71 => // VK_F2
                  if (keyState > 0) "\\E[1;%p1%dQ" else getCapability(Capability.key_f2)
                case 0x72 => // VK_F3
                  if (keyState > 0) "\\E[1;%p1%dR" else getCapability(Capability.key_f3)
                case 0x73 => // VK_F4
                  if (keyState > 0) "\\E[1;%p1%dS" else getCapability(Capability.key_f4)
                case 0x74 => // VK_F5
                  if (keyState > 0) "\\E[15;%p1%d~" else getCapability(Capability.key_f5)
                case 0x75 => // VK_F6
                  if (keyState > 0) "\\E[17;%p1%d~" else getCapability(Capability.key_f6)
                case 0x76 => // VK_F7
                  if (keyState > 0) "\\E[18;%p1%d~" else getCapability(Capability.key_f7)
                case 0x77 => // VK_F8
                  if (keyState > 0) "\\E[19;%p1%d~" else getCapability(Capability.key_f8)
                case 0x78 => // VK_F9
                  if (keyState > 0) "\\E[20;%p1%d~" else getCapability(Capability.key_f9)
                case 0x79 => // VK_F10
                  if (keyState > 0) "\\E[21;%p1%d~" else getCapability(Capability.key_f10)
                case 0x7A => // VK_F11
                  if (keyState > 0) "\\E[23;%p1%d~" else getCapability(Capability.key_f11)
                case 0x7B => // VK_F12
                  if (keyState > 0) "\\E[24;%p1%d~" else getCapability(Capability.key_f12)
                case _ => null
              }
              if (escapeSequence != null) {
                (0 until keyEvent.repeatCount.toInt)
                  .foreach(_ => sb.append(escapeSequence.replaceAllLiterally("\\E", "\u001B")))
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
  val raw: InputStream = new SimpleInputStream {
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
  def setRawMode(toggle: Boolean): Unit = isRaw.set(toggle)
  override def read(): Int = if (isRaw.get) raw.read() else in.read()
}
