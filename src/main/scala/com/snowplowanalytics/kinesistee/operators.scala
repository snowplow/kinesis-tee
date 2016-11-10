package com.snowplowanalytics.kinesistee

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


import java.io.StringReader
import javax.script.{Invocable, ScriptEngineManager, ScriptException}

import com.snowplowanalytics.kinesistee.models.{Content, FilteredContent, NonEmptyContent}
import com.snowplowanalytics.snowplow.analytics.scalasdk.json.EventTransformer

import scalaz.syntax.validation._
import scalaz.{Failure, Success}
import scalaz.ValidationNel

/**
  * Operators work with ValidationNel[Throwable, Content]
  * and return ValidationNel[Throwable, Content] allowing
  * chaining.
  */
trait Operator extends Product with Serializable {
  def apply(content: ValidationNel[Throwable, Content]): ValidationNel[Throwable, Content]
}

/**
  * Javascript Operator that can be used as a filter or transformer
  * @param js
  */
case class JavascriptOperator(js: String) extends Operator {

  val engine = new ScriptEngineManager(null).getEngineByName("nashorn")
  if (engine==null) { throw new IllegalStateException("Nashorn script engine not available")}
  val in: Invocable = engine.asInstanceOf[Invocable]
  engine.eval(new StringReader(js))

  /**
    * JavaScript filter - invokes nashorn on the given js script
    * The script must contain a 'operate' function that accepts the record as an argument
    * @param content the record to operate on
    * @return Either NonEmptyContent or FilteredContent
    */

  override def apply(content: ValidationNel[Throwable, Content]): ValidationNel[Throwable, Content] = {
    content match {
      case Success(NonEmptyContent(row, partitionKey)) =>
        try {
          val retVal = in.invokeFunction("operator", row)
          retVal match {
            case row: String => NonEmptyContent(row, partitionKey).success
            case bool:java.lang.Boolean => if (bool) NonEmptyContent(row, partitionKey).success else FilteredContent.success
            case e => new RuntimeException(s"'$e' returned by your js function cannot be converted to a row").failureNel
          }
        } catch {
          case e @ (_: ScriptException | _: NoSuchMethodException ) => e.failureNel
        }
      case Success(FilteredContent) => FilteredContent.success
      case Failure(f) => throw new IllegalStateException(s"Preceding operation has failed '$content': ${f.head}")
    }
  }
}


case class SnowplowEnrichedToNestedJsonTransformOperator() extends Operator {
  /**
    * Use the Snowplow Scala analytics SDK to turn an enriched event into nested JSON
    * @param content the record to transform
    * @return a nested json representation of the enriched event, or failure if it could not be converted
    */
  def apply(content: ValidationNel[Throwable, Content]): ValidationNel[Throwable, Content] = {
    content match {
      case Success(NonEmptyContent(row, partitionKey)) =>
        EventTransformer.transform(row) match {
          case Success(s) => NonEmptyContent(s, partitionKey).success
          case Failure(f) => new IllegalArgumentException(f.head.toString).failureNel
        }
      case Success(FilteredContent) => FilteredContent.success
      case Failure(f) => throw new IllegalStateException(s"Preceding operation has failed '$content': ${f.head}")
    }
  }
}
