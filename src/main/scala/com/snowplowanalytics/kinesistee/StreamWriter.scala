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

import java.io.{PrintWriter, StringWriter}
import java.nio.ByteBuffer

import com.amazonaws.auth.{AWSCredentials, AWSCredentialsProvider, BasicAWSCredentials, DefaultAWSCredentialsProviderChain}
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.kinesis.AmazonKinesisClient
import com.amazonaws.services.kinesis.model.{PutRecordsRequestEntry, PutRecordsRequest}
import com.snowplowanalytics.kinesistee.config.TargetAccount
import com.snowplowanalytics.kinesistee.models.{Content, Stream}
import scala.collection.JavaConverters._

/**
  * Write a record to a predefined stream
  * @param stream the stream to write to
  * @param targetAccount the target account details, if not using Lambda IAM perms
  * @param producer the kinesis client to use
  */
class StreamWriter(stream: Stream, targetAccount: Option[TargetAccount], producer: AmazonKinesisClient) {

  /**
    * push the given record to the requested stream
    * @param contents the sequence of records to push
    */
  def write(contents: Seq[Content]): Unit = {
    val requestEntries = contents.map { content =>
      val data = ByteBuffer.wrap(content.row.getBytes("UTF-8"))

      new PutRecordsRequestEntry().withData(data).withPartitionKey(content.partitionKey)
    }

    val request = new PutRecordsRequest().withStreamName(stream.name).withRecords(requestEntries.asJava)
    producer.putRecords(request)
  }

  def flush: Unit = {
  }

  def close: Unit = {
    flush
  }

  override def toString: String = {
      s"`${stream.name}`, using separate account details: " + (if (targetAccount.isDefined) { "TRUE" } else { "FALSE" })
  }

}

object StreamWriter {

  /**
    * Write a stacktrace as a string
    * @param t a Throwable to generate the string stacktrace for
    * @return a string representation of the given Throwable
    */
  def stacktrace(t:Throwable): String = {
    val sw = new StringWriter()
    t.printStackTrace(new PrintWriter(sw))
    sw.toString
  }

  /**
    * Build the kinesis client we need to push data to
    * @param region the region the target stream is in
    * @param targetCreds the credentials needed to push to the target stream
    * @return a kinesis client
    */
  def buildClientConfig(region: Region, targetCreds: Option[(String, String)]) = {

    val credentialsProvider = if (targetCreds.isDefined) {
      val (acctKey, secretKey) = targetCreds.get
      new AWSCredentialsProvider() {
        override def refresh(): Unit = {}
        override def getCredentials: AWSCredentials = new BasicAWSCredentials(acctKey, secretKey)
      }
    } else {
      new DefaultAWSCredentialsProviderChain()
    }

    val client = new AmazonKinesisClient(credentialsProvider)

    client.setRegion(region)

    client
  }

  /**
    * Build the client using the supplied credentials
    * @param region the AWS region the stream is located
    * @param targetAccount alternate account details (if none use our own IAM perms)
    * @return an kinesis client connector
    */
  def buildClient(region:Region, targetAccount: Option[TargetAccount]) = {

    val creds = targetAccount match {
      case Some(t) => Some((t.awsAccessKey, t.awsSecretAccessKey))
      case _ => None
    }

    buildClientConfig(region, creds)
  }

}
