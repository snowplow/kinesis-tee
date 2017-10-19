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

package com.snowplowanalytics.kinesistee.routing

import com.snowplowanalytics.kinesistee.StreamWriter

import scalaz._
import scalaz.syntax.validation._
import com.snowplowanalytics.kinesistee.models.Stream

/**
  * This routing strategy passes all traffic through to the destination
  * @param destination the endpoint to route all data to
  */
class PointToPointRoute(destination: StreamWriter, val batchSize: Int) extends RoutingStrategy {

  /**
    * Routing strategy that sends all traffic to the given destination
    * @return all traffic sent to the given destination
    */
  override def route: ValidationNel[String, StreamWriter] = {
      destination.success
  }

  override def toString:String = {
    s"Stream to stream route: stream `source` -> stream ${destination.toString}"
  }

}
