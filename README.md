# SMQD HTTP Bridge

[![Build Status](https://travis-ci.org/smqd/smqd-bridge-http.svg?branch=develop)](https://travis-ci.org/smqd/smqd-bridge-http)
[![Sonatype Nexus (Releases)](https://img.shields.io/nexus/r/https/oss.sonatype.org/com.thing2x/smqd-bridge-http_2.12.svg)](https://oss.sonatype.org/content/groups/public/com/thing2x/smqd-bridge-http_2.12/)
[![Sonatype Nexus (Snapshots)](https://img.shields.io/nexus/s/https/oss.sonatype.org/com.thing2x/smqd-bridge-http_2.12.svg)](https://oss.sonatype.org/content/groups/public/com/thing2x/smqd-bridge-http_2.12/)
[![License](http://img.shields.io/:license-apache-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)

## Usage

```scala
    libraryDependencies += "com.thing2x.smqd" %% "smqd-bridge-http" % "x.y.z"
```

## Configuration

```
smqd {
  bridge {

    drivers = [
      {
        name = http_br
        entry.plugin = thing2x-bridge-http
        config {
            parallelism = 4
            queue = 20
            overflow-strategy = drop-buffer
            bridges = [
              {
                topic = "sensor/+/temperature"
                content-type = text/plain
                method = POST     # PUT, POST
                prefix = http://192.168.1.100/api/receiver/
                suffix = ?from=smqd-01
              },
              {
                topic = "sensor/+/humidity"
                content-type = application/octet-stream
                method = POST     # PUT, POST
                uri = http://192.168.1.100/api/receiver/endpoint
              }
            ]
        }
      }
    ]
  }
}
```

### driver settings

- _parallelism_

    must be non-zero positive integer, default is 1

- _queue_

    size of driver's queue, default is 10

- _overflow-strategy_

    queue overflow strategy

    - drop-head
    - drop-tail
    - drop-buffer (default)
    - drop-new
    - backpressure
    - fail

### bridge settings

- _topic_

    topic filter that the bridge will subscribe

- _content-type_

    if not specified, default is `application/octet-stream`

- _method_

    http method, POST, PUT

- _prefix_ & _suffix_

    the final http endpoint will be _prefix_ + topic + _suffix_.

    As example, if _prefix_ = `http://192.168.1.100:80/api/receiver/`, _suffix_ = `?from=smq-01`, 
    the messages published to `sensor/123/temperature` will be
    delivered to `http://192.168.1.100:80/api/receiver/sensor/123/temperature?from=smq-01`

- _uri_

    if _uri_ is defined, _prefix_ and _suffix_ are ignored.

    all messages published to the _topic_ will be bridged to the specified uri
