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

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

import awscala.dynamodbv2.DynamoDB
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.lambda.runtime.events.KinesisEvent
import com.amazonaws.services.lambda.runtime.events.KinesisEvent.KinesisEventRecord
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import com.snowplowanalytics.kinesistee.config.{TargetStream, _}
import com.amazonaws.services.lambda.runtime.{Context => LambdaContext}
import com.snowplowanalytics.kinesistee.models.{NonEmptyContent, Stream}
import com.snowplowanalytics.kinesistee.routing.{PointToPointRoute, RoutingStrategy}
import org.mockito.Matchers.{eq => eqTo}

import scalaz.syntax.validation._
import scala.collection.JavaConversions._
import scala.language.reflectiveCalls
import com.amazonaws.services.kinesis.AmazonKinesisClient

class MainSpec extends Specification with Mockito {

  val sampleConfig = Configuration(name = "My Kinesis Tee example",
    targetStream = TargetStream("my-target-stream", None),
    operator = Some(List()))

  class MockMain extends Main {
    override val kinesisTee:Tee = mock[Tee]

    override val lambdaUtils:AwsLambdaUtils = {
      val util = mock[AwsLambdaUtils]
      util.getLambdaDescription(any[String], any[String]) returns "dynamodb:us-east-1/config".success
      util.getRegionFromArn(any[String]) returns "us-east-1".success
      util.configLocationFromLambdaDesc(any[String]) returns (Regions.US_EAST_1, "table-name").success
      util
    }

    override val configurationBuilder:Builder = {
      val builder = mock[Builder]
      builder.build(any[String], any[String])(any[DynamoDB]) returns sampleConfig
      builder
    }

    override val getKinesisConnector = (_:Region, _:Option[TargetAccount]) => mock[AmazonKinesisClient]

    override val ddb = (_:Region) => mock[DynamoDB]
  }

  val sampleArn = "arn:aws:elasticbeanstalk:us-east-1:123456789012:environment/My App/MyEnvironment"
  val sampleFunctionName = "fun"

  def sampleContext = {
    val context = mock[LambdaContext]
    context.getInvokedFunctionArn returns sampleArn
    context.getFunctionName returns sampleFunctionName
    context
  }

  def sampleKinesisEvent = {
    val event = mock[KinesisEvent]
    val record = mock[KinesisEventRecord]
    val kinesisRecord = mock[KinesisEvent.Record]

    kinesisRecord.getPartitionKey returns "p"
    kinesisRecord.getData returns ByteBuffer.wrap("hello world".getBytes("UTF-8"))
    record.getKinesis returns kinesisRecord

    event.getRecords returns List(record)
    event
  }

  "getting configuration" should {

    "use the lambda utils to grab the ARN" in {
      val main = new MockMain
      main.getConfiguration(sampleContext)
      there was one (main.lambdaUtils).getRegionFromArn(eqTo(sampleArn))
    }

    "throw an exception if the ARN cannot be ascertained" in {
      val main = new MockMain {
        override val lambdaUtils:AwsLambdaUtils = {
          val util = mock[AwsLambdaUtils]
          util.getRegionFromArn(any[String]) returns "Cannot handle it".failureNel
          util
        }
      }
      main.getConfiguration(sampleContext) must throwA[IllegalStateException](message = "Cannot handle it")
    }

    "use the given arn/function name to fetch lambda description" in {
      val mockMain = new MockMain
      mockMain.getConfiguration(sampleContext)
      there was one (mockMain.lambdaUtils).getLambdaDescription(eqTo(sampleFunctionName), eqTo("us-east-1"))
    }

    "throw an exception if the lambda description cannot be ascertained" in {
      val mockMain = new MockMain {
        override val lambdaUtils:AwsLambdaUtils = {
          val util = mock[AwsLambdaUtils]
          util.getLambdaDescription(any[String], any[String]) returns new RuntimeException("failed?").failureNel
          util.getRegionFromArn(any[String]) returns "us-east-1".success
        }
      }

      mockMain.getConfiguration(sampleContext) must throwA[IllegalStateException](message = "failed?")
    }

    "throw an exception if the lambda config location cannot be ascertained" in {
      val mockMain = new MockMain {
        override val lambdaUtils:AwsLambdaUtils = {
          val util = mock[AwsLambdaUtils]
          util.getLambdaDescription(any[String], any[String]) returns "dynamodb:sample/sample".success
          util.getRegionFromArn(any[String]) returns "us-east-1".success
          util.configLocationFromLambdaDesc(eqTo("dynamodb:sample/sample")) returns "oops".failureNel
        }
      }

      mockMain.getConfiguration(sampleContext) must throwA[IllegalStateException](message = "oops")
    }

    "use the lambda description to build config from" in {
      val main = new MockMain
      main.getConfiguration(sampleContext)
      there was one (main.configurationBuilder).build(eqTo("table-name"), eqTo(sampleFunctionName))(any[DynamoDB])
    }

    "throw an exception if the configuration fails to build" in {
      val main = new MockMain {
        override val configurationBuilder:Builder = {
          val cb = mock[Builder]
          cb.build(any[String], any[String])(any[DynamoDB]) throws new RuntimeException("broken")
        }
      }

      main.getConfiguration(sampleContext) must throwA[IllegalStateException](message="Couldn't build configuration")
    }

  }

