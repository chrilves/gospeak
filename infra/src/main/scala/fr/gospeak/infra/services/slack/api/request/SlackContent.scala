package fr.gospeak.infra.services.slack.api.request

sealed trait SlackContent {
  def toOpts: Map[String, String]
}

object SlackContent {

  final case class Markdown(text: String) extends SlackContent {
    def toOpts: Map[String, String] = Map(
      "mrkdwn" -> "true",
      "text" -> text)
  }

}
