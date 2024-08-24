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

package io.xeres.ui;

import io.xeres.common.mui.MinimalUserInterface;
import io.xeres.ui.support.uri.UriService;
import javafx.application.Application;
import javafx.stage.Stage;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.util.Objects;

/**
 * This is only executed in UI mode (that is, without the --no-gui flag).
 */
public class JavaFxApplication extends Application
{
	private ConfigurableApplicationContext springContext;

	private static Class<?> springApplicationClass;

	static void start(Class<?> springApplicationClass, String[] args)
	{
		JavaFxApplication.springApplicationClass = springApplicationClass;
		Application.launch(JavaFxApplication.class, args);
	}

	@Override
	public void init()
	{
		try
		{
			springContext = new SpringApplicationBuilder()
					.sources(springApplicationClass)
					.headless(false) // JavaFX defaults to true which is not what we want
					.run(getParameters().getRaw().toArray(new String[0]));
		}
		catch (Exception e)
		{
			MinimalUserInterface.showError(e);
			System.exit(1);
		}
	}

	@Override
	public void start(Stage primaryStage)
	{
		Objects.requireNonNull(springContext);

		var openUrlService = springContext.getBean(UriService.class);
		openUrlService.setHostServices(getHostServices());

		springContext.publishEvent(new StageReadyEvent(primaryStage));
	}

	@Override
	public void stop()
	{
		springContext.close();
	}
}
