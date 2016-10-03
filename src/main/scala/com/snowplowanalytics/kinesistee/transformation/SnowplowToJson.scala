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
import com.snowplowanalytics.kinesistee.models.Content
import com.snowplowanalytics.snowplow.analytics.scalasdk.json.EventTransformer

import scalaz.{Failure, Success, ValidationNel}
import scalaz.syntax.validation._

/**
  * Transformation strategy - uses the Snowplow Scala analytics SDK to convert enriched events to nested JSON
  */
class SnowplowToJson extends TransformationStrategy {

  /**
    * Use the Snowplow Scala analytics SDK to turn an enriched event into nested JSON
    * @param content the record to transform
    * @return a nested json representation of the enriched event, or failure if it could not be converted
    */
  override def transform(content: Content): ValidationNel[Throwable, Content] = {
    EventTransformer.transform(content.row) match {
      case Success(s) => Content(s, content.partitionKey).success
      case Failure(f) => new IllegalArgumentException(f.head.toString).failureNel
    }
  }

}
