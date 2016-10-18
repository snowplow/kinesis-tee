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
package com.snowplowanalytics.kinesistee.transformation

import java.io.StringReader
import javax.script.{Invocable, ScriptEngineManager, ScriptException}

import com.snowplowanalytics.kinesistee.models.Content

import scalaz.syntax.validation._
import scalaz.ValidationNel


class JavascriptTransformer(js: String) extends TransformationStrategy {

  val engine = new ScriptEngineManager(null).getEngineByName("nashorn")
  if (engine==null) { throw new IllegalStateException("Nashorn script engine not available")}
  val in: Invocable = engine.asInstanceOf[Invocable]

  engine.eval(new StringReader(js))

  /**
    * Transform filter - invokes nashorn on the given js script
    * The script must contain a 'transform' function that accepts the record as an argument
    * @param content the record to transform
    * @return content transformed content row
    */
  override def transform(content: Content): ValidationNel[Throwable, Content] = {
    try {
      val retVal = in.invokeFunction("transform", content.row)
      retVal match {
        case row: String => new Content(row, content.partitionKey).success
        case e => new RuntimeException(s"'$e' returned by your js function cannot be converted to boolean").failureNel
      }
    } catch {
      case e @ (_: ScriptException | _: NoSuchMethodException ) => e.failureNel
    }
  }

}
