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

package io.xeres.ui.controller.settings;

import io.xeres.ui.client.SettingsClient;
import io.xeres.ui.controller.WindowController;
import io.xeres.ui.model.settings.Settings;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import net.rgielen.fxweaver.core.FxWeaver;
import net.rgielen.fxweaver.core.FxmlView;
import org.springframework.stereotype.Component;

import java.util.ResourceBundle;

@Component
@FxmlView(value = "/view/settings/settings.fxml")
public class SettingsWindowController implements WindowController
{
	private final SettingsClient settingsClient;

	@FXML
	private ListView<SettingsGroup> listView;

	private Settings originalSettings;
	private Settings newSettings;

	@FXML
	private AnchorPane content;

	private final FxWeaver fxWeaver;
	private final ResourceBundle bundle;

	public SettingsWindowController(SettingsClient settingsClient, FxWeaver fxWeaver, ResourceBundle bundle)
	{
		this.settingsClient = settingsClient;
		this.fxWeaver = fxWeaver;
		this.bundle = bundle;
	}

	@Override
	public void initialize()
	{
		listView.setCellFactory(SettingsCell::new);
		listView.getItems().addAll(
				new SettingsGroup(bundle.getString("settings.general"), new ImageView("/image/settings_general.png"), SettingsGeneralController.class),
				new SettingsGroup(bundle.getString("settings.network"), new ImageView("/image/settings_networks.png"), SettingsNetworksController.class));

		listView.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
			saveContent();

			content.getChildren().clear();
			if (newValue.controllerClass() != null)
			{
				var controllerAndView = fxWeaver.load(newValue.controllerClass(), bundle);
				controllerAndView.getController().onLoad(newSettings);

				var view = controllerAndView.getView().orElseThrow();

				content.getChildren().add(view);
				AnchorPane.setTopAnchor(view, 0.0);
				AnchorPane.setBottomAnchor(view, 0.0);
				AnchorPane.setLeftAnchor(view, 0.0);
				AnchorPane.setRightAnchor(view, 0.0);

				view.setUserData(controllerAndView.getController());
			}
		});

		listView.setDisable(true);

		settingsClient.getSettings().doOnSuccess(settings -> Platform.runLater(() -> {
					originalSettings = settings;
					newSettings = originalSettings.clone();
					listView.setDisable(false);
					listView.getSelectionModel().selectFirst();
				}))
				.subscribe();
	}

	@Override
	public void onHidden()
	{
		saveContent();

		if (newSettings != null)
		{
			settingsClient.patchSettings(originalSettings, newSettings)
					.subscribe();
		}
	}

	private void saveContent()
	{
		if (!content.getChildren().isEmpty())
		{
			var controller = (SettingsController) content.getChildren().get(0).getUserData();
			newSettings = controller.onSave();
		}
	}
}
