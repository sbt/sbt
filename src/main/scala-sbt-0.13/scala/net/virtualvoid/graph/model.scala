/*
 * Copyright 2015 Johannes Rudolph
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package net.virtualvoid.sbt.graph

import sbinary.{ Format, DefaultProtocol }

object ModuleGraphProtocol extends DefaultProtocol {
  implicit def seqFormat[T: Format]: Format[Seq[T]] = wrap[Seq[T], List[T]](_.toList, _.toSeq)
  implicit val ModuleIdFormat: Format[ModuleId] = asProduct3(ModuleId)(ModuleId.unapply(_).get)
  implicit val ModuleFormat: Format[Module] = asProduct6(Module)(Module.unapply(_).get)
  implicit val ModuleGraphFormat: Format[ModuleGraph] = asProduct2(ModuleGraph.apply _)(ModuleGraph.unapply(_).get)
}
