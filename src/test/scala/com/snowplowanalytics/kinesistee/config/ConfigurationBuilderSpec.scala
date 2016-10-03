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

package com.snowplowanalytics.kinesistee.config

import java.util
import awscala.dynamodbv2.{AttributeValue, DynamoDB}
import com.amazonaws.services.dynamodbv2.model.{QueryRequest, QueryResult}
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

class ConfigurationBuilderSpec extends Specification with Mockito {


  val sampleGoodConfig = scala.io.Source.fromURL(getClass.getResource("/sample_self_describing_config.json")).mkString
  val sampleConfig = Configuration(name = "My Kinesis Tee example",
                                   targetStream = TargetStream("my-target-stream", None),
                                   transformer = Some(Transformer(BuiltIn.SNOWPLOW_TO_NESTED_JSON)),
                                   filter = None)

  "A valid configuration" should {

    "generate the correct case class" in {
      ConfigurationBuilder.build(sampleGoodConfig) mustEqual sampleConfig
    }

  }

  "An invalid JSON configuration" should {

    "throw an exception" in {
      ConfigurationBuilder.build("banana") must throwA[IllegalArgumentException]
    }

  }

  "A configuration that doesn't match the given schema" should {

    "throw an exception" in {
      ConfigurationBuilder.build(
        """
          |{
          |  "schema": "com.thing",
          |  "data": { "foo":"bar" }
          |}
        """.stripMargin) must throwA(new IllegalArgumentException("Invalid configuration"))
    }

  }

  "Loading from DynamoDB" should {

    val sampleConfigTableName = "config-table-sample-name"

    "load a configuration using dynamodb and the specified table name" in {
      implicit val dynamoDB = mock[DynamoDB]
      val res = mock[QueryResult]
      val items:util.List[java.util.Map[java.lang.String,com.amazonaws.services.dynamodbv2.model.AttributeValue]] = new util.ArrayList()

      val one:util.Map[String,com.amazonaws.services.dynamodbv2.model.AttributeValue] = new util.HashMap()
      one.put("id", new AttributeValue(Some("with-id")))
      one.put("configuration", new AttributeValue(Some(sampleGoodConfig)))
      items.add(one)

      res.getItems returns items
      dynamoDB.query(any[QueryRequest]) returns res

      ConfigurationBuilder.build(sampleConfigTableName, "with-id") mustEqual sampleConfig
    }

    "give a good error if the table doesn't have a matching entry" in {
      implicit val dynamoDB = mock[DynamoDB]
      val res = mock[QueryResult]
      val items:util.List[java.util.Map[java.lang.String,com.amazonaws.services.dynamodbv2.model.AttributeValue]] = new util.ArrayList()

      res.getItems returns items
      dynamoDB.query(any[QueryRequest]) returns res

      ConfigurationBuilder.build(sampleConfigTableName, "with-id") must throwA(new IllegalStateException(s"No configuration in table '$sampleConfigTableName' for lambda 'with-id'!"))
    }

    "give a good error if the table doesn't have the right keys (id and configuration)" in {
      implicit val dynamoDB = mock[DynamoDB]
      val res = mock[QueryResult]
      val items:util.List[java.util.Map[java.lang.String,com.amazonaws.services.dynamodbv2.model.AttributeValue]] = new util.ArrayList()

      val one:util.Map[String,com.amazonaws.services.dynamodbv2.model.AttributeValue] = new util.HashMap()
      one.put("id", new AttributeValue(Some("with-id")))
      one.put("this-is-not-config", new AttributeValue(Some("abc")))

      items.add(one)
      res.getItems returns items
      dynamoDB.query(any[QueryRequest]) returns res

      ConfigurationBuilder.build(sampleConfigTableName, "with-id") must throwA(new IllegalStateException(s"Config table '${sampleConfigTableName}' for lambda 'with-id' is missing a 'configuration' field!"))
    }

    "do something reasonable if ddb errors" in {
      implicit val dynamoDB = mock[DynamoDB]
      val exception = new IllegalArgumentException("Query exploded")
      dynamoDB.query(any[QueryRequest]) throws exception

      // NB IllegalArgumentException is rethrown as IllegalStateException
      ConfigurationBuilder.build(sampleConfigTableName, "with-id") must throwA[IllegalStateException](message = "Query exploded")
    }


  }

}
