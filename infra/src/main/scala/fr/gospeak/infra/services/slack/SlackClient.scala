package fr.gospeak.infra.services.slack

import cats.effect.IO
import fr.gospeak.infra.services.slack.JsonFormats._
import fr.gospeak.infra.services.slack.api.request.{SlackContent, SlackSender}
import fr.gospeak.infra.services.slack.api.{SlackChannel, SlackMessage, SlackToken, SlackUser}
import fr.gospeak.infra.utils.HttpClient
import fr.gospeak.libs.scalautils.Extensions._
import io.circe.parser._

// scala lib: https://github.com/slack-scala-client/slack-scala-client
class SlackClient {
  private val baseUrl = "https://slack.com/api"

  // cf https://api.slack.com/methods/channels.create
  def createChannel(token: SlackToken, name: SlackChannel.Name): IO[SlackChannel] =
    HttpClient.getAsString(s"$baseUrl/channels.create", query = Map(
      "token" -> token.value,
      "name" -> name.value
    )).map(decode[SlackChannel.Single]).flatMap(_.map(_.channel).toIO)

  // cf https://api.slack.com/methods/channels.invite
  def inviteToChannel(token: SlackToken, channel: SlackChannel.Id, user: SlackUser.Id): IO[SlackChannel] =
    HttpClient.getAsString(s"$baseUrl/channels.invite", query = Map(
      "token" -> token.value,
      "channel" -> channel.value,
      "user" -> user.value
    )).map(decode[SlackChannel.Single]).flatMap(_.map(_.channel).toIO)

  // cf https://api.slack.com/methods/channels.list
  def listChannels(token: SlackToken): IO[Seq[SlackChannel]] =
    HttpClient.getAsString(s"$baseUrl/channels.list", query = Map("token" -> token.value))
      .map(decode[SlackChannel.List]).flatMap(_.map(_.channels).toIO)

  // cf https://api.slack.com/methods/users.list
  def listUsers(token: SlackToken): IO[Seq[SlackUser]] =
    HttpClient.getAsString(s"$baseUrl/users.list", query = Map("token" -> token.value))
      .map(decode[SlackUser.List]).flatMap(_.map(_.members).toIO)

  // cf https://api.slack.com/methods/chat.postMessage
  def postMessage(token: SlackToken, channel: SlackChannel.Ref, sender: SlackSender, msg: SlackContent): IO[SlackMessage] =
    HttpClient.getAsString(s"$baseUrl/chat.postMessage", query = sender.toOpts ++ msg.toOpts ++ Map(
      "token" -> token.value,
      "channel" -> channel.value
    )).map(decode[SlackMessage.Posted]).flatMap(_.map(_.message).toIO)
}
