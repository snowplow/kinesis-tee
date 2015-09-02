/*
 * Copyright (c) 2016 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package com.snowplowanalytics.kinesistee.filters

import java.io.StringReader

import com.snowplowanalytics.kinesistee.models.{Content, Stream}
import javax.script.Invocable
import javax.script.ScriptEngineManager
import javax.script.ScriptException

import scalaz.syntax.validation._
import scalaz.ValidationNel

class JavascriptFilter(js: String) extends FilterStrategy {

  // beware:
  // https://github.com/sbt/sbt/issues/1214
  val engine = new ScriptEngineManager(null).getEngineByName("nashorn")
  if (engine==null) { throw new IllegalStateException("Nashorn script engine not available") }
  val in: Invocable = engine.asInstanceOf[Invocable]

  engine.eval(new StringReader(js))

  /**
    * JavaScript filter - invokes nashorn on the given js script
    * The script must contain a 'filter' function that accepts the record as an argument
    * @param content the record to filter
    * @return true if the value is to pass through the stream, false if not
    */
  override def filter(content: Content): ValidationNel[Throwable, Boolean] = {
    try {
      val retVal = in.invokeFunction("filter", content.row)
      retVal match {
        case bool:java.lang.Boolean => bool.booleanValue().success
        case e => new RuntimeException(s"'$e' returned by your js function cannot be converted to boolean").failureNel
      }
    } catch {
      case e @ (_: ScriptException | _: NoSuchMethodException ) => e.failureNel
    }
  }

}
