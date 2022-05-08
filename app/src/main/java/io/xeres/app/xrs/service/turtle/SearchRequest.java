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

package io.xeres.app.xrs.service.turtle;

import io.xeres.common.id.LocationId;

import java.time.Instant;

class SearchRequest
{
	private final LocationId source;
	private final Instant lastUsed;
	private final int depth;
	private final String keywords;
	private final int results;
	private final int hitLimit;

	public SearchRequest(LocationId source, int depth, String keywords, int results, int hitLimit)
	{
		this.source = source;
		this.lastUsed = Instant.now();
		this.depth = depth;
		this.keywords = keywords;
		this.results = results;
		this.hitLimit = hitLimit;
	}

	public LocationId getSource()
	{
		return source;
	}

	public Instant getLastUsed()
	{
		return lastUsed;
	}

	public int getDepth()
	{
		return depth;
	}

	public String getKeywords()
	{
		return keywords;
	}

	public int getResults()
	{
		return results;
	}

	public int getHitLimit()
	{
		return hitLimit;
	}
}
