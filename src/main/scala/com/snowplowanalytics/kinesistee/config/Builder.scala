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

import awscala.dynamodbv2.DynamoDB

/**
  * This trait allows us to override configuration building
  */
trait Builder {
  /**
    * Build a Configuration object, collecting the configuration from dynamodb
    * @param tableName the name of the dynamodb table the configuration is stored in
    * @param functionName the name of the AWS lambda function (the 'id' key field in the dynamodb table)
    * @param dynamoDB the dynamodb instance to use for fetching data
    * @return a configuration object
    */
  def build(tableName: String, functionName: String)(implicit dynamoDB: DynamoDB): Configuration
}
