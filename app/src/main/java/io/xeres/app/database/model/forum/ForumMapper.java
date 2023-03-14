/*
 * Copyright (c) 2023 by David Gerber - https://zapek.com
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

package io.xeres.app.database.model.forum;

import io.xeres.app.xrs.service.forum.item.ForumGroupItem;
import io.xeres.common.dto.forum.ForumDTO;

import java.util.List;

import static org.apache.commons.collections4.ListUtils.emptyIfNull;

public final class ForumMapper
{
	private ForumMapper()
	{
		throw new UnsupportedOperationException("Utility class");
	}

	public static ForumDTO toDTO(ForumGroupItem forumGroupItem)
	{
		if (forumGroupItem == null)
		{
			return null;
		}

		return new ForumDTO(
				forumGroupItem.getId(),
				forumGroupItem.getGxsId(),
				forumGroupItem.getName(),
				forumGroupItem.getDescription()
		);
	}

	public static List<ForumDTO> toDTOs(List<ForumGroupItem> forumGroupItems)
	{
		return emptyIfNull(forumGroupItems).stream()
				.map(ForumMapper::toDTO)
				.toList();
	}
}
