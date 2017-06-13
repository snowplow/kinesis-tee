# Kinesis Tee

![Build Status][travis-image]
![Release][release-image]
![License][license-image]

## Overview

Kinesis Tee is like **[Unix tee] [tee]**, but for Kinesis streams. Use it to:

1. Transform the format of a Kinesis stream
2. Filter records from a Kinesis stream based on rules
3. Write a Kinesis stream to another Kinesis stream

Rules to apply to your Kinesis stream (e.g. for filtering) are written in JavaScript.

## How it works

You configure Kinesis Tee with a self-describing Avro configuration file containing:

1. A single **source stream** to read records from
2. A single **sink stream** to write records to
3. An optional **stream transformer** to convert the records to another supported format
4. An optional **stream filter** to determine whether to write the records to the sink stream

Here is an example:

```json
{
  "schema": "iglu:com.snowplowanalytics.kinesis-tee/Config/avro/1-0-0",
  "data": {
    "name": "My Kinesis Tee example",
    "targetStream": {
      "name": "my-target-stream",
    },
    "transformer": "SNOWPLOW_TO_NESTED_JSON", // Or "NONE"
    "filter": { // Or null
      "javascript": "BASE64 ENCODED STRING"
    }
  }
}
```

Avro schema for configuration: **[com.snowplowanalytics.kinesistee/config/avro/1-0-0] [config-file]**

## Find out more

|  **[Devops Guide] [devops-guide]**     | **[Developers Guide] [developers-guide]**     |
|:--------------------------------------:|:---------------------------------------------:|
|  [![i1] [devops-image]] [devops-guide] | [![i3] [developers-image]] [developers-guide] |

## Copyright and license

Kinesis Tee is copyright 2015-2016 Snowplow Analytics Ltd.

Licensed under the **[Apache License, Version 2.0] [license]** (the "License");
you may not use this software except in compliance with the License.

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

[developers-guide]: https://github.com/snowplow/kinesis-tee/wiki/Guide-for-developers
[devops-guide]: https://github.com/snowplow/kinesis-tee/wiki/Guide-for-devops-users

[devops-image]:  http://sauna-github-static.s3-website-us-east-1.amazonaws.com/devops.svg
[developers-image]:  http://sauna-github-static.s3-website-us-east-1.amazonaws.com/developer.svg

[travis-image]: https://travis-ci.org/snowplow/kinesis-tee.png?branch=master
[travis]: http://travis-ci.org/snowplow/kinesis-tee

[release-image]: http://img.shields.io/badge/release-0.1.0-blue.svg?style=flat
[releases]: https://github.com/snowplow/kinesis-tee/releases

[license-image]: http://img.shields.io/badge/license-Apache--2-blue.svg?style=flat
[license]: http://www.apache.org/licenses/LICENSE-2.0

[tee]: https://en.wikipedia.org/wiki/Tee_%28command%29

[config-file]: http://iglucentral.com/schemas/com.snowplowanalytics.kinesistee.config/Configuration/avro/1-0-0
