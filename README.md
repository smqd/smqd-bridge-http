# SMQD HTTP Bridge

[![Build Status](https://travis-ci.org/smqd/smqd-bridge-http.svg?branch=develop)](https://travis-ci.org/smqd/smqd-bridge-http)

## Usage

```scala
    resolvers += Resolver.bintrayRepo("smqd", "smqd")

    libraryDependencies += "t2x.smqd" %% "smqd-bridge-http" % "0.1.0"
```

## Configuration

```
smqd {
  bridge {

    drivers = [
      {
        name = http_br
        class = t2x.smqd.bridge.HttpBridgeDriver
        parallelism = 4
        queue = 20
        overflow-strategy = drop-buffer
      }
    ]

    bridges = [
      {
        topic = "sensor/+/temperature"
        driver = http_br
        content-type = text/plain
        method = POST     # PUT, POST
        prefix = http://192.168.1.100/api/receiver/
        suffix = ?from=smqd-01
      },
      {
        topic = "sensor/+/humidity"
        driver = http_br
        content-type = application/octet-stream
        method = POST     # PUT, POST
        uri = http://192.168.1.100/api/receiver/endpoint
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

- _driver_

    should be name of http bridge driver

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
