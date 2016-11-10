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


import com.snowplowanalytics.kinesistee.models.{FilteredContent, NonEmptyContent}
import org.specs2.mutable.Specification
import org.specs2.scalaz.ValidationMatchers

import scala.util.Try
import scalaz.syntax.validation._
import scalaz.{Failure, Success}

class JavascriptOperatorSpec extends Specification with ValidationMatchers {

  "A valid JS Operator" should {

    val jsTransform =
      """
        | function transform(row) {
        |     return row.replace("$", "");
        | }
      """.stripMargin

    val jsFilterTrue =
      """
        | function filter(row) {
        |     return true;
        | }
      """.stripMargin

    val jsFilterFalse =
      """
        | function filter(row) {
        |     return false;
        | }
      """.stripMargin

    val jsFilterHelloWorldOnly =
      """
        | function filter(row) {
        |      return row != "hello world";
        | }
      """.stripMargin

    "with a js transform operator function that strips `$` with `$hello world`, returns hello world" in {
      val strategy = JavascriptTransformOperator(jsTransform)
      strategy.apply(NonEmptyContent("hello world$", "p").success) must beSuccessful(NonEmptyContent("hello world", "p"))
    }

    "with a js filter operator function returns true, return Content" in {
      val strategy = JavascriptFilterOperator(jsFilterTrue)
      strategy.apply(NonEmptyContent("hello world", "p").success) must beSuccessful(NonEmptyContent("hello world", "p"))
    }

    "with a js filter operator function that only returns false, return FilteredContent" in {
      val strategy = JavascriptFilterOperator(jsFilterFalse)
      strategy.apply(NonEmptyContent("hello world", "p").success) must beSuccessful(FilteredContent)
    }

    "with a js filter operator function that filters out `hello world`, return FilteredContent if content is `hello world`" in {
      val strategy = JavascriptFilterOperator(jsFilterHelloWorldOnly)
      strategy.apply(NonEmptyContent("hello world", "p").success) must beSuccessful(FilteredContent)
    }

  }

  "An invalid js filter" should {

    "fail if js is not well formed" in {
      val badlyFormedJs =
        """
          | function filter(row) {
        """.stripMargin

      val expectedError =
        """<eval>:3:8 Expected } but found eof
          |
          |        ^ in <eval> at line number 3 at column number 8""".stripMargin

      scala.util.Try(JavascriptFilterOperator(badlyFormedJs)) match {
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

      val strategy = JavascriptFilterOperator(missingfunc)
      strategy.apply(NonEmptyContent("abc", "p").success) match {
        case Success(_) => ko("Javascript Filter Operator cannot succeed without a 'filter' function")
        case Failure(f) => f.toString() mustEqual "NonEmptyList(java.lang.NoSuchMethodException: No such function filter)"
      }
    }

    "fail if the js has a runtime error" in {
      val runtimeBloop =
        """
          |function filter(org) { return 1/0; }
        """.stripMargin

      val strategy = JavascriptFilterOperator(runtimeBloop)
      strategy.apply(NonEmptyContent("abc", "p").success) must beFailing
    }
  }

  "An invalid js transform" should {

    "fail if js is not well formed" in {
      val badlyFormedJs =
        """
          | function transform(row) {
        """.stripMargin

      val expectedError =
        """<eval>:3:8 Expected } but found eof
          |
          |        ^ in <eval> at line number 3 at column number 8""".stripMargin

      scala.util.Try(JavascriptTransformOperator(badlyFormedJs)) match {
        case scala.util.Success(_) => ko("Badly formed JS did not generate exception")
        case scala.util.Failure(f) => f.getMessage.replaceAll("\\s", "") mustEqual expectedError.replaceAll("\\s", "")
      }
    }

    "fail if the js is missing a 'transform' function" in {
      val missingfunc =
        """
          |function banana(row) {
          |   return row;
          |}
        """.stripMargin

      val strategy = JavascriptTransformOperator(missingfunc)
      strategy.apply(NonEmptyContent("abc", "p").success) match {
        case Success(_) => ko("Javascript Transform Operator cannot succeed without a 'transform' function")
        case Failure(f) => f.toString() mustEqual "NonEmptyList(java.lang.NoSuchMethodException: No such function transform)"
      }
    }

    "fail if the js has a runtime error" in {
      val runtimeBloop =
        """
          |function transform(org) { return 1/0; }
        """.stripMargin

      val strategy = JavascriptTransformOperator(runtimeBloop)
      strategy.apply(NonEmptyContent("abc", "p").success) must beFailing
    }
  }
}