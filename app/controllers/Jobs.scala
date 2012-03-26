package controllers

import play.api._
import play.api.mvc._
import play.api.data.Form
import play.api.libs.json._
import play.api.libs.iteratee._
import play.api.libs.concurrent._
import org.w3.util._
import org.w3.util.Pimps._
import org.w3.vs.controllers._
import org.w3.vs.exception._
import org.w3.vs.model._
import org.w3.vs.actor._
import org.w3.vs.actor.message._
import scalaz._
import Scalaz._
import Validation._
import akka.dispatch.Await
import akka.dispatch.Future
import akka.util.Duration
import akka.util.duration._
import java.util.concurrent.TimeUnit._

object Jobs extends Controller {
  
  val logger = play.Logger.of("Controller.Jobs")
  // TODO: make the implicit explicit!!!
  implicit def configuration = org.w3.vs.Prod.configuration
  
  import Application._
  
  def redirect = Action { implicit req => Redirect(routes.Jobs.index) }
  
  def index = Action { implicit req =>
    AsyncResult {
      val futureResult = for {
        user <- getAuthenticatedUserOrResult
        jobs <- User.getJobs(user.organization) failMap toResult(Some(user))
        viewInputs <- {
          val sortedJobs = jobs.toList.sortWith{(a, b) => a.configuration.createdOn.toString() < b.configuration.createdOn.toString()}
          val iterableFuture = sortedJobs map { job => job.jobData map (data => (job.configuration, data)) }
          val futureIterable = Future.sequence(iterableFuture)
          futureIterable.lift
        }.failMap(t => toResult(Some(user))(StoreException(t)))
      } yield {
        Ok(views.html.dashboard(viewInputs, user))
      }
      futureResult.expiresWith(InternalServerError, 1, SECONDS).toPromise
    }
  }
  
  def show(id: JobId, messages: List[(String, String)] = List.empty) = Action { implicit req =>
    AsyncResult {
      val futureResult =
        for {
          user <- getAuthenticatedUserOrResult
          job <- getJobIfAllowed(user, id) failMap toResult(Some(user))
          data <- job.jobData.lift failMap {t => toResult(Some(user))(Unexpected(t))}
          ars <- Job.getAssertorResults(job.id) failMap toResult(Some(user))
        } yield {
          Ok(views.html.job(job.configuration, data, sort(filter(ars)), user, messages))
        }
      futureResult.expiresWith(InternalServerError, 1, SECONDS).toPromise()
    }
  }
  private def sort(ar: Iterable[AssertorResult]) = {
    ar
  }
  private def filter(ar: Iterable[AssertorResult]) = {
    ar.collect{case a: Assertions => a}
  }
  
  // TODO: This should also stop the job and kill the actor
  def delete(id: JobId) = Action { implicit req =>
    AsyncResult {
      val futureResult =
        for {
          user <- getAuthenticatedUserOrResult
          job <- getJobIfAllowed(user, id) failMap toResult(Some(user))
          _ <- Job.delete(id) failMap toResult(Some(user))
        } yield {
          if (isAjax) Ok else SeeOther(routes.Jobs.index.toString).flashing(("info" -> "Job deleted"))
        }
      futureResult.expiresWith(InternalServerError, 1, SECONDS).toPromise
    }
  }
  
  def new1 = newOrEditJob(None)
  def edit(id: JobId) = newOrEditJob(Some(id))
  def create = createOrUpdateJob(None)
  def update(id: JobId) = createOrUpdateJob(Some(id))
  
  def on(id: JobId) = simpleJobAction(id)(user => job => job.on())("Job on")
  def off(id: JobId) = simpleJobAction(id)(user => job => job.off())("Job off")
  def refresh(id: JobId) = simpleJobAction(id)(user => job => job.refresh())("Job restarted")
  def stop(id: JobId) = simpleJobAction(id)(user => job => job.stop())("Job stopped")
  
  def dispatcher(implicit id: JobId) = Action { implicit req =>
    (for {
      body <- req.body.asFormUrlEncoded
      param <- body.get("action")
      action <- param.headOption
    } yield action.toLowerCase match {
      case "delete" => delete(id)(req)
      case "update" => update(id)(req)
      case "on" => on(id)(req)
      case "off" => off(id)(req)
      case "stop" => stop(id)(req)
      case "refresh" => refresh(id)(req)
      case _ => BadRequest("BadRequest: unknown action")
    }).getOrElse(BadRequest("BadRequest: JobDispatcher"))
  }
  
