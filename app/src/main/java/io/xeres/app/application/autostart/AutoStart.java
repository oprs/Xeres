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

package io.xeres.app.application.autostart;

import org.apache.commons.lang3.SystemUtils;
import org.springframework.stereotype.Component;

@Component
public class AutoStart
{
	private final AutoStarter autoStarter;

	public AutoStart()
	{
		if (SystemUtils.IS_OS_WINDOWS)
		{
			autoStarter = new AutoStarterWindows();
		}
		else
		{
			autoStarter = new AutoStarterGeneric();
		}
	}

	public boolean isSupported()
	{
		return autoStarter.isSupported();
	}

	public boolean isEnabled()
	{
		return autoStarter.isEnabled();
	}

	public void enable()
	{
		autoStarter.enable();
	}

	public void disable()
	{
		autoStarter.disable();
	}
}
