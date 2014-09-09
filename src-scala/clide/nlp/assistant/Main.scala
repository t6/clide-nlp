package clide.nlp.assistant
import clojure.java.api.Clojure

object Main {
  val require = Clojure.`var`("clojure.core", "require")
  require.invoke(Clojure.read("clide.nlp.assistant.main"))
  val _main = Clojure.`var`("clide.nlp.assistant.main", "-main")
  
  def main(args: Array[String]): Unit = _main.invoke()
}
