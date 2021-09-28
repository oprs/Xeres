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

package io.xeres.common.message.chat;

import java.util.Objects;

public class RoomInfo
{
	private long id;
	private String name;
	private RoomType roomType;
	private String topic;
	private int count;
	private boolean isSigned;

	public RoomInfo()
	{

	}

	public RoomInfo(String name)
	{
		this.name = name;
	}

	public RoomInfo(long id, String name, RoomType roomType, String topic, int count, boolean isSigned)
	{
		this.id = id;
		this.name = name;
		this.roomType = roomType;
		this.topic = topic;
		this.count = count;
		this.isSigned = isSigned;
	}

	public long getId()
	{
		return id;
	}

	public void setId(long id)
	{
		this.id = id;
	}

	public String getName()
	{
		return name;
	}

	public void setName(String name)
	{
		this.name = name;
	}

	public RoomType getRoomType()
	{
		return roomType;
	}

	public void setRoomType(RoomType roomType)
	{
		this.roomType = roomType;
	}

	public String getTopic()
	{
		return topic;
	}

	public void setTopic(String topic)
	{
		this.topic = topic;
	}

	public int getCount()
	{
		return count;
	}

	public void setCount(int count)
	{
		this.count = count;
	}

	public boolean isSigned()
	{
		return isSigned;
	}

	public void setSigned(boolean signed)
	{
		isSigned = signed;
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		var roomInfo = (RoomInfo) o;
		return id == roomInfo.id;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(id);
	}

	@Override
	public String toString()
	{
		return name;
	}
}
