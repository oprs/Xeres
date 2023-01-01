/*
 * Copyright (c) 2019-2023 by David Gerber - https://zapek.com
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

package io.xeres.app.xrs.service.chat.item;

import io.xeres.app.xrs.item.Item;
import io.xeres.app.xrs.item.ItemPriority;
import io.xeres.app.xrs.serialization.RsSerialized;

import java.util.ArrayList;
import java.util.List;

public class ChatRoomListItem extends Item
{
	@RsSerialized
	private final List<VisibleChatRoomInfo> chatRooms = new ArrayList<>();

	public ChatRoomListItem()
	{
		// Required
	}

	public ChatRoomListItem(List<VisibleChatRoomInfo> chatRooms)
	{
		this.chatRooms.addAll(chatRooms);
	}

	@Override
	public int getPriority()
	{
		return ItemPriority.INTERACTIVE.getPriority();
	}

	@Override
	public String toString()
	{
		return "ChatRoomListItem{" +
				"chatRooms=" + chatRooms +
				'}';
	}

	public List<VisibleChatRoomInfo> getChatRooms()
	{
		return chatRooms;
	}
}
