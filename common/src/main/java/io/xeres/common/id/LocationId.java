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

package io.xeres.common.id;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Embeddable;

import java.util.Arrays;
import java.util.Objects;


@Embeddable
public class LocationId implements Identifier, Comparable<LocationId>
{
	public static final int LENGTH = 16;

	private byte[] identifier;

	public LocationId()
	{

	}

	public LocationId(byte[] identifier)
	{
		Objects.requireNonNull(identifier, "Null identifier");
		if (identifier.length != LENGTH)
		{
			throw new IllegalArgumentException("Bad identifier length, expected " + LENGTH + ", got " + identifier.length);
		}
		this.identifier = identifier;
	}

	/**
	 * Creates a {@link LocationId} from a string.
	 *
	 * @param from the string representing the LocationId in hexadecimal form (lowercase, no prefix)
	 * @return the LocationId or an empty LocationId if the string was invalid
	 */
	public static LocationId fromString(String from)
	{
		return new LocationId(Identifier.parseString(from, LENGTH));
	}

	@Override
	public byte[] getBytes()
	{
		return identifier;
	}

	// This is used for serialization (for example passing a GxsId in a STOMP message)
	public void setBytes(byte[] identifier)
	{
		this.identifier = identifier;
	}

	@JsonIgnore
	@Override
	public int getLength()
	{
		return LENGTH;
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (o == null || getClass() != o.getClass())
		{
			return false;
		}
		var that = (LocationId) o;
		return Arrays.equals(identifier, that.identifier);
	}

	@Override
	public int hashCode()
	{
		return Arrays.hashCode(identifier);
	}

	@Override
	public String toString()
	{
		return Id.toString(identifier);
	}

	@Override
	public int compareTo(LocationId o)
	{
		return Arrays.compare(identifier, o.identifier);
	}
}
