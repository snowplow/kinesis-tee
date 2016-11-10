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

import scalaz.syntax.validation._
import scalaz.{Failure, Success}

class JavascriptOperatorSpec extends Specification with ValidationMatchers {

  "A valid JS Operator" should {

    val jsTransform =
      """
        | function operator(row) {
        |     return row.replace("$", "");
        | }
      """.stripMargin

    val jsFilterTrue =
      """
        | function operator(row) {
        |     return true;
        | }
      """.stripMargin

    val jsFilterFalse =
      """
        | function operator(row) {
        |     return false;
        | }
      """.stripMargin

    val jsFilterHelloWorldOnly =
      """
        | function operator(row) {
        |      return row != "hello world";
        | }
      """.stripMargin

    "with a js transform operator function that strips `$` with `$hello world`, returns hello world" in {
      val strategy = JavascriptOperator(jsTransform)
      strategy.apply(NonEmptyContent("hello world$", "p").success) must beSuccessful(NonEmptyContent("hello world", "p"))
    }

    "with a js filter operator function returns true, return Content" in {
      val strategy = JavascriptOperator(jsFilterTrue)
      strategy.apply(NonEmptyContent("hello world", "p").success) must beSuccessful(NonEmptyContent("hello world", "p"))
    }

    "with a js filter operator function that only returns false, return FilteredContent" in {
      val strategy = JavascriptOperator(jsFilterFalse)
      strategy.apply(NonEmptyContent("hello world", "p").success) must beSuccessful(FilteredContent)
    }

    "with a js filter operator function that filters out `hello world`, return FilteredContent if content is `hello world`" in {
      val strategy = JavascriptOperator(jsFilterHelloWorldOnly)
      strategy.apply(NonEmptyContent("hello world", "p").success) must beSuccessful(FilteredContent)
    }

  }

  "An invalid js operator" should {

    "fail if js is not well formed" in {
      val badlyFormedJs =
        """
          | function operator(row) {
        """.stripMargin

      val expectedError =
        """<eval>:3:8 Expected } but found eof
          |
          |        ^ in <eval> at line number 3 at column number 8""".stripMargin

      scala.util.Try(JavascriptOperator(badlyFormedJs)) match {
        case scala.util.Success(_) => ko("Badly formed JS did not generate exception")
        case scala.util.Failure(f) => f.getMessage.replaceAll("\\s", "") mustEqual expectedError.replaceAll("\\s", "")
      }
    }

    "fail if the js is missing a 'operator' function" in {
      val missingfunc =
        """
          |function banana() {
          |   return false;
          |}
        """.stripMargin

      val strategy = JavascriptOperator(missingfunc)
      strategy.apply(NonEmptyContent("abc", "p").success) match {
        case Success(_) => ko("Javascript Operator cannot succeed without a 'operator' function")
        case Failure(f) => f.toString() mustEqual "NonEmptyList(java.lang.NoSuchMethodException: No such function operator)"
      }
    }

    "fail if the js has a runtime error" in {
      val runtimeBloop =
        """
          |function operator(org) { return 1/0; }
        """.stripMargin

      val strategy = JavascriptOperator(runtimeBloop)
      strategy.apply(NonEmptyContent("abc", "p").success) must beFailing
    }

  }
}