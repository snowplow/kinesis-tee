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
import com.snowplowanalytics.kinesistee.models.{Content, FilteredContent, NonEmptyContent, Stream}
import com.snowplowanalytics.kinesistee.routing.RoutingStrategy
import org.mockito.Matchers.{eq => eqTo}

import scala.language.reflectiveCalls
import scalaz.syntax.validation._
import scalaz.{Success, ValidationNel}

class KinesisTeeSpec extends Specification with Mockito {

  def sampleStream(streamName:String) = Stream(streamName, Region.getRegion(Regions.US_EAST_1))

  "the tee function" should {

    def mockRoute = new RoutingStrategy {
      val mockStreamWriter = mock[StreamWriter]
      override def route(): ValidationNel[String, StreamWriter] = mockStreamWriter.success
    }

    "write everything to the StreamWriter if no operator is in use" in {
      val sampleContent = Seq(NonEmptyContent("a", "p"), NonEmptyContent("a", "p"), NonEmptyContent("a", "p"))
      val route = mockRoute
      KinesisTee.tee(route, Nil, sampleContent)
      there was three (route.mockStreamWriter).write(eqTo(NonEmptyContent("a", "p").success))
    }

    "write to the stream writer only if the operator does not filter" in {
      val sampleContent = Seq(NonEmptyContent("a", "p"), NonEmptyContent("a", "p"), NonEmptyContent("a", "p"))

      case class FilterEverything() extends Operator {
        override def apply(content: ValidationNel[Throwable, Content]): ValidationNel[Throwable, Content] = {
          FilteredContent.success
        }
      }

      val routeMock = mockRoute
      KinesisTee.tee(routingStrategy = routeMock,
                     operationStrategy = List(FilterEverything()),
                     content = sampleContent)
      there was no (routeMock.mockStreamWriter).write(any[ValidationNel[Throwable, Content]])
    }

    "transform stream content using the given transformation operator" in {
      val sampleContent = Seq(NonEmptyContent("a", "p"), NonEmptyContent("a", "p"), NonEmptyContent("a", "p"))

      case class MakeEverythingB() extends Operator {
        override def apply(content: ValidationNel[Throwable, Content]): ValidationNel[Throwable, Content] = {
          NonEmptyContent("b", "p").success
        }
      }

      val routeMock = mockRoute
      KinesisTee.tee(routeMock, List(MakeEverythingB()), sampleContent)

      there was three (routeMock.mockStreamWriter).write(eqTo(NonEmptyContent("b", "p").success))
    }

    "run the operations in chain" in {
      val sampleContent = Seq(NonEmptyContent("a", "p"), NonEmptyContent("a", "p"), NonEmptyContent("c", "p"))

      case class MakeAB() extends Operator {
        override def apply(content: ValidationNel[Throwable, Content]): ValidationNel[Throwable, Content] = {
          content match {
            case Success(NonEmptyContent("a", "p")) => NonEmptyContent("b", "p").success
            case Success(s) => s.success
            case _ => throw new RuntimeException("Test unexpectedly returned a failure")
          }
        }
      }

      case class FilterNotB() extends Operator {
        override def apply(content: ValidationNel[Throwable, Content]): ValidationNel[Throwable, Content] = {
          content match {
            case Success(NonEmptyContent("b", "p")) => NonEmptyContent("b", "p").success
            case Success(s) => FilteredContent.success
            case _ => throw new RuntimeException("Test unexpectedly returned a failure")
          }
        }
      }

      val routeMock = mockRoute
      KinesisTee.tee(routeMock, List(MakeAB(),FilterNotB()), sampleContent)
      there was two (routeMock.mockStreamWriter).write(eqTo(NonEmptyContent("b", "p").success))
    }


    "throw failures in the operator strategy before pushing anything to the stream writer" in {
      case class FailureTransform() extends Operator {
        override def apply(content: ValidationNel[Throwable, Content]): ValidationNel[Throwable, Content] = new IllegalStateException("something").failureNel
      }

      val routeMock = mockRoute
      KinesisTee.tee(routeMock, List(new FailureTransform), Seq(NonEmptyContent("b", "p")))
      there was no (routeMock.mockStreamWriter).write(any[ValidationNel[Throwable, Content]])
    }

  }
}
