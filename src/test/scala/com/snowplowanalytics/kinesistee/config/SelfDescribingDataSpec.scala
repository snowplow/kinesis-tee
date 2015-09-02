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

import org.specs2.mutable.Specification
import org.json4s._
import org.json4s.jackson.JsonMethods._

import scala.util.{Failure, Success, Try}

class SelfDescribingDataSpec extends Specification {

  "with valid self describing JSON" should {

    "give the right schema" in {
      val sdd = new SelfDescribingData(
        """
          |{
          |  "schema":"iglu:com.acme.banana",
          |  "data": { }
          |}
        """.stripMargin)

      sdd.schema mustEqual "iglu:com.acme.banana"
    }

    "give correctly formed json data payload" in {
      val sdd = new SelfDescribingData(
        """
          |{
          |  "schema":"iglu:com.acme.banana",
          |  "data": {
          |   "foo":"bar"
          |  }
          |}
        """.stripMargin)

      val expected = pretty(parse(
        """
          | { "foo":"bar" }
        """.stripMargin
      ))

      sdd.data mustEqual expected
    }


  }

  "with invalid self describing JSON" should {

    "error if the `schema` field is missing" in {
      Try(new SelfDescribingData(
        """
          | {
          |   "data" : { "nope": "nope" }
          | }
        """.stripMargin)) match {
        case Success(_) => ko("self describing data created without a `schema` field")
        case Failure(f) => f.getMessage mustEqual "Invalid self describing schema: missing the `schema` field"
      }
    }

    "error if the `data` field is missing" in {
      Try(new SelfDescribingData(
        """
          | {
          |    "schema": "iglu:com.acme.thing"
          | }
        """.stripMargin)) match {
        case Success(_) => ko("self describing data created without a `data` field")
        case Failure(f) => f.getMessage mustEqual "Invalid self describing schema: missing the `data` field (or it is empty)"
      }
    }
  }

  "with invalid json" should {

    "return a validation exception" in {
      Try(new SelfDescribingData("{")) match {
        case Success(_) => ko("Invalid JSON parsed successfully")
        case Failure(f) => f.getMessage mustEqual "Invalid self describing schema: invalid JSON Avro"
      }
    }

  }

}
