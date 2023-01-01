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

package io.xeres.app.xrs.service.sliceprobe.item;

import io.netty.channel.ChannelHandlerContext;
import io.xeres.app.xrs.item.Item;
import io.xeres.app.xrs.service.RsServiceType;

public class SliceProbeItem extends Item
{
	public static SliceProbeItem from(ChannelHandlerContext ctx)
	{
		var sliceProbeItem = new SliceProbeItem();
		sliceProbeItem.setOutgoing(ctx.alloc(), 2, RsServiceType.PACKET_SLICING_PROBE, 0xCC);
		return sliceProbeItem;
	}

	@Override
	public String toString()
	{
		return "SliceProbeItem";
	}
}
