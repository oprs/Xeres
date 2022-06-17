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

package io.xeres.ui.support.uri;

import io.xeres.ui.support.contentline.Content;
import io.xeres.ui.support.contentline.ContentText;
import io.xeres.ui.support.contentline.ContentUri;
import io.xeres.ui.support.util.UiUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.stream.Stream;

public class IdentityContentParser implements ContentParser
{
	@Override
	public String getProtocol()
	{
		return PROTOCOL_RETROSHARE;
	}

	@Override
	public String getAuthority()
	{
		return "identity";
	}

	@Override
	public Content parse(URI uri, String text)
	{
		var uriComponents = UriComponentsBuilder.fromPath(uri.getPath())
				.query(uri.getQuery())
				.build();

		var gxsId = uriComponents.getQueryParams().getFirst("gxsid");
		var name = uriComponents.getQueryParams().getFirst("name");
		var groupData = uriComponents.getQueryParams().getFirst("groupdata");

		if (Stream.of(gxsId, name, groupData).anyMatch(StringUtils::isBlank))
		{
			return ContentText.EMPTY;
		}

		return new ContentUri(groupData, "Identity (name=" + name + ", ID=" + gxsId + ")", data -> UiUtils.showAlertInfo("Adding identities is not supported yet."));
	}
}
