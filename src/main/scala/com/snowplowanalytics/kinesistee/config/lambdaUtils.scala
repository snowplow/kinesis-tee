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

import com.amazonaws.regions.Regions
import com.amazonaws.services.lambda.AWSLambdaClient
import com.amazonaws.services.lambda.model.GetFunctionConfigurationRequest

import scalaz.Scalaz._
import scalaz._

/**
  * A set of utilities for interacting with AWS Lambda
  */
object LambdaUtils extends AwsLambdaUtils {

  val isUri = "^dynamodb:([^/]*)/([^/]*)/?$".r

  /**
    * pull a region from an ARN
    *
    * @param arn the arn to extract the region from
    * @return the region as it appears in the arn, for example eu-west-1
    */
  def getRegionFromArn(arn: String): ValidationNel[String, String] = {
    if (arn.trim.isEmpty) {
      "Cannot extract region from an empty ARN".failureNel
    } else {
      scala.util.Try(arn.split(":")(3)) match {
        case scala.util.Success(v) => v.success
        case scala.util.Failure(_) => "Cannot extract region from ARN '%s': invalid format".format(arn).failureNel
      }
    }
  }


  /**
    * Uses the AWS Lambda SDK to collect the description of a given lambda
    *
    * @param lambdaFunction the name of the AWS lambda to query
    * @param region the region the lambda to query is in
    * @return the description of the AWS lambda given
    */
  def getLambdaDescription(lambdaFunction: String, region: String): ValidationNel[Exception, String] = {
    try {
      val request = new GetFunctionConfigurationRequest().withFunctionName(lambdaFunction)
      val client: AWSLambdaClient = new AWSLambdaClient().withRegion(Regions.fromName(region))
      val response = client.getFunctionConfiguration(request)

      response.getDescription.success
    } catch {
      case e: java.lang.Exception => e.failureNel
    }
  }

  /**
    * Converts a specially crafted description (in the format dynamodb:region/table-name) into its constituent parts
    *
    * @param description the given lambda description
    * @return the AWS region and table name
    */
  def configLocationFromLambdaDesc(description:String): ValidationNel[String, (Regions, String)] = {
    description match {
      case isUri(region, table) => {
        scala.util.Try(Regions.fromName(region)) match {
          case scala.util.Success(r) => (r, table).success
          case scala.util.Failure(f) => s"'$region' is not a valid AWS region: ${f.getMessage}".failureNel
        }
      }
      case _ => s"'$description' is not a valid configuration location - expected the format 'dynamodb:eu-west-1/config-table-name'".failureNel
    }
  }

}

trait AwsLambdaUtils {
  def getRegionFromArn(arn: String): ValidationNel[String, String]
  def getLambdaDescription(lambdaFunction: String, region: String): ValidationNel[Exception, String]
  def configLocationFromLambdaDesc(description:String): ValidationNel[String, (Regions, String)]
}