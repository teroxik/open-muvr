package com.eigengo.lift.spark.api

import akka.actor.ActorSystem
import com.typesafe.config.Config
import org.slf4j.{LoggerFactory, Logger}
import spray.client.pipelining._
import spray.http.{Uri, HttpResponse, HttpRequest}
import spray.http.StatusCodes._

import scala.concurrent.Future

/**
 * Spark HttpClient
 */
trait HttpClient {

  //TODO: External
  private implicit lazy val actorSystem = ActorSystem("spray-client")

  private implicit lazy val ec = actorSystem.dispatcher

  private lazy val pipeline: HttpRequest => Future[HttpResponse] = sendReceive

  private lazy val logger = LoggerFactory.getLogger(classOf[HttpClient])

  /**
   * Creates a Http request to given uri
   * @param requestBuilder builds request, i.e. GET/POST/..,path etc.
   * @param config configuration containing connection details to the main app
   * @return Future of Right if status was 200 or Left otherwise
   */
  def request(requestBuilder: Uri => HttpRequest, config: Config): Future[Either[String, String]] = {
    val uri = Uri(s"http://${config.getString("app.rest.api")}")
      .withPort(config.getInt("app.rest.port"))

    val request = requestBuilder(uri)

    logger.info(s"Http request $request")

    pipeline(requestBuilder(uri)).map { r => r.status match {
      case OK => {
        logger.info(s"Request succeeded: $r")
        Right(s"Request succeeded")
      }
      case s => {
        logger.warn(s"Request failed: $r")
        Left(s"Request failed with status $s")
      }
    }}
  }
}