/*
 * Copyright 2011-2019 GatlingCorp (https://gatling.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.gatling.jms.request

import java.io.{ Serializable => JSerializable }

import io.gatling.core.action.builder.ActionBuilder
import io.gatling.core.config.GatlingConfiguration
import io.gatling.core.session.{ Expression, ExpressionSuccessWrapper }
import io.gatling.jms.JmsCheck
import io.gatling.jms.action.{ RequestReplyBuilder, SendBuilder }

import com.softwaremill.quicklens._

final case class JmsDslBuilderBase(requestName: Expression[String]) {

  def send(implicit configuration: GatlingConfiguration): SendDslBuilderQueue = SendDslBuilderQueue(requestName, configuration)

  def requestReply(implicit configuration: GatlingConfiguration): RequestReplyDslBuilderQueue = RequestReplyDslBuilderQueue(requestName, configuration)
}

final case class SendDslBuilderQueue(
    requestName: Expression[String],
    configuration: GatlingConfiguration
) {

  def queue(name: Expression[String]): SendDslDslBuilderMessage = destination(JmsQueue(name))

  def destination(destination: JmsDestination): SendDslDslBuilderMessage =
    SendDslDslBuilderMessage(requestName, destination, configuration)
}

final case class RequestReplyDslBuilderQueue(
    requestName: Expression[String],
    configuration: GatlingConfiguration
) {

  def queue(name: Expression[String]): RequestReplyDslBuilderMessage = destination(JmsQueue(name))

  def destination(destination: JmsDestination): RequestReplyDslBuilderMessage =
    RequestReplyDslBuilderMessage(requestName, destination, JmsTemporaryQueue, setJmsReplyTo = true, None, None, configuration)
}

final case class SendDslDslBuilderMessage(
    requestName: Expression[String],
    destination: JmsDestination,
    configuration: GatlingConfiguration
) {

  def textMessage(text: Expression[String]): SendDslBuilder = message(TextJmsMessage(text))
  def bytesMessage(bytes: Expression[Array[Byte]]): SendDslBuilder = message(BytesJmsMessage(bytes))
  def mapMessage(map: Map[String, Any]): SendDslBuilder = mapMessage(map.expressionSuccess)
  def mapMessage(map: Expression[Map[String, Any]]): SendDslBuilder = message(MapJmsMessage(map))
  def objectMessage(o: Expression[JSerializable]): SendDslBuilder = message(ObjectJmsMessage(o))

  private def message(mess: JmsMessage) =
    SendDslBuilder(JmsAttributes(requestName, destination, None, mess), SendBuilder.apply(_, configuration))
}

final case class RequestReplyDslBuilderMessage(
    requestName: Expression[String],
    destination: JmsDestination,
    replyDest: JmsDestination,
    setJmsReplyTo: Boolean,
    trackerDest: Option[JmsDestination],
    messageSelector: Option[String],
    configuration: GatlingConfiguration
) {

  /**
   * Add a reply queue, if not specified dynamic queue is used
   */
  def replyQueue(name: Expression[String]): RequestReplyDslBuilderMessage = replyDestination(JmsQueue(name))
  def replyDestination(destination: JmsDestination): RequestReplyDslBuilderMessage = this.copy(replyDest = destination)
  def noJmsReplyTo: RequestReplyDslBuilderMessage = this.copy(setJmsReplyTo = false)
  def trackerQueue(name: Expression[String]): RequestReplyDslBuilderMessage = trackerDestination(JmsQueue(name))
  def trackerDestination(destination: JmsDestination): RequestReplyDslBuilderMessage = this.copy(trackerDest = Some(destination))

  /**
   * defines selector for reply destination that is used for responses
   */
  def selector(selector: String): RequestReplyDslBuilderMessage = this.copy(messageSelector = Some(selector))

  def textMessage(text: Expression[String]): RequestReplyDslBuilder = message(TextJmsMessage(text))
  def bytesMessage(bytes: Expression[Array[Byte]]): RequestReplyDslBuilder = message(BytesJmsMessage(bytes))
  def mapMessage(map: Map[String, Any]): RequestReplyDslBuilder = mapMessage(map.expressionSuccess)
  def mapMessage(map: Expression[Map[String, Any]]): RequestReplyDslBuilder = message(MapJmsMessage(map))
  def objectMessage(o: Expression[JSerializable]): RequestReplyDslBuilder = message(ObjectJmsMessage(o))

  private def message(mess: JmsMessage) =
    RequestReplyDslBuilder(
      JmsAttributes(requestName, destination, messageSelector, mess),
      RequestReplyBuilder.apply(_, replyDest, setJmsReplyTo, trackerDest, configuration)
    )
}

final case class SendDslBuilder(attributes: JmsAttributes, factory: JmsAttributes => ActionBuilder) {

  /**
   * Add JMS message properties (aka headers) to the outbound message
   */
  def property(key: Expression[String], value: Expression[Any]): SendDslBuilder = this.modify(_.attributes.messageProperties).using(_ + (key -> value))

  def jmsType(jmsType: Expression[String]): SendDslBuilder = this.modify(_.attributes.jmsType).setTo(Some(jmsType))

  def build(): ActionBuilder = factory(attributes)
}

final case class RequestReplyDslBuilder(attributes: JmsAttributes, factory: JmsAttributes => ActionBuilder) {

  /**
   * Add JMS message properties (aka headers) to the outbound message
   */
  def property(key: Expression[String], value: Expression[Any]): RequestReplyDslBuilder = this.modify(_.attributes.messageProperties).using(_ + (key -> value))

  def jmsType(jmsType: Expression[String]): RequestReplyDslBuilder = this.modify(_.attributes.jmsType).setTo(Some(jmsType))

  /**
   * Add a check that will be performed on each received JMS response message before giving Gatling on OK/KO response
   */
  def check(checks: JmsCheck*): RequestReplyDslBuilder = this.modify(_.attributes.checks).using(_ ::: checks.toList)

  def build(): ActionBuilder = factory(attributes)
}
