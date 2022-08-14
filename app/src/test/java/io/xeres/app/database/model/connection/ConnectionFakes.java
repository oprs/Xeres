/*
 * Copyright (c) 2019-2020 by David Gerber - https://zapek.com
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

package io.xeres.app.database.model.connection;

import io.xeres.app.net.protocol.PeerAddress;

import java.util.concurrent.ThreadLocalRandom;

public final class ConnectionFakes
{
	private ConnectionFakes()
	{
		throw new UnsupportedOperationException("Utility class");
	}

	public static Connection createConnection()
	{
		var r = ThreadLocalRandom.current();
		return createConnection(PeerAddress.Type.IPV4, "" +
						r.nextInt(11, 110) + "." +
						r.nextInt(1, 254) + "." +
						r.nextInt(1, 254) + "." +
						r.nextInt(1, 254) + ":" +
						r.nextInt(1025, 65534),
				false);
	}

	public static Connection createConnection(PeerAddress.Type type, String address, boolean isExternal)
	{
		var connection = new Connection();
		connection.setType(type);
		connection.setAddress(address);
		connection.setExternal(isExternal);
		return connection;
	}
}
