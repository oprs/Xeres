/*
 * Copyright (c) 2024 by David Gerber - https://zapek.com
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

package io.xeres.app.xrs.service.turtle.item;

import io.xeres.app.xrs.serialization.RsSerialized;

public class TurtleGenericDataItem extends TurtleGenericTunnelItem implements Cloneable
{
	@RsSerialized
	private byte[] tunnelData;

	public TurtleGenericDataItem()
	{
		// Required
	}

	@Override
	public int getSubType()
	{
		return 10;
	}

	@Override
	public boolean shouldStampTunnel()
	{
		return true;
	}

	public byte[] getTunnelData()
	{
		return tunnelData;
	}

	@Override
	public String toString()
	{
		return "TurtleGenericDataItem{" +
				"tunnelData.length=" + getTunnelData().length +
				'}';
	}

	@Override
	public TurtleGenericDataItem clone()
	{
		var clone = (TurtleGenericDataItem) super.clone();
		clone.tunnelData = tunnelData.clone();
		return clone;
	}
}
