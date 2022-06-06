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

package io.xeres.ui.support.util;

import javafx.scene.control.Cell;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;
import org.apache.commons.lang3.StringUtils;

import java.util.function.Supplier;

public final class TooltipUtils
{
	private TooltipUtils()
	{
		throw new UnsupportedOperationException("Utility class");
	}

	public static void installTooltip(@SuppressWarnings("rawtypes") Cell cell, Supplier<String> textSupplier, Supplier<ImageView> graphicSupplier)
	{
		cell.addEventFilter(MouseEvent.MOUSE_ENTERED, event -> {
			if (cell.getItem() != null)
			{
				if (textSupplier == null && graphicSupplier == null)
				{
					return;
				}
				var text = textSupplier != null ? textSupplier.get() : null;
				if (StringUtils.isBlank(text))
				{
					return;
				}
				var tooltip = new Tooltip(text);
				if (graphicSupplier != null)
				{
					tooltip.setGraphic(graphicSupplier.get());
				}
				tooltip.setShowDuration(Duration.minutes(1.0));
				Tooltip.install(cell, tooltip);
			}
		});
		cell.addEventFilter(MouseEvent.MOUSE_EXITED, event -> {
			if (cell.getItem() != null)
			{
				if (cell.getTooltip() != null)
				{
					cell.getTooltip().hide();
					Tooltip.uninstall(cell, cell.getTooltip());
				}
			}
		});
	}
}
