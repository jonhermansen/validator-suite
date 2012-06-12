package org.w3.vs.model

import org.w3.banana._
import scalaz._
import scalaz.Scalaz._
import scalaz.Validation._
import org.joda.time.DateTime
import org.w3.util._

// object Binders {
//   def apply[Rdf <: RDF](diesel: Diesel[Rdf]): Binders[Rdf] = new Binders[Rdf](diesel)
// }

/**
 * creates [EntityGraphBinder]s for the VS entities
 */
case class Binders[Rdf <: RDF](diesel: Diesel[Rdf])
extends UriBuilders[Rdf] with Ontologies[Rdf] with LiteralBinders[Rdf] {

  import diesel._

  implicit val ops = diesel.ops

  val xsd = XSDPrefix(ops)
  val anyURI = xsd("anyURI")

  /* helper: to be moved */

  class IfDefined[S](s: S) {
    def ifDefined[T](opt: Option[T])(func: (S, T) => S) = opt match {
      case None => s
      case Some(t) => func(s, t)
    }
  }

  implicit def addIfDefinedMethod[S](s: S): IfDefined[S] = new IfDefined[S](s)

  /* binders for entities */

  val AssertionVOBinder = new PointedGraphBinder[Rdf, AssertionVO] {

    def toPointedGraph(t: AssertionVO): PointedGraph[Rdf] = (
      AssertionUri(t.id).a(ont.Assertion)
        -- ont.jobId ->- JobUri(t.jobId)
        -- ont.runId ->- RunUri(t.runId)
        -- ont.assertorId ->- AssertorUri(t.assertorId)
        -- ont.url ->- t.url
        -- ont.lang ->- t.lang
        -- ont.title ->- t.title
        -- ont.severity ->- t.severity
        -- ont.description ->- t.description
        -- ont.timestamp ->- t.timestamp
    )

    def fromPointedGraph(pointed: PointedGraph[Rdf]): Validation[BananaException, AssertionVO] = {
      for {
        id <- pointed.as[Rdf#URI] flatMap AssertionUri.getId
        jobId <- (pointed / ont.jobId).as[Rdf#URI] flatMap JobUri.getId
        runId <- (pointed / ont.runId).as[Rdf#URI].flatMap(RunUri.getId)
        assertorId <- (pointed / ont.assertorId).as[Rdf#URI].flatMap(AssertorUri.getId)
        url <- (pointed / ont.url).as[URL]
        lang <- (pointed / ont.lang).as[String]
        title <- (pointed / ont.title).as[String]
        severity <- (pointed / ont.severity).as[AssertionSeverity]
        description <- (pointed / ont.description).asOption[String]
        timestamp <- (pointed / ont.timestamp).as[DateTime]
      } yield {
        AssertionVO(id, jobId, runId, assertorId, url, lang, title, severity, description, timestamp)
      }
    }

  }


  val ContextVOBinder = new PointedGraphBinder[Rdf, ContextVO] {

    def toPointedGraph(t: ContextVO): PointedGraph[Rdf] = (
      ContextUri(t.id).a(ont.Context)
        -- ont.content ->- t.content
        -- ont.line ->- t.line
        -- ont.column ->- t.column
        -- ont.assertionId ->- AssertionUri(t.assertionId)
    )

    def fromPointedGraph(pointed: PointedGraph[Rdf]): Validation[BananaException, ContextVO] = {
      for {
        id <- pointed.as[Rdf#URI] flatMap ContextUri.getId
        content <- (pointed / ont.content).as[String]
        line <- (pointed / ont.line).asOption[Int]
        column <- (pointed / ont.column).asOption[Int]
        assertionId <- (pointed / ont.assertionId).as[Rdf#URI].flatMap(AssertionUri.getId)
      } yield {
        ContextVO(id, content, line, column, assertionId)
      }
    }

  }



  /*val AssertorResultVOBinder = new PointedGraphBinder[Rdf, AssertorResultVO] {

    def toPointedGraph(t: AssertorResultVO): PointedGraph[Rdf] = (
      AssertorResponseUri(t.id).a(ont.AssertorResult)
        -- ont.jobId ->- JobUri(t.jobId)
        -- ont.runId ->- RunUri(t.runId)
        -- ont.assertorId ->- AssertorUri(t.assertorId)
        -- ont.sourceUrl ->- t.sourceUrl
        -- ont.timestamp ->- t.timestamp
    )

    def fromPointedGraph(pointed: PointedGraph[Rdf]): Validation[BananaException, AssertorResultVO] = {
      for {
        id <- pointed.as[Rdf#URI] flatMap AssertorResponseUri.getId
        jobId <- (pointed / ont.jobId).asUri.flatMap(JobUri.getId)
        runId <- (pointed / ont.runId).asUri.flatMap(RunUri.getId)
        assertorId <- (pointed / ont.assertorId).asUri.flatMap(AssertorUri.getId)
        sourceUrl <- (pointed / ont.sourceUrl).as[URL]
        timestamp <- (pointed / ont.timestamp).as[DateTime]
      } yield {
        AssertorResultVO(id, jobId, runId, assertorId, sourceUrl, timestamp)
      }
    }

  }*/



  val JobVOBinder = new PointedGraphBinder[Rdf, JobVO] {

    def toPointedGraph(t: JobVO): PointedGraph[Rdf] = (
      JobUri(t.id).a(ont.Job)
        -- ont.name ->- t.name
        -- ont.createdOn ->- t.createdOn
        -- ont.creator ->- UserUri(t.creatorId)
        -- ont.organization ->- OrganizationUri(t.organizationId)
        -- ont.strategy ->- StrategyUri(t.strategyId)
    )

    def fromPointedGraph(pointed: PointedGraph[Rdf]): Validation[BananaException, JobVO] = {
      for {
        id <- pointed.as[Rdf#URI] flatMap JobUri.getId
        name <- (pointed / ont.name).as[String]
        createdOn <- (pointed / ont.createdOn).as[DateTime]
        creator <- (pointed / ont.creator).as[Rdf#URI] flatMap UserUri.getId
        organization <- (pointed / ont.organization).as[Rdf#URI] flatMap OrganizationUri.getId
        strategy <- (pointed / ont.strategy).as[Rdf#URI] flatMap StrategyUri.getId
      } yield {
        JobVO(id, name, createdOn, creator, organization, strategy)
      }
    }

  }







  val OrganizationVOBinder = new PointedGraphBinder[Rdf, OrganizationVO] {

    def toPointedGraph(t: OrganizationVO): PointedGraph[Rdf] = (
      OrganizationUri(t.id).a(ont.Organization)
        -- ont.name ->- t.name
        -- ont.admin ->- UserUri(t.admin)
    )

    def fromPointedGraph(pointed: PointedGraph[Rdf]): Validation[BananaException, OrganizationVO] = {
      for {
        id <- pointed.as[Rdf#URI] flatMap OrganizationUri.getId
        name <- (pointed / ont.name).as[String]
        adminId <- (pointed / ont.admin).as[Rdf#URI].flatMap(UserUri.getId)
      } yield {
        OrganizationVO(id, name, adminId)
      }
    }

  }




  val ErrorResponseVOBinder = new PointedGraphBinder[Rdf, ErrorResponseVO] {

    def toPointedGraph(t: ErrorResponseVO): PointedGraph[Rdf] = (
      ResourceResponseUri(t.id).a(ont.ResourceResponse).a(ont.ErrorResponse)
        -- ont.jobId ->- JobUri(t.jobId)
        -- ont.runId ->- RunUri(t.runId)
        -- ont.url ->- t.url
        -- ont.action ->- t.action
        -- ont.timestamp ->- t.timestamp
        -- ont.why ->- t.why
    )

    def fromPointedGraph(pointed: PointedGraph[Rdf]): Validation[BananaException, ErrorResponseVO] = {
      for {
        id <- pointed.as[Rdf#URI] flatMap ResourceResponseUri.getId
        jobId <- (pointed / ont.jobId).as[Rdf#URI].flatMap(JobUri.getId)
        runId <- (pointed / ont.runId).as[Rdf#URI].flatMap(RunUri.getId)
        url <- (pointed / ont.url).as[URL]
        action <- (pointed / ont.action).as[HttpAction]
        timestamp <- (pointed / ont.timestamp).as[DateTime]
        why <- (pointed / ont.why).as[String]
      } yield {
        ErrorResponseVO(id, jobId, runId, url, action, timestamp, why)
      }
    }

  }



  val HttpResponseVOBinder = new PointedGraphBinder[Rdf, HttpResponseVO] {

    def toPointedGraph(t: HttpResponseVO): PointedGraph[Rdf] = (
      ResourceResponseUri(t.id).a(ont.ResourceResponse).a(ont.HttpResponse)
        -- ont.jobId ->- JobUri(t.jobId)
        -- ont.runId ->- RunUri(t.runId)
        -- ont.url ->- t.url
        -- ont.action ->- t.action
        -- ont.timestamp ->- t.timestamp
        -- ont.status ->- t.status
        -- ont.headers -->- t.headers
        -- ont.extractedURLs -->- t.extractedURLs
    )

    def fromPointedGraph(pointed: PointedGraph[Rdf]): Validation[BananaException, HttpResponseVO] = {
      for {
        id <- pointed.as[Rdf#URI] flatMap ResourceResponseUri.getId
        jobId <- (pointed / ont.jobId).as[Rdf#URI].flatMap(JobUri.getId)
        runId <- (pointed / ont.runId).as[Rdf#URI].flatMap(RunUri.getId)
        url <- (pointed / ont.url).as[URL]
        action <- (pointed / ont.action).as[HttpAction]
        timestamp <- (pointed / ont.timestamp).as[DateTime]
        status <- (pointed / ont.status).as[Int]
        headers <- (pointed / ont.headers).as[Headers]
        urls <- (pointed / ont.extractedURLs).as[List[URL]]
      } yield {
        HttpResponseVO(id, jobId, runId, url, action, timestamp, status, headers, urls)
      }
    }

  }








  val ResourceResponseVOBinder = new PointedGraphBinder[Rdf, ResourceResponseVO] {

    def toPointedGraph(t: ResourceResponseVO): PointedGraph[Rdf] = t match {
      case e: ErrorResponseVO => ErrorResponseVOBinder.toPointedGraph(e)
      case h: HttpResponseVO => HttpResponseVOBinder.toPointedGraph(h)
    }

    def fromPointedGraph(pointed: PointedGraph[Rdf]): Validation[BananaException, ResourceResponseVO] = {
      // TODO improve banana rdf to avoid this horrible thing...
      if ((pointed / rdf("type")).exists(_.node == ont.ErrorResponse))
        ErrorResponseVOBinder.fromPointedGraph(pointed)
      else
        HttpResponseVOBinder.fromPointedGraph(pointed)
    }

  }

  // does not map distance as this one will be soon removed
  val RunVOBinder = new PointedGraphBinder[Rdf, RunVO] {

    def toPointedGraph(t: RunVO): PointedGraph[Rdf] = (
      RunUri(t.id).a(ont.Run)
        -- ont.explorationMode ->- t.explorationMode
        -- ont.toBeExplored -->- t.toBeExplored
        -- ont.fetched -->- t.fetched.toList
        -- ont.createdAt ->- t.createdAt
        -- ont.jobId ->- JobUri(t.jobId)
        -- ont.resources ->- t.resources
        -- ont.errors ->- t.errors
        -- ont.warnings ->- t.warnings
    )

    def fromPointedGraph(pointed: PointedGraph[Rdf]): Validation[BananaException, RunVO] = {
      for {
        id <- pointed.as[Rdf#URI] flatMap RunUri.getId
        explorationMode <- (pointed / ont.explorationMode).as[ExplorationMode]
        toBeExplored <- (pointed / ont.toBeExplored).as[List[URL]]
        fetched <- (pointed / ont.fetched).as[List[URL]]
        createdAt <- (pointed / ont.createdAt).as[DateTime]
        jobId <- (pointed / ont.jobId).as[Rdf#URI] flatMap JobUri.getId
        resources <- (pointed / ont.resources).as[Int]
        errors <- (pointed / ont.errors).as[Int]
        warnings <- (pointed / ont.warnings).as[Int]
      } yield {
        RunVO(id, explorationMode, Map.empty, toBeExplored, fetched.toSet, createdAt, jobId, resources, errors, warnings)
      }
    }
  }


  val UserVOBinder = new PointedGraphBinder[Rdf, UserVO] {

    def toPointedGraph(t: UserVO): PointedGraph[Rdf] = (
      UserUri(t.id).a(ont.User)
        -- ont.name ->- t.name
        -- ont.email ->- t.email
        -- ont.password ->- t.password
        -- ont.organizationId ->- OrganizationUri(t.organizationId)
    )

    def fromPointedGraph(pointed: PointedGraph[Rdf]): Validation[BananaException, UserVO] = {
      for {
        id <- pointed.as[Rdf#URI] flatMap UserUri.getId
        name <- (pointed / ont.name).as[String]
        email <- (pointed / ont.email).as[String]
        password <- (pointed / ont.password).as[String]
        organizationId <- (pointed / ont.organizationId).as[Rdf#URI] flatMap OrganizationUri.getId
      } yield {
        UserVO(id, name, email, password, organizationId)
      }
    }
  }

  implicit val AssertorSelectorBinder: PointedGraphBinder[Rdf, AssertorSelector] = new PointedGraphBinder[Rdf, AssertorSelector] {

    def toPointedGraph(t: AssertorSelector): PointedGraph[Rdf] = (
      bnode().a(ont.AssertorSelector)
        -- ont.name ->- t.name
        -- ont.map -->- t.map
    )

    def fromPointedGraph(pointed: PointedGraph[Rdf]): Validation[BananaException, AssertorSelector] = {
      for {
        name <- (pointed / ont.name).as[String]
        map <- (pointed / ont.map).as[Map[String, List[String]]]
      } yield {
        AssertorSelector(name, map)
      }
    }

  }

  // works only for Filter(include = Everything, exclude = Nothing) for the moment
  val StrategyVOBinder = new PointedGraphBinder[Rdf, StrategyVO] {

    // WTF?
    // implicit val binder: PointedGraphBinder[Rdf, AssertorSelector] = AssertorSelectorBinder

    def toPointedGraph(t: StrategyVO): PointedGraph[Rdf] = (
      StrategyUri(t.id).a(ont.Strategy)
        -- ont.entrypoint ->- t.entrypoint
        -- ont.distance ->- t.distance
        -- ont.linkCheck ->- t.linkCheck.toString
        -- ont.maxResources ->- t.maxResources
        -- ont.assertorSelector -->- t.assertorSelector
    )

    def fromPointedGraph(pointed: PointedGraph[Rdf]): Validation[BananaException, StrategyVO] = {
      for {
        id <- pointed.as[Rdf#URI] flatMap StrategyUri.getId
        entrypoint <- (pointed / ont.entrypoint).as[URL]
        distance <- (pointed / ont.distance).as[Int]
        linkCheck <- (pointed / ont.linkCheck).as[String].map(_.toBoolean)
        maxResources <- (pointed / ont.maxResources).as[Int]
        assertorSelector <- (pointed / ont.assertorSelector).as[AssertorSelector]
      } yield {
        StrategyVO(id, entrypoint, distance, linkCheck, maxResources, Filter.includeEverything, assertorSelector)
      }
    }
  }



}






// case class Stores[Rdf <: RDF](
//   store: RDFStore[Rdf],
//   binders: Binders[Rdf]) {

//   import binders._

//   val OrganizationStore = EntityStore(store, OrganizationDataBinder)

// }
