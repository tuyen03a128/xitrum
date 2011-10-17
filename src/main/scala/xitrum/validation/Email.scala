package xitrum.validation

import scala.xml.Elem
import xitrum.Action

object Email extends Validator {
  def render(action: Action, elem: Elem, paramName: String): Elem = {
    import action._
    jsAddToView(js$name(paramName) + ".rules('add', {email: true})")
    elem
  }

  def validate(action: Action, paramName: String): Boolean = {
    val value = action.param(paramName).trim
    """(?i)^[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,4}$""".r.findFirstIn(value).isDefined
  }
}
