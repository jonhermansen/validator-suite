package org.w3.vs.model

import org.w3.util._
import org.w3.util.Headers.wrapHeaders
import org.joda.time._
import scalaz.Scalaz._
import scalaz._
import org.w3.banana._
import org.w3.banana.util._
import org.w3.banana.LinkedDataStore._
import org.w3.vs._
import diesel._
import org.w3.vs.store.Binders._
import org.w3.vs.sparql._
import org.w3.banana.util._

object ResourceResponse {

  def bananaGetFor(orgId: OrganizationId, jobId: JobId, runId: RunId)(implicit conf: VSConfiguration): BananaFuture[Set[ResourceResponse]] = {
    import conf._
    bananaGetFor((orgId, jobId, runId).toUri)
  }

  def bananaGetFor(runUri: Rdf#URI)(implicit conf: VSConfiguration): BananaFuture[Set[ResourceResponse]] = {
    import conf._
    for {
      ldr <- store.GET(runUri)
      events <- (ldr.resource / ont.event).asSet[RunEvent]
    } yield {
      events collect { case ResourceResponseEvent(rr, _) => rr }
    }
  }

}

sealed trait ResourceResponse {
  val url: URL
  val method: HttpMethod
}

case class ErrorResponse(
    url: URL,
    method: HttpMethod,
    why: String) extends ResourceResponse

object HttpResponse {

  def apply(
      url: URL,
      method: HttpMethod,
      status: Int,
      headers: Headers,
      body: String): HttpResponse = {
    
    val extractedURLs: List[URL] = headers.mimetype collect {
      case "text/html" | "application/xhtml+xml" => URLExtractor.fromHtml(url, body).distinct
      case "text/css" => URLExtractor.fromCSS(url, body).distinct
    } getOrElse List.empty
    
    HttpResponse(url = url, method = method, status = status, headers = headers, extractedURLs = extractedURLs)
  }

}

case class HttpResponse(
    url: URL,
    method: HttpMethod,
    status: Int,
    headers: Headers,
    extractedURLs: List[URL]) extends ResourceResponse
