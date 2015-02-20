package com.eigengo.lift.spark.api

import akka.actor.ActorSystem
import com.typesafe.config.Config
import spray.client.pipelining._
import spray.http.{Uri, HttpResponse, HttpRequest}

import scala.concurrent.Future

trait HttpClient {

  //TODO: External
  private implicit def actorSystem = ActorSystem("spray-client")
  private implicit def ec = actorSystem.dispatcher

  private def pipeline: HttpRequest => Future[HttpResponse] = sendReceive

  def request(requestBuilder: Uri => HttpRequest, config: Config): Future[HttpResponse] = {
    ///exercise/musclegroups

    val uri = Uri(s"http://${config.getString("app.rest.api" )}")
      .withPort(config.getInt("app.rest.port"))

    val request = requestBuilder(uri)

    //TODO: Log
    println(s"Http request $request")

    pipeline(requestBuilder(uri))
  }
}