  def dashboardSocket() = WebSocket.using[JsValue] { implicit req =>
    val seed = new UpdateData(null)
    
    // I don't know how to flatten a Promise[(iteratee, enumerator)] so i use another for loop for now
    val promiseIteratee = (for {
      user <- getAuthenticatedUser
      jobConfs <- Job.getAll(user.organization)
      enumerators <- Future.sequence(jobConfs map { jobConf => JobsActor.getJobOrCreate(jobConf).map(_.subscribeToUpdates) }).lift
    } yield {
      Iteratee.ignore[JsValue].mapDone[Unit]{_ => 
      	enumerators map { enum =>
      	  logger.error("Closing enumerator: " + enum)
      	  enum.close // This doesn't work: PushEnumerator:close() does not call the enumerator's onComplete method, possibly a bug.
      	}
      }
    }).failMap(_ => Iteratee.ignore[JsValue]).expiresWith(Iteratee.ignore[JsValue], 1, SECONDS).toPromiseT[Iteratee[JsValue, Unit]]

    val promiseEnumerator = (for {
      user <- getAuthenticatedUser
      jobConfs <- Job.getAll(user.organization)
      enumerators <- Future.sequence(jobConfs map { jobConf => JobsActor.getJobOrCreate(jobConf).map(_.subscribeToUpdates) }).lift
    } yield {
      val out = {
        val foo = enumerators map { (enum: Enumerator[RunUpdate]) =>
          // Filter the enumerator, taking only the UpdateData messages
          enum &> Enumeratee.collect[RunUpdate] { case e: UpdateData => e } &>
            // Transform to a tuple (updateData, sameAsPrevious)
            Enumeratee.scanLeft[UpdateData]((seed, false)) {
              case ((prev, _), to) if to != prev => (to, false)
              case (_, to) => (to, true)
            }
          // Interleave the resulting enumerators
        }
        foo.reduce((e1, e2) => e1 >- e2) &>
          // And collect messages that are marked as changed
          Enumeratee.collect { case (a, false) => a.toJS }
      }
      out
    }).failMap(_ => Enumerator.eof[JsValue]).expiresWith(Enumerator.eof[JsValue], 1, SECONDS).toPromiseT[Enumerator[JsValue]]
    
    val enumerator: Enumerator[JsValue] = Enumerator.flatten(promiseEnumerator)
    val iteratee: Iteratee[JsValue, Unit] = Iteratee.flatten(promiseIteratee)
    
    (iteratee, enumerator)
  }
  
  /*
   * Private methods
   */
  private def newOrEditJob(implicit idOpt: Option[JobId]) = Action { implicit req =>
    AsyncResult {
      val futureResult =
        for {
          user <- getAuthenticatedUserOrResult
          id <- idOpt.toImmediateSuccess(ifNone = Ok(views.html.jobForm(jobForm, user )))
          jobC <- getJobConfIfAllowed(user, id) failMap toResult(Some(user))
        } yield {
          Ok(views.html.jobForm(jobForm.fill(jobC), user))
        }
      futureResult.expiresWith(InternalServerError, 1, SECONDS).toPromise
    }
  }
  
  private def createOrUpdateJob(implicit idOpt: Option[JobId]) = Action { implicit req =>
    AsyncResult {
      val futureResult =
        for {
          user <- getAuthenticatedUserOrResult
          jobF <- isValidForm(jobForm).toImmediateValidation failMap { formWithErrors =>
            BadRequest(views.html.jobForm(formWithErrors, user))
          }
          result <- idOpt match {
            case None =>
              for {
                _ <- Job.save(jobF.assignTo(user)) failMap toResult(Some(user))
              } yield {
                if (isAjax) Created(views.html.libs.messages(List(("info" -> "Job created")))) 
                else        SeeOther(routes.Jobs.show(jobF.id).toString).flashing(("info" -> "Job created"))
              }
            case Some(id) =>
              for {
                jobC <- getJobConfIfAllowed(user, id) failMap toResult(Some(user))
                 _ <- Job.save(jobC.copy(strategy = jobF.strategy, name = jobF.name)) failMap toResult(Some(user))
              } yield {
                if (isAjax) Created(views.html.libs.messages(List(("info" -> "Job updated")))) 
                else        SeeOther(routes.Jobs.show(jobF.id).toString).flashing(("info" -> "Job updated"))
              }
          }
        } yield {
          result
        }
      futureResult.expiresWith(InternalServerError, 1, SECONDS).toPromise
    }
  }
  
  private def simpleJobAction(id: JobId)(action: User => Job => Any)(msg: String) = Action { implicit req =>
    AsyncResult {
      val futureResult =
        for {
          user <- getAuthenticatedUserOrResult
          job <- getJobIfAllowed(user, id) failMap toResult(Some(user))
        } yield {
          action(user)(job)
          if (isAjax) Accepted(views.html.libs.messages(List(("info" -> msg)))) 
          else        SeeOther(routes.Jobs.show(job.id).toString).flashing(("info" -> msg))
        }
      futureResult.expiresWith(InternalServerError, 1, SECONDS).toPromise
    }
  }

  private def getJobIfAllowed(user: User, id: JobId): FutureValidationNoTimeOut[SuiteException, Job] =
    for {
      jobConf <- getJobConfIfAllowed(user, id)
      job <- JobsActor.getJobOrCreate(jobConf).liftWith { case t => Unexpected(t) }
    } yield job

  private def getJobConfIfAllowed(user: User, id: JobId): FutureValidationNoTimeOut[SuiteException, JobConfiguration] = {
    for {
      jobConfO <- Job.get(id)
      jobConf <- jobConfO.toSuccess(UnknownJob).toImmediateValidation
      jobConfAllowed <- {
        val validation = if (jobConf.organization === user.organization) Success(jobConf) else Failure(UnauthorizedJob)
        validation.toImmediateValidation
      }
    } yield jobConfAllowed
  }
  
}