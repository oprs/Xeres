/*
 * Copyright (c) 2019-2021 by David Gerber - https://zapek.com
 *
 * This file is part of Xeres.
 *
 * Xeres is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Xeres is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Xeres.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.xeres.app.web.api.controller.chat;

import io.xeres.app.xrs.service.chat.ChatService;
import io.xeres.common.id.LocationId;
import io.xeres.common.message.MessageType;
import io.xeres.common.message.chat.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import javax.validation.Valid;

import static io.xeres.common.message.MessageHeaders.DESTINATION_ID;
import static io.xeres.common.message.MessageHeaders.MESSAGE_TYPE;
import static io.xeres.common.rest.PathConfig.CHAT_PATH;

@Controller
public class ChatMessageController
{
	private static final Logger log = LoggerFactory.getLogger(ChatMessageController.class);

	private final ChatService chatService;

	public ChatMessageController(ChatService chatService)
	{
		this.chatService = chatService;
	}

	@MessageMapping(CHAT_PATH)
	public void processMessageFromClient(SimpMessageHeaderAccessor accessor, @Payload @Valid ChatMessage message)
	{
		String destinationId = accessor.getFirstNativeHeader(DESTINATION_ID);
		var messageType = MessageType.valueOf(accessor.getFirstNativeHeader(MESSAGE_TYPE));

		switch (messageType)
		{
			case CHAT_PRIVATE_MESSAGE -> {
				assert destinationId != null;
				log.debug("Received websocket message, sending to peer location: {}, content {}", destinationId, message);
				chatService.sendPrivateMessage(new LocationId(destinationId), message.getContent());
			}
			case CHAT_ROOM_MESSAGE -> {
				assert destinationId != null;
				log.debug("Sending to room: {}, content {}", destinationId, message);
				chatService.sendChatRoomMessage(Long.parseLong(destinationId), message.getContent());
			}
			case CHAT_BROADCAST_MESSAGE -> {
				log.debug("Sending broadcast message");
				chatService.sendBroadcastMessage(message.getContent());
			}
			case CHAT_TYPING_NOTIFICATION -> {
				assert destinationId != null;
				log.debug("Sending typing notification...");
				chatService.sendPrivateTypingNotification(new LocationId(destinationId));
			}
			case CHAT_ROOM_TYPING_NOTIFICATION -> {
				assert destinationId != null;
				log.debug("Sending typing notification...");
				chatService.sendChatRoomTypingNotification(Long.parseLong(destinationId));
			}
			default -> log.error("Couldn't figure out which message to send");
		}
	}

	@MessageExceptionHandler
	@SendToUser("/queue/errors") // XXX: how can we use this? Well, it works... just have to subscribe to it
	public String handleException(Throwable e)
	{
		log.debug("Got exception: {}", e.getMessage(), e);
		return e.getMessage();
	}
}
