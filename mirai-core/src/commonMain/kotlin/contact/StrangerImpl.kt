/*
 * Copyright 2019-2021 Mamoe Technologies and contributors.
 *
 *  此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 *  Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 *  https://github.com/mamoe/mirai/blob/master/LICENSE
 */
@file:OptIn(LowLevelApi::class)
@file:Suppress(
    "EXPERIMENTAL_API_USAGE",
    "DEPRECATION_ERROR",
    "NOTHING_TO_INLINE",
    "INVISIBLE_MEMBER",
    "INVISIBLE_REFERENCE"
)

package net.mamoe.mirai.internal.contact

import net.mamoe.mirai.LowLevelApi
import net.mamoe.mirai.contact.Stranger
import net.mamoe.mirai.contact.User
import net.mamoe.mirai.contact.asFriendOrNull
import net.mamoe.mirai.data.StrangerInfo
import net.mamoe.mirai.event.events.StrangerMessagePostSendEvent
import net.mamoe.mirai.event.events.StrangerMessagePreSendEvent
import net.mamoe.mirai.internal.QQAndroidBot
import net.mamoe.mirai.internal.message.OnlineMessageSourceToStrangerImpl
import net.mamoe.mirai.internal.message.createMessageReceipt
import net.mamoe.mirai.internal.network.protocol.packet.list.StrangerList
import net.mamoe.mirai.message.MessageReceipt
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.utils.cast
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.coroutines.CoroutineContext


@OptIn(ExperimentalContracts::class)
internal inline fun Stranger.impl(): StrangerImpl {
    contract { returns() implies (this@impl is StrangerImpl) }
    check(this is StrangerImpl) { "A Stranger instance is not instance of StrangerImpl. Your instance: ${this::class.qualifiedName}" }
    return this
}

internal class StrangerImpl(
    bot: QQAndroidBot,
    parentCoroutineContext: CoroutineContext,
    override val info: StrangerInfo,
) : Stranger, AbstractUser(bot, parentCoroutineContext, info) {
    override suspend fun delete() {
        check(bot.strangers[this.id] != null) {
            "Stranger ${this.id} had already been deleted"
        }
        bot.network.run {
            StrangerList.DelStranger(bot.client, this@StrangerImpl)
                .sendAndExpect().also {
                    check(it.isSuccess) { "delete Stranger failed: ${it.result}" }
                }
        }
    }

    private val handler: StrangerSendMessageHandler by lazy { StrangerSendMessageHandler(this) }

    @Suppress("DuplicatedCode")
    override suspend fun sendMessage(message: Message): MessageReceipt<Stranger> {
        return asFriendOrNull()?.sendMessage(message)?.convert()
            ?: handler.sendMessageImpl<Stranger>(
                message = message,
                preSendEventConstructor = ::StrangerMessagePreSendEvent,
                postSendEventConstructor = ::StrangerMessagePostSendEvent.cast()
            )
    }

    private fun MessageReceipt<User>.convert(): MessageReceipt<StrangerImpl> {
        return OnlineMessageSourceToStrangerImpl(source, this@StrangerImpl).createMessageReceipt(
            this@StrangerImpl,
            doLightRefine = false //we've already did
        )
    }

    override fun toString(): String = "Stranger($id)"
}
