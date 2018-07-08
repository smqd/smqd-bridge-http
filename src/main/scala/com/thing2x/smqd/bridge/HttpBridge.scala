// Copyright 2018 UANGEL
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.thing2x.smqd.bridge

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{Keep, Sink, Source, SourceQueueWithComplete}
import akka.util.ByteString
import com.thing2x.smqd._
import com.thing2x.smqd.plugin.{AbstractBridge, Bridge, BridgeDriver}
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging
import io.netty.buffer.ByteBuf

import scala.util.{Failure, Success}

/**
  * 2018. 6. 23. - Created by Kwon, Yeong Eon
  *
  * Configuration example
  *
  */
class HttpBridgeDriver(name: String, smqd: Smqd, config: Config) extends BridgeDriver(name, smqd, config) with StrictLogging {

  val parallelism: Int = config.getOptionInt("parallelism").getOrElse(1) match {
    case p if p <= 0 => 1
    case p => p
  }
  val queueSize: Int = config.getOptionInt("queue").getOrElse(10)
  val overflowStrategy: OverflowStrategy = config.getOverflowStrategy("overflow-strategy")

  private var source: Option[SourceQueueWithComplete[HttpRequest]] = None

  override protected def createBridge(filterPath: FilterPath, config: Config, index: Long): Bridge = {
    val method = config.getString("method") match {
      case "POST" => HttpMethods.POST
      case "PUT" => HttpMethods.PUT
      case "GET" => HttpMethods.GET
      case "DELETE" => HttpMethods.DELETE
      case "PATCH" => HttpMethods.PATCH
      case _ => HttpMethods.POST
    }

    val contentType = config.getOptionString("content-type") match {
      case Some(ct) => ct.toLowerCase match {
        case "application/json" => ContentTypes.`application/json`
        case "text/plain" => ContentTypes.`text/plain(UTF-8)`
        case "text/html" => ContentTypes.`text/html(UTF-8)`
        case "text/xml" => ContentTypes.`text/xml(UTF-8)`
        case "application/octet-stream" => ContentTypes.`application/octet-stream`
        case str => ContentType.parse(str) match {
          case Right(x) => x
          case Left(x) =>
            logger.warn(s"HttpBridge mis-configured content-type: $str", x.mkString(", "))
            ContentTypes.`application/octet-stream`
        }
      }

      case None =>
        ContentTypes.`application/octet-stream`
    }
    val path = config.getOptionString("uri")
    val prefix = config.getOptionString("prefix")
    val suffix = config.getOptionString("suffix")

    new HttpBridge(this, index, filterPath, method, contentType, path, prefix, suffix)
  }

  // wsClient: StandaloneAhcWSClient = StandaloneAhcWSClient()
  override protected def connect(): Unit = {

    import smqd.Implicit._

    val http = Http()

    // Materialization with SourceQueue
    //   refer = https://stackoverflow.com/questions/30964824/how-to-create-a-source-that-can-receive-elements-later-via-a-method-call
    val queue = Source.queue[HttpRequest](queueSize, overflowStrategy)
      //.log(s"HttpBridge($name)")
      .mapAsyncUnordered(parallelism) { request =>
        // logger.trace(request.toString)
        http.singleRequest(request)
      }
      .toMat(Sink.last)(Keep.left)
      .run()

    // when Source complete or remote server close connection
    queue.watchCompletion.onComplete {
      case Success(_) =>
        source = None
        http.shutdownAllConnectionPools()
        logger.debug(s"HttpBridgeDriver($name) connection closed.")
      case Failure(ex) =>
        source = None
        http.shutdownAllConnectionPools()
        logger.debug(s"MqttBridgeDriver($name) connection lost: ", ex)
    }

    source = Some(queue)
  }

  override protected def disconnect(): Unit = {
    if (source.isDefined)
      source.get.complete()
    source = None
  }

  def deliver(bridge: HttpBridge, topicPath: TopicPath, msg: Any): Unit = {
    source match {
      case Some(queue) if !isClosed =>

        val path = if (bridge.path.isDefined)
          bridge.path.get
        else
          bridge.prefix.getOrElse("") + topicPath.toString + bridge.suffix.getOrElse("")

        val entity = msg match {
          case bb: ByteBuf =>
            val buf = new Array[Byte](bb.readableBytes)
            bb.getBytes(0, buf)
            HttpEntity(bridge.contentType, buf)
          case bs: ByteString =>
            HttpEntity(bridge.contentType, bs)
          case _ =>
            HttpEntity(msg.toString)
        }
        queue.offer(HttpRequest(bridge.method, uri = path, entity = entity))
        logger.trace(s"HttpBridgeDriver($name) ${bridge.method.value} ${path.toString}, payload: ${entity.contentType.toString} ${entity.contentLength} bytes")

      case _ =>
        logger.warn(s"HttpBridgeDriver($name) is not conntected, messages for '${topicPath.toString}' will be discarded")
    }
  }
}


class HttpBridge(driver: HttpBridgeDriver, index: Long, filterPath: FilterPath, val method: HttpMethod, val contentType: ContentType, val path: Option[String], val prefix: Option[String], val suffix: Option[String]) extends AbstractBridge(driver, index, filterPath) {
  override def bridge(topic: TopicPath, msg: Any): Unit = driver.deliver(this, topic, msg)
}
