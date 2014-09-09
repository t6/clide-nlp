package clide.nlp.util

import com.typesafe.config.ConfigFactory
import clide.collaboration.{Insert, Operation, Retain}
import clide.persistence.{Schema, DBAccess}
import clide.models._
import scala.slick.driver.H2Driver
import scala.slick.driver.H2Driver.simple._
import play.core.server.NettyServer
import scala.io.Source

object WebStarter {
  val resourcePath = "/clide/nlp/examples/"

  def textFiles: List[(String,String)] = {
    val url = getClass.getResource(resourcePath + "INDEX")
    val index = Source.fromURL(url)
    val files = index.getLines()

    files.map(filename => {
      val url = getClass.getResource(resourcePath + filename)
      val file = Source.fromURL(url)
      val content = file.mkString
      (filename, content)
    }).toList
  }

  def populateDatabase(): Unit = {
    val config = ConfigFactory.load.getConfig("clide-core")
    val database = slick.session.Database.forURL(
      url = config.getString("db.url"),
      user = config.getString("db.user"),
      password = config.getString("db.password"),
      driver = config.getString("db.driver"))

    val dbAccess = DBAccess(database, new Schema(H2Driver))

    val user = new UserInfo("clide-nlp", "tobik@tzi.de").withPassword("clide-nlp")
    val user2 = new UserInfo("clide-nlp2", "tobik@tzi.de").withPassword("clide-nlp")

    dbAccess.db.withSession {
      implicit session: Session =>
        dbAccess.schema.createAllIfNotExist()
        dbAccess.schema.UserInfos.insert(user)
        dbAccess.schema.UserInfos.insert(user2)
        val project = dbAccess.schema.ProjectInfos.create(
          name = "Example",
          owner = user.name,
          description = Some("A project with example texts and an introduction to clide-nlp"),
          public = true
        )

        val projectFolder = dbAccess.schema.FileInfos.create(
          project = project.id,
          path = Seq(),
          mimeType = None,
          deleted = false,
          exists = false,
          isDirectory = true,
          parent = None
        )

        for ((filename, content) <- textFiles)
        yield {
          val file = dbAccess.schema.FileInfos.create(
            project = project.id,
            path = projectFolder.path :+ filename,
            mimeType = Some(if(filename.endsWith(".clj")) "text/x-clojure" else "text/plain"),
            deleted = false,
            exists = false,
            isDirectory = false,
            parent = Some(projectFolder.id)
          )
          dbAccess.schema.Revisions.create(
            file = file.id,
            id = 0,
            content = Operation(List(Insert(content)))
          )
        }
    }
  }

  def main(args: Array[String]) {
    populateDatabase()
    NettyServer.main(args)
  }
}
