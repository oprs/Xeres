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

package io.xeres.ui.support.contentline;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ContentUtils
{
	private static final Pattern URL_PATTERN = Pattern.compile("\\b((?:https?|ftps?)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|])");

	private ContentUtils()
	{
		throw new UnsupportedOperationException("Utility class");
	}

	public static void parseInlineUrls(String s, List<Content> contents)
	{
		var matcher = URL_PATTERN.matcher(s);
		var previousRange = new Range(0, 0);

		// Find URLs
		while (matcher.find())
		{
			var currentRange = new Range(matcher);

			// Text before/between URLs
			var betweenRange = currentRange.textRange(previousRange);
			if (betweenRange.hasRange())
			{
				contents.add(new ContentText(s.substring(betweenRange.start, betweenRange.end)));
			}

			// URL
			contents.add(new ContentUri(s.substring(currentRange.start, currentRange.end)));

			previousRange = currentRange;
		}

		if (!previousRange.hasRange())
		{
			// Text if no URL at all
			contents.add(new ContentText(s));
		}
		else if (previousRange.end < s.length())
		{
			// Text after the last URL
			contents.add(new ContentText(s.substring(previousRange.end)));
		}
	}

	private static class Range
	{
		private final int start;
		private final int end;

		public Range(Matcher matcher)
		{
			start = matcher.start(1);
			end = matcher.end();
		}

		public Range(int start, int end)
		{
			this.start = start;
			this.end = end;
		}

		public boolean hasRange()
		{
			return end > start;
		}

		public Range textRange(Range other)
		{
			if (other.start > start)
			{
				// other is after us
				return new Range(end, other.start);
			}
			else
			{
				// other is before us
				return new Range(other.end, start);
			}
		}
	}
}
