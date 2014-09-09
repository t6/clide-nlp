package clide.nlp.assistant

import clide.collaboration._
import clojure.java.api.Clojure
import scala.collection.JavaConverters._
import clide.collaboration.Delete
import clide.collaboration.Retain
import clide.collaboration.Insert
import clide.collaboration.AnnotationType
import scala.util.{Failure, Success, Try}

object Utilities {
  private val name = Clojure.`var`("clojure.core", "name")
  private val `int` = Clojure.`var`("clojure.core", "int")
  private val isInt = Clojure.`var`("clojure.core", "integer?")
  private val vector = Clojure.`var`("clojure.core", "vector")
  private val retain = Clojure.read(":retain")
  private val insert = Clojure.read(":insert")
  private val delete = Clojure.read(":delete")

  def name(o: Object): String = name.invoke(o).asInstanceOf[String]
  def integer(o: Object): Integer =
    if(isInt.invoke(o).equals(true)) `int`.invoke(o).asInstanceOf[Integer]
    else throw new IllegalArgumentException(o + " not an int!")

  def asList(o: Object): List[Object] = o.asInstanceOf[java.util.List[Object]].asScala.toList

  def annotationTypeFromString(s: String): Option[AnnotationType.Value] =
  // TODO: Check if there is another way than
  // reflection. AnnotationType.withName does not work here.
    (for {
      method <- Try(AnnotationType.getClass.getMethod(s))
      value  <- Try(method.invoke(AnnotationType).asInstanceOf[AnnotationType.Value])
    } yield value) match {
      case Failure(_) => None
      case Success(annotationType) => Some(annotationType)
    }

  /**
   * Translate annotations represented as Clojure data to an Clide Annotations object
   * @param data the Clojure data representing the annotations
   * @return a Clide Annotations object
   */
  def dataToAnnotations(data: Object): Annotations =
    if (data == null) Annotations()
    else {
      val xs = asList(data)
      val annotations = for (x <- xs) yield asList(x) match {
        case List(k, length) if "plain".equals(name(k)) =>
          // Clojure integers can be one of long, int or BigInteger
          // we need to make sure that we get an int before we cast.
          // clojure.core/int should take care of that.
          Plain(integer(length))
        case List(k, length, contents) if "annotate".equals(name(k)) =>
          val content = for (c <- asList(contents))
          yield asList(c) match {
              case List(annotationType, s) =>
                (for {
                  n <- Option(name(annotationType))
                  v <- annotationTypeFromString(n)
                } yield v) match {
                  case Some(value) => (value, s.asInstanceOf[String])
                  case None => throw new IllegalArgumentException("nil name for " + annotationType + "?")
                }
              case _ => throw new IllegalArgumentException("Couldn't translate " + contents)
            }

          Annotate(integer(length), content)
        case _ => throw new IllegalArgumentException("Couldn't translate " + x)
      }
      Annotations(annotations)
    }

  /**
   * Translate Clide edit operations to a Clojure data representation
   * @param op the Clide edit operations to translate
   * @return
   */
  def operationToData(op: Operation): Object =
    (for (action <- op.actions) yield action match {
      // Clojure does not know about scala.Int and we need to explicitly cast
      // them to java.lang.Integer to prevent hard to track Boxed errors
      case Retain(n) => vector.invoke(retain, n.asInstanceOf[Integer])
      case Insert(s) => vector.invoke(insert, s)
      case Delete(n) => vector.invoke(delete, n.asInstanceOf[Integer])
    }).asJava
}
