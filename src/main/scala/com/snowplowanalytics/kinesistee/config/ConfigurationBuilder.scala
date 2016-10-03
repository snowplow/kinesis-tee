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

import java.io.ByteArrayInputStream

import com.sksamuel.avro4s.AvroInputStream
import awscala.dynamodbv2.DynamoDB
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.dynamodbv2.model.{AttributeValue, QueryRequest}
import scala.collection.JavaConverters._
import scalaz._
import scalaz.syntax.validation._

/**
  * Object that lets us collect configuration from dynamodb, and inflate it into a Configuration object
  */
object ConfigurationBuilder extends Builder {

  /**
    * Fetch a configuration from specified dynamodb instance, using the lambda function name as the key
    *
    * @param tableName the name of the table to look for config in
    * @param functionName the name of the lambda function (a configuration must exist for this!)
    * @param dynamoDB a dynamodb instance to connect using
    * @return the configuration stored for the lambda function name
    */
  def build(tableName: String, functionName: String)(implicit dynamoDB: DynamoDB): Configuration = {
    fetchConfigString(tableName, functionName)(dynamoDB) match {
      case Success(config) => build(config)
      case Failure(f) => throw new IllegalStateException(f.head)
    }
  }

  private def fetchConfigString(tableName: String, functionName: String)(dynamoDB: DynamoDB): ValidationNel[String, String] = {
    val request = new QueryRequest()
      .withTableName(tableName)
      .withKeyConditionExpression("id = :id")
      .withExpressionAttributeValues(Map(":id" -> new AttributeValue(functionName)).asJava)

    val response = scala.util.Try(dynamoDB.query(request)) match {
      case scala.util.Success(resp) => resp
      case scala.util.Failure(f) => return f.getMessage.failureNel
    }

    val row = response.getItems.asScala.headOption
    row match {
      case Some(data) => {
        if (data.containsKey("configuration")) {
          data.get("configuration").getS.success
        } else {
          s"Config table '${tableName}' for lambda '$functionName' is missing a 'configuration' field!".failureNel
        }
      }
      case None => s"No configuration in table '$tableName' for lambda '$functionName'!".failureNel
    }
  }

  /**
    * Build the configuration from a string, not using dynamodb
    * @param json the avro/json configuration file
    * @return a configuration object inflated from the avro/json input
    */
  def build(json: String): Configuration = {
    val data = new SelfDescribingData(json).data
    val input = AvroInputStream.json[Configuration](new ByteArrayInputStream(data.getBytes("UTF-8")))

    input.singleEntity match {
      case scala.util.Success(configuration) => configuration
      case scala.util.Failure(f) => throw new IllegalArgumentException("Invalid configuration", f)
    }
  }

}
