/*
 * Copyright (c) 2019-2022 by David Gerber - https://zapek.com
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

package io.xeres.app.database.model.chat;

import io.xeres.common.dto.chat.ChatIdentityDTO;
import io.xeres.common.dto.chat.ChatRoomContextDTO;
import io.xeres.common.dto.chat.ChatRoomDTO;
import io.xeres.common.dto.chat.ChatRoomsDTO;
import io.xeres.common.message.chat.ChatRoomContext;
import io.xeres.common.message.chat.ChatRoomInfo;

import java.util.List;

import static org.apache.commons.collections4.ListUtils.emptyIfNull;

public final class ChatMapper
{
	private ChatMapper()
	{
		throw new UnsupportedOperationException("Utility class");
	}

	public static ChatRoomContextDTO toDTO(ChatRoomContext chatRoomContext)
	{
		if (chatRoomContext == null)
		{
			return null;
		}
		return new ChatRoomContextDTO(
				new ChatRoomsDTO(toDTOs(chatRoomContext.getChatRoomLists().getSubscribed()), toDTOs(chatRoomContext.getChatRoomLists().getAvailable())),
				new ChatIdentityDTO(chatRoomContext.getOwnUser().getNickname(), chatRoomContext.getOwnUser().getGxsId())
		);
	}

	public static List<ChatRoomDTO> toDTOs(List<ChatRoomInfo> chatRoomInfoList)
	{
		return emptyIfNull(chatRoomInfoList).stream()
				.map(ChatMapper::toDTO)
				.toList();
	}

	public static ChatRoomDTO toDTO(ChatRoomInfo chatRoomInfo)
	{
		return new ChatRoomDTO(
				chatRoomInfo.getId(),
				chatRoomInfo.getName(),
				chatRoomInfo.getRoomType(),
				chatRoomInfo.getTopic(),
				chatRoomInfo.getCount(),
				chatRoomInfo.isSigned()
		);
	}
}
