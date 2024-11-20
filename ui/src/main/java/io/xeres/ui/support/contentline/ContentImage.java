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

package io.xeres.ui.support.contentline;

import io.xeres.common.i18n.I18nUtils;
import io.xeres.ui.custom.ResizeableImageView;
import io.xeres.ui.support.clipboard.ClipboardUtils;
import io.xeres.ui.support.util.UiUtils;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import javafx.stage.Stage;
import net.harawata.appdirs.AppDirsFactory;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignI;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.ResourceBundle;

import static io.xeres.ui.support.util.DateUtils.DATE_TIME_FILENAME;
import static io.xeres.ui.support.util.UiUtils.getWindow;
import static javafx.scene.control.Alert.AlertType.ERROR;

public class ContentImage implements Content
{
	private static final ContextMenu contextMenu;

	private static final ResourceBundle bundle = I18nUtils.getBundle();

	static
	{
		var viewMenuItem = new MenuItem(bundle.getString("view"));
		viewMenuItem.setGraphic(new FontIcon(MaterialDesignI.IMAGE));
		viewMenuItem.setOnAction(ContentImage::view);

		var copyMenuItem = new MenuItem(bundle.getString("copy"));
		copyMenuItem.setGraphic(new FontIcon(MaterialDesignC.CONTENT_COPY));
		copyMenuItem.setOnAction(ContentImage::copyToClipboard);

		var saveAsMenuItem = new MenuItem(bundle.getString("save-as"));
		saveAsMenuItem.setGraphic(new FontIcon(MaterialDesignC.CONTENT_SAVE));
		saveAsMenuItem.setOnAction(ContentImage::saveAs);

		contextMenu = new ContextMenu(viewMenuItem, new SeparatorMenuItem(), copyMenuItem, saveAsMenuItem);
	}

	private final ImageView node;

	public ContentImage(Image image)
	{
		node = new ImageView();

		// Remove ImageView's output scaling so that it's not zoomed in on 4K monitors.
		node.setFitWidth(image.getWidth() / Screen.getPrimary().getOutputScaleX());
		node.setFitHeight(image.getHeight() / Screen.getPrimary().getOutputScaleY());

		node.setImage(image);
		node.setOnContextMenuRequested(event -> contextMenu.show(node, event.getScreenX(), event.getScreenY()));
	}

	@Override
	public Node getNode()
	{
		return node;
	}

	private static void copyToClipboard(ActionEvent event)
	{
		ClipboardUtils.copyImageToClipboard(getImageViewFromEvent(event).getImage());
	}

	private static void saveAs(ActionEvent event)
	{
		SaveFormat saveFormat;

		var bufferedImage = SwingFXUtils.fromFXImage(getImageViewFromEvent(event).getImage(), null);
		if (bufferedImage.getColorModel().hasAlpha())
		{
			saveFormat = new SaveFormat("PNG", List.of("*.png"));
		}
		else
		{
			saveFormat = new SaveFormat("JPG", List.of("*.jpg", "*.jpeg", "*.jfif"));
		}

		var fileChooser = new FileChooser();
		fileChooser.setTitle(bundle.getString("file-requester.save-image-title"));
		fileChooser.setInitialDirectory(new File(AppDirsFactory.getInstance().getUserDownloadsDir(null, null, null)));
		fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(saveFormat.format(), saveFormat.extensions()));
		fileChooser.setInitialFileName("Image_" + DATE_TIME_FILENAME.format(Instant.now()) + saveFormat.getPrimaryExtension());

		var selectedFile = fileChooser.showSaveDialog(getWindow(event));
		if (selectedFile != null)
		{
			try
			{
				if (!ImageIO.write(bufferedImage, saveFormat.format(), selectedFile))
				{
					UiUtils.alert(ERROR, "Couldn't find a writer");
				}
			}
			catch (IOException e)
			{
				UiUtils.alert(ERROR, e.getMessage());
			}
		}
	}

	private static void view(ActionEvent event)
	{
		var imageView = getImageViewFromEvent(event);

		var resizeableImageView = new ResizeableImageView();
		resizeableImageView.setPreserveRatio(true);
		resizeableImageView.setPickOnBounds(true);
		resizeableImageView.setImageProper(imageView.getImage());

		var hbox = new HBox(resizeableImageView);
		HBox.setHgrow(resizeableImageView, Priority.ALWAYS);
		hbox.setAlignment(Pos.CENTER);

		var vbox = new VBox(hbox);
		VBox.setVgrow(hbox, Priority.ALWAYS);

		var scene = new Scene(vbox, imageView.getImage().getWidth(), imageView.getImage().getHeight());
		var stage = new Stage();
		stage.setTitle("Image Viewer");
		stage.setScene(scene);
		stage.setFullScreen(true);
		stage.setFullScreenExitHint(""); // There's no way to show the hint only once or quickly so...
		scene.setOnMouseClicked(mouseEvent -> {
			if (mouseEvent.getButton() == MouseButton.PRIMARY)
			{
				stage.hide();
			}
		});
		scene.setOnKeyPressed(keyEvent -> {
			if (keyEvent.getCode() == KeyCode.ESCAPE)
			{
				stage.hide();
			}
		});
		stage.show();
	}

	private static ImageView getImageViewFromEvent(ActionEvent event)
	{
		var selectedMenuItem = (MenuItem) event.getTarget();

		var popup = Objects.requireNonNull(selectedMenuItem.getParentPopup());
		return (ImageView) popup.getOwnerNode();
	}

	private record SaveFormat(String format, List<String> extensions)
	{
		String getPrimaryExtension()
		{
			return extensions.getFirst().substring(1);
		}
	}
}
