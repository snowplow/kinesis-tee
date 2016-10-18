package com.snowplowanalytics.kinesistee

import java.lang.String

import awscala.{Region => AWSScalaRegion}
import awscala.dynamodbv2.DynamoDB
import com.amazonaws.regions.{Region, Regions}
import com.amazonaws.services.kinesis.AmazonKinesisClient
import com.amazonaws.services.lambda.runtime.{Context => LambdaContext}
import com.amazonaws.services.lambda.runtime.events.KinesisEvent
import com.amazonaws.services.lambda.runtime.events.KinesisEvent.KinesisEventRecord
import com.snowplowanalytics.kinesistee.config.{_}
import com.snowplowanalytics.kinesistee.filters.JavascriptFilter
import com.snowplowanalytics.kinesistee.transformation.JavascriptTransformer

import scala.collection.JavaConversions._
import scalaz._
import com.snowplowanalytics.kinesistee.models.{Content, Stream}
import com.snowplowanalytics.kinesistee.routing.PointToPointRoute
import com.snowplowanalytics.kinesistee.transformation.SnowplowToJson

class Main {

  val kinesisTee:Tee = KinesisTee
  val lambdaUtils:AwsLambdaUtils = LambdaUtils
  val configurationBuilder:Builder = ConfigurationBuilder
  val getKinesisConnector: (Region, Option[TargetAccount]) => AmazonKinesisClient = StreamWriter.buildClient
  val ddb: (AWSScalaRegion) => DynamoDB = DynamoDB.at

  /**
    * AWS Lambda entry point
    *
    * @param event an amazon kinesis event
    * @param context the context our lambda is in
    */
  def kinesisEventHandler(event: KinesisEvent, context: LambdaContext): Unit = {

     val conf = getConfiguration(context)
     val data = for { rec: KinesisEventRecord <- event.getRecords
                      row = new String(rec.getKinesis.getData.array(), "UTF-8")
                      partitionKey = rec.getKinesis.getPartitionKey
                      content = Content(row, partitionKey)
                } yield content

    val myRegion = lambdaUtils.getRegionFromArn(context.getInvokedFunctionArn) match {
      case Success(r) => Region.getRegion(Regions.fromName(r))
      case Failure(f) => throw new IllegalStateException(f.toString())
    }

    val transformation = conf.transformer match {
      case Some(Transformer("BuiltIn", "SNOWPLOW_TO_NESTED_JSON")) => Some(new SnowplowToJson)
      case Some(Transformer("Javascript", f)) => Some(new JavascriptTransformer(new String(java.util.Base64.getDecoder.decode(f), "UTF-8")))
      case _ => None
    }

    val filter = conf.filter match {
      case Some(Filter("Javascript", f)) => Some(new JavascriptFilter(new String(java.util.Base64.getDecoder.decode(f), "UTF-8")))
      case _ => None
    }

    val targetAccount = conf.targetStream.targetAccount
    val targetStream = targetAccount match {
      case Some(t) => Stream(conf.targetStream.name, Region.getRegion(Regions.fromName(t.region)))
      case None => Stream(conf.targetStream.name, myRegion)
    }
    val streamWriter = new StreamWriter(targetStream, targetAccount, getKinesisConnector(targetStream.region, targetAccount))
    val route = new PointToPointRoute(streamWriter)

    kinesisTee.tee(route,
                   transformation,
                   filter,
                   data)

    streamWriter.flush
    streamWriter.close
  }

  def getConfiguration(context: LambdaContext): Configuration = {
    val region = lambdaUtils.getRegionFromArn(context.getInvokedFunctionArn) match {
      case Success(r) => r
      case Failure(f) => throw new IllegalStateException(f.toString())
    }

    val (confRegion, confTable) = lambdaUtils.getLambdaDescription(context.getFunctionName, region) match {
      case Success(desc) => {
        lambdaUtils.configLocationFromLambdaDesc(desc) match {
          case Success((region,table)) => (region, table)
          case Failure(f) => throw new IllegalStateException(f.toString())
        }
      }
      case Failure(f) => throw new IllegalStateException(f.toString(), f.head)
    }

    scala.util.Try(configurationBuilder.build(confTable, context.getFunctionName)(ddb(Region.getRegion(confRegion)))) match {
      case scala.util.Success(c) => c
      case scala.util.Failure(f) => throw new IllegalStateException("Couldn't build configuration", f)
    }
  }

}