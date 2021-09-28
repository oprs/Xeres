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

package io.xeres.app.xrs.serialization;

import io.netty.buffer.ByteBuf;
import io.xeres.app.xrs.common.SecurityKey;
import io.xeres.common.id.GxsId;
import io.xeres.common.id.Id;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;

import static io.xeres.app.xrs.serialization.Serializer.*;
import static io.xeres.app.xrs.serialization.TlvType.*;

final class TlvSecurityKeySerializer
{
	private static final Logger log = LoggerFactory.getLogger(TlvSecurityKeySerializer.class);

	private TlvSecurityKeySerializer()
	{
		throw new UnsupportedOperationException("Utility class");
	}

	static int serialize(ByteBuf buf, SecurityKey securityKey)
	{
		log.trace("Writing TlvRsaKey");

		int len = getSize(securityKey);
		buf.ensureWritable(len);
		buf.writeShort(SECURITY_KEY.getValue());
		buf.writeInt(len);
		TlvSerializer.serialize(buf, STR_KEY_ID, Id.toString(securityKey.getGxsId())); // XXX: RS uses some weird bytes to ascii here... not sure this is correct :-/ (I first used Id.toAsciiBytes() but it wants a string. see deserialization)

		Serializer.serialize(buf, EnumSet.of(SecurityKey.Flags.DISTRIBUTION_ADMIN), FieldSize.INTEGER); // keyFlags (XXX: set them to TSTLV_KEY_DISTRIB_ADMIN, possibly others)
		Serializer.serialize(buf, 0); // startTS (XXX: I think it stays at 0)
		Serializer.serialize(buf, 0); // endTS (XXX: I think it stays at 0)

		TlvSerializer.serialize(buf, KEY_EVP_PKEY, securityKey.getData());
		return len;
	}

	static int getSize(SecurityKey securityKey)
	{
		return TLV_HEADER_SIZE
				+ TlvSerializer.getSize(STR_KEY_ID)
				+ 4
				+ 4
				+ 4
				+ TLV_HEADER_SIZE + securityKey.getData().length; // XXX: add a getSize() accessor
	}

	static SecurityKey deserialize(ByteBuf buf)
	{
		log.trace("Reading TlvRsaKey");

		TlvUtils.checkTypeAndLength(buf, SECURITY_KEY);
		var gxsId = new GxsId(Id.asciiStringToBytes((String) TlvSerializer.deserialize(buf, STR_KEY_ID)));
		var flags = deserializeEnumSet(buf, SecurityKey.Flags.class, FieldSize.INTEGER);
		var startTs = deserializeInt(buf);
		var endTs = deserializeInt(buf);

		var data = (byte[]) TlvSerializer.deserialize(buf, KEY_EVP_PKEY);
		return new SecurityKey(gxsId, flags, startTs, endTs, data);
	}
}
