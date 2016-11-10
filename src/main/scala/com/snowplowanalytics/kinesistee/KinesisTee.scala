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


import com.snowplowanalytics.kinesistee.models.{Content, FilteredContent, NonEmptyContent}
import com.snowplowanalytics.kinesistee.routing.RoutingStrategy

import scalaz.{Failure, Success}
import scalaz.syntax.validation._
import scalaz.ValidationNel


/**
  * This object is responsible for gluing together the routing, transformation and filtering steps
  */
object KinesisTee extends Tee {

  /**
    * Send incoming content to the given stream
    * using the given routingStrategy. Transform the data using the given transformation strategy
    * first - and then use the given filter strategy to filter the transformed data
    * @param routingStrategy routing strategy to use
    * @param operationStrategy list sequence of operations to be applied
    * @param content list of records/content to tee on
    */
  def tee(routingStrategy: RoutingStrategy,
          operationStrategy: List[Operator],
          content: Seq[NonEmptyContent]) = {

  // Apply operations in sequence
  def operations(content: NonEmptyContent) = {
    var transformedContent: ValidationNel[Throwable, Content] = NonEmptyContent(content.row, content.partitionKey).success
    operationStrategy.foreach((operator: Operator) => {
      transformedContent = operator(transformedContent)
    })
    transformedContent
  }

  // Filter Out failures and FilteredContent
  def filterEmptyContent(content: ValidationNel[Throwable, Content]) = {
    content match {
      case Success(NonEmptyContent(row, partitionKey)) => true
      case Success(FilteredContent) => false
      case Failure(f) =>
        System.err.println(s"Error filtering item '$content'\n\n:Reason: ${f.head.getMessage}")
        false
    }
  }

  def route = {
    routingStrategy.route() match {
      case Success(s) => s
      case Failure(f) => throw new IllegalStateException(s"Error routing item '$content': ${f.head}")
    }
  }

  content
      .map(operations)
      .filter(filterEmptyContent)
      .foreach(route.write)
  }
}
