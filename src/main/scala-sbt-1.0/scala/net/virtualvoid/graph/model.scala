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

import sjsonnew._, LList.:*:

object ModuleGraphProtocol extends BasicJsonProtocol {

  implicit val ModuleIdFormat: IsoLList[ModuleId] =
    LList.isoCurried(
      (m: ModuleId) => 
        ("organisation", m.organisation) :*: 
        ("name", m.name) :*:
        ("version", m.version) :*: 
        LNil
    ) { case 
          (_, organisation) :*:
          (_, name) :*:
          (version, _) :*:
          LNil => ModuleId(organisation, name, version)
    }

  implicit val ModuleFormat: IsoLList[Module] =
    LList.isoCurried(
      (m: Module) => 
        ("id", m.id) :*: 
        ("license", m.license) :*:
        ("extraInfo", m.extraInfo) :*: 
        ("evictedByVersion", m.evictedByVersion) :*: 
        ("jarFile", m.jarFile) :*: 
        ("error", m.error) :*:
        LNil
    ) {
      case 
        (_, id) :*: 
        (_, license) :*:
        (_, extraInfo) :*: 
        (_, evictedByVersion) :*: 
        (_, jarFile) :*: 
        (_, error) :*:
        LNil => Module(id, license, extraInfo, evictedByVersion, jarFile, error)
    }


  implicit val ModuleGraphFormat: IsoLList[ModuleGraph] =
    LList.isoCurried(
      (g: ModuleGraph) => 
        ("nodes", g.nodes) :*: 
        ("edges", g.edges) :*:
        LNil
    ) { case 
          (_, nodes) :*:
          (_, edges) :*:
          LNil => ModuleGraph(nodes, edges)
    }
}