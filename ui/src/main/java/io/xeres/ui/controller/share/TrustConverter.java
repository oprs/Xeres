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

package io.xeres.ui.controller.share;

import io.xeres.common.i18n.I18nUtils;
import io.xeres.common.pgp.Trust;
import javafx.util.StringConverter;

import java.util.Arrays;
import java.util.ResourceBundle;

public class TrustConverter extends StringConverter<Trust>
{
	private static final ResourceBundle bundle = I18nUtils.getBundle();

	private enum Permission
	{
		NOBODY(Trust.UNKNOWN, bundle.getString("trust-converter.nobody")),
		ANYBODY(Trust.NEVER, bundle.getString("trust-converter.everybody")),
		MARGINAL(Trust.MARGINAL, bundle.getString("trust-converter.marginal")),
		FULL(Trust.FULL, bundle.getString("trust-converter.full")),
		ULTIMATE(Trust.ULTIMATE, bundle.getString("trust-converter.ultimate"));

		private final Trust trust;
		private final String value;

		Permission(Trust trust, String value)
		{
			this.trust = trust;
			this.value = value;
		}

		public Trust getTrust()
		{
			return trust;
		}

		public String getValue()
		{
			return value;
		}
	}

	@Override
	public String toString(Trust trust)
	{
		return Arrays.stream(Permission.values())
				.filter(permission -> permission.getTrust() == trust)
				.findFirst().orElseThrow()
				.getValue();
	}

	@Override
	public Trust fromString(String s)
	{
		return Arrays.stream(Permission.values())
				.filter(permission -> permission.getValue().equals(s))
				.findFirst().orElseThrow()
				.getTrust();
	}
}