  "the kinesis tee lambda entry point" should {

    "tee with the given records" in {
      val main = new MockMain
      main.kinesisEventHandler(sampleKinesisEvent, sampleContext)
      there was one(main.kinesisTee).tee(any[RoutingStrategy],
        any[List[Operator]],
        eqTo(Seq(NonEmptyContent("hello world", "p"))))

    }

    "tee using the routing strategy point-to-point" in {
      val main = new MockMain {
        override val kinesisTee = new Tee {

          var lastRoutingStrategy: Option[PointToPointRoute] = None

          override def tee(routingStrategy: RoutingStrategy,
                           operationStrategy: List[Operator],
                           content: Seq[NonEmptyContent]): Unit = {
            lastRoutingStrategy = Some(routingStrategy.asInstanceOf[PointToPointRoute])
          }
        }
      }

      main.kinesisEventHandler(sampleKinesisEvent, sampleContext)
      val expectedRouter = new PointToPointRoute(new StreamWriter(Stream(sampleConfig.targetStream.name, Region.getRegion(Regions.US_EAST_1)),
        sampleConfig.targetStream.targetAccount,
        mock[AmazonKinesisClient]))

      val lastRoutingStrategy: PointToPointRoute = main.kinesisTee.lastRoutingStrategy.get
      lastRoutingStrategy.toString mustEqual expectedRouter.toString
    }
  }

  "tee using the operators defined in the configuration" in {

    val sampleFilterJs =
      """
        | function operator(data) {
        |   if (data=="good") { return true; }
        |   else { return false; }
        | }
      """.stripMargin


    val sampleTransformJs =
      """
        | function operator(data) {
        |   return data.replace("$", "")
        | }
      """.stripMargin

    val base64FilterJs = java.util.Base64.getEncoder.encodeToString(sampleFilterJs.getBytes(StandardCharsets.UTF_8))
    val base64TransformJs = java.util.Base64.getEncoder.encodeToString(sampleTransformJs.getBytes(StandardCharsets.UTF_8))

    val main = new MockMain {
      override val configurationBuilder:Builder = {
        val builder = mock[Builder]
        builder.build(any[String], any[String])(any[DynamoDB]) returns sampleConfig.copy(operator = Some(List(
          Operator(OperatorType.JAVASCRIPT, base64TransformJs),
          Operator(OperatorType.JAVASCRIPT, base64FilterJs)
        )))
        builder
      }

      override val kinesisTee = new Tee {

        var operations: List[Operator] = List()

        override def tee(routingStrategy: RoutingStrategy,
                         operationStrategy: List[Operator],
                         content: Seq[NonEmptyContent]): Unit = {
          operations = operationStrategy
        }
      }
    }

    main.kinesisEventHandler(sampleKinesisEvent, sampleContext)
    main.kinesisTee.operations mustEqual List(JavascriptOperator(sampleTransformJs), JavascriptOperator(sampleFilterJs))
  }
}
