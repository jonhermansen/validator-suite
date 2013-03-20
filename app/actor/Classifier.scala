package org.w3.vs.actor

import org.w3.vs.model._
import scala.math.Ordering
import org.w3.util.URL

object Classifier {

  implicit val ordering: Ordering[Classifier] = new Ordering[Classifier] {
    def compare(x: Classifier, y: Classifier): Int = (x, y) match {
      case (ResourceDatasFor(u1), ResourceDatasFor(u2)) => URL.ordering.compare(u1, u2)
      case (AssertionsFor(u1), AssertionsFor(u2)) => URL.ordering.compare(u1, u2)
      case _ => x.hashCode - y.hashCode
    }
  }

  case object AllRunEvents extends Classifier
  case object AllRunDatas extends Classifier
  case object AllResourceDatas extends Classifier
  case class ResourceDatasFor(url: URL) extends Classifier
  case object AllAssertions extends Classifier
  case class AssertionsFor(url: URL) extends Classifier
  case object AllGroupedAssertionDatas extends Classifier

}

sealed trait Classifier {

  import Classifier._

  def matches(event: Any): Boolean = this match {
    case AllRunEvents => event.isInstanceOf[RunEvent]
    case AllRunDatas => event.isInstanceOf[RunData]
    case AllResourceDatas => event.isInstanceOf[ResourceData]
    case ResourceDatasFor(url) => event match {
      case ResourceData(`url`, _, _, _) => true
      case _ => false
    }
    case AllAssertions => event.isInstanceOf[Assertion]
    case AssertionsFor(url) => event match {
      case Assertion(`url`, _, _, _, _, _, _, _) => true
      case _ => false
    }
    case AllGroupedAssertionDatas => event.isInstanceOf[GroupedAssertionData]
  }

}