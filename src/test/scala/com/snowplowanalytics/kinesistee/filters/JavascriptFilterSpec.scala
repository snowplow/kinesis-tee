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
package com.snowplowanalytics.kinesistee.filters

import com.amazonaws.regions.{Region, Regions}
import com.snowplowanalytics.kinesistee.models.{Content, Stream}
import org.specs2.mutable.Specification
import org.specs2.scalaz.ValidationMatchers

import scalaz.{Failure, Success}

class JavascriptFilterSpec extends Specification with ValidationMatchers {

  "A valid JS filter" should {

   val jsTrue =
     """
       | function filter(row) {
       |     return true;
       | }
     """.stripMargin

    val jsFalse =
      """
        | function filter(row) {
        |     return false;
        | }
      """.stripMargin

    val jsHelloWorldOnly =
      """
        | function filter(row) {
        |      return row != "hello world";
        | }
      """.stripMargin


    "with a js function that only returns true, return true" in {
      val strategy = new JavascriptFilter(jsTrue)
      strategy.filter(Content("hello world", "p")) must beSuccessful(true)
    }

    "with a js function that only returns false, return false" in {
      val strategy = new JavascriptFilter(jsFalse)
      strategy.filter(Content("hello world", "p")) must beSuccessful(false)
    }

    "with a function that filters out `hello world`, return true for non hello world" in {
      val strategy = new JavascriptFilter(jsHelloWorldOnly)
      strategy.filter(Content("banana", "p")) must beSuccessful(true)
    }

    "with a function that filters out `hello world`, return false if content is `hello world`" in {
      val strategy = new JavascriptFilter(jsHelloWorldOnly)
      strategy.filter(Content("hello world", "p")) must beSuccessful(false)
    }

  }

  "An invalid js filter" should {

    "fail if js is not well formed" in {
      val badlyFormedJs =
        """
          | function filter(row) {
        """.stripMargin // no trailing slash

      val expectedError =
        """<eval>:3:8 Expected } but found eof
          |
          |        ^ in <eval> at line number 3 at column number 8""".stripMargin

      scala.util.Try(new JavascriptFilter(badlyFormedJs)) match {
        case scala.util.Success(_) => ko("Badly formed JS did not generate exception")
        case scala.util.Failure(f) => f.getMessage.replaceAll("\\s", "") mustEqual expectedError.replaceAll("\\s", "")
      }
    }

    "fail if the js is missing a 'filter' function" in {
      val missingfunc =
        """
          |function banana() {
          |   return false;
          |}
        """.stripMargin

      val strategy = new JavascriptFilter(missingfunc)
      strategy.filter(Content("abc", "p")) match {
        case Success(_) => ko("Filter cannot succeed without a 'filter' function")
        case Failure(f) => f.toString() mustEqual "NonEmptyList(java.lang.NoSuchMethodException: No such function filter)"
      }
    }

    "fail if the js has a runtime error" in {
      val runtimeBloop =
        """
          |function filter(org) { return 1/0; }
        """.stripMargin

      val strategy = new JavascriptFilter(runtimeBloop)
      strategy.filter(Content("abc", "p")) must beFailing
    }


  }
}
