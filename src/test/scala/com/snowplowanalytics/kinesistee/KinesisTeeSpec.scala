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

import com.amazonaws.regions.{Region, Regions}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import com.snowplowanalytics.kinesistee.filters.FilterStrategy
import com.snowplowanalytics.kinesistee.models.Content
import com.snowplowanalytics.kinesistee.models.Stream
import com.snowplowanalytics.kinesistee.routing.RoutingStrategy
import com.snowplowanalytics.kinesistee.transformation.TransformationStrategy
import org.mockito.Matchers.{eq => eqTo}

import scala.language.reflectiveCalls
import scalaz.syntax.validation._
import scalaz.ValidationNel

class KinesisTeeSpec extends Specification with Mockito {

  def sampleStream(streamName:String) = Stream(streamName, Region.getRegion(Regions.US_EAST_1))

  "the tee function" should {

    def mockRoute = new RoutingStrategy {
      val mockStreamWriter = mock[StreamWriter]
      override def route(): ValidationNel[String, StreamWriter] = mockStreamWriter.success
    }

    "write everything to the StreamWriter if no filter strategy is in use" in {
      val sampleContent = Seq(Content("a", "p"), Content("a", "p"), Content("a", "p"))
      val route = mockRoute
      KinesisTee.tee(route, None, None, sampleContent)
      there was one (route.mockStreamWriter).write(eqTo(sampleContent))
    }

    "write to the stream writer only if the filter function returns false" in {
      val sampleContent = Seq(Content("a", "p"), Content("a", "p"), Content("a", "p"))

      class FilterEverything extends FilterStrategy {
        override def filter(content: Content): ValidationNel[Throwable, Boolean] = {
          false.success
        }
      }

      val routeMock = mockRoute
      KinesisTee.tee(routingStrategy = routeMock,
                     transformationStrategy = None,
                     filterStrategy = Some(new FilterEverything),
                     content = sampleContent)
      there was no (routeMock.mockStreamWriter).write(any[Seq[Content]])
    }

    "transform stream content using the given transformation strategy" in {
      val sampleContent = Seq(Content("a", "p"), Content("a", "p"), Content("a", "p"))

      class MakeEverythingB extends TransformationStrategy {
        override def transform(content: Content): ValidationNel[Throwable, Content] = {
          Content("b", "p").success
        }
      }

      val routeMock = mockRoute
      KinesisTee.tee(routeMock, Some(new MakeEverythingB), None, sampleContent)

      val expectedContents = Seq(Content("b", "p"), Content("b", "p"), Content("b", "p"))
      there was one (routeMock.mockStreamWriter).write(eqTo(expectedContents))
    }

    "run the transformation strategy prior to the filter strategy" in {
      val sampleContent = Seq(Content("a", "p"), Content("a", "p"), Content("a", "p"))

      class MakeEverythingB extends TransformationStrategy {
        override def transform(content: Content): ValidationNel[Throwable, Content] = {
          Content("b", "p").success
        }
      }

      class FilterNotB extends FilterStrategy {
        override def filter(content: Content): ValidationNel[Throwable, Boolean] = {
          content match {
            case Content("b", "p") => true.success
            case _ => false.success
          }
        }
      }

      val routeMock = mockRoute
      KinesisTee.tee(routeMock, Some(new MakeEverythingB), Some(new FilterNotB), sampleContent)

      val expectedContents = Seq(Content("b", "p"), Content("b", "p"), Content("b", "p"))
      there was one (routeMock.mockStreamWriter).write(eqTo(expectedContents))
    }

    "swallow failures in the filter strategy before pushing anything to the stream writer" in {
      class FailureFilter extends FilterStrategy {
        override def filter(content: Content): ValidationNel[Throwable, Boolean] = new IllegalArgumentException("something").failureNel
      }

      val routeMock = mockRoute
      there was no (routeMock.mockStreamWriter).write(any[Seq[Content]])
    }

    "throw failures in the transformation strategy before pushing anything to the stream writer" in {
      class FailureTransform extends TransformationStrategy {
        override def transform(content: Content): ValidationNel[Throwable, Content] = new IllegalStateException("something").failureNel
      }

      val routeMock = mockRoute
      KinesisTee.tee(routeMock, Some(new FailureTransform), None, Seq(Content("b", "p")))
      there was one (routeMock.mockStreamWriter).write(any[Seq[Content]])
    }

  }
}
