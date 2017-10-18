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

package com.snowplowanalytics.kinesistee

import com.snowplowanalytics.kinesistee.filters.FilterStrategy
import com.snowplowanalytics.kinesistee.models.Content
import com.snowplowanalytics.kinesistee.transformation.TransformationStrategy
import com.snowplowanalytics.kinesistee.models.Stream
import com.snowplowanalytics.kinesistee.routing.RoutingStrategy

import scalaz.{Failure, Success}

/**
  * This object is responsible for gluing together the routing, transformation and filtering steps
  */
object KinesisTee extends Tee {

  /**
    * Send incoming content to the given stream
    * using the given routingStrategy. Transform the data using the given transformation strategy
    * first - and then use the given filter strategy to filter the transformed data
    * @param routingStrategy routing strategy to use
    * @param transformationStrategy transformation strategy to use
    * @param filterStrategy filtering strategy to use
    * @param content list of records/content to tee on
    */
  def tee(routingStrategy: RoutingStrategy,
          transformationStrategy: Option[TransformationStrategy],
          filterStrategy: Option[FilterStrategy],
          content: Seq[Content]): Unit = {

    // transform first
    // then filter
    // then push to stream via StreamWriter

    def transform(content:Content) = {
      transformationStrategy match  {
        case Some(strategy) => {
          strategy.transform(content) match {
            case Success(s) => s
            case Failure(f) => {
              // see https://github.com/snowplow/kinesis-tee/issues/11
              System.err.println(s"Error transforming item: '$content'\n\n:Reason: ${f.head.getMessage}")
              content
            }
          }
        }
        case None => content
      }
    }

    def filter(content:Content) = {
      filterStrategy match {
        case Some(strategy) => {
          strategy.filter(content) match {
            case Success(s) => s
            case Failure(f) => {
              // see https://github.com/snowplow/kinesis-tee/issues/11
              System.err.println(s"Error filtering item '$content'\n\n:Reason: ${f.head.getMessage}")
              false
            }
          }
        }
        case None => true
      }
    }

    def route = {
      routingStrategy.route() match {
        case Success(s) => s
        case Failure(f) => throw new IllegalStateException(s"Error routing item '$content': ${f.head}")
      }
    }

    content
      .map(transform)
      .filter(filter)
      .grouped(100)
      .foreach(route.write)
  }


}
