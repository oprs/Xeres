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

package io.xeres.ui.support.window;

import io.xeres.common.AppName;
import io.xeres.common.id.Sha1Sum;
import io.xeres.common.message.chat.ChatAvatar;
import io.xeres.common.message.chat.ChatMessage;
import io.xeres.common.rest.file.AddDownloadRequest;
import io.xeres.common.rest.forum.PostRequest;
import io.xeres.common.rest.location.RSIdResponse;
import io.xeres.ui.OpenUriEvent;
import io.xeres.ui.client.GeneralClient;
import io.xeres.ui.client.ProfileClient;
import io.xeres.ui.client.ShareClient;
import io.xeres.ui.client.message.MessageClient;
import io.xeres.ui.controller.MainWindowController;
import io.xeres.ui.controller.WindowController;
import io.xeres.ui.controller.about.AboutWindowController;
import io.xeres.ui.controller.account.AccountCreationWindowController;
import io.xeres.ui.controller.chat.ChatRoomCreationWindowController;
import io.xeres.ui.controller.chat.ChatRoomInvitationWindowController;
import io.xeres.ui.controller.debug.PropertiesWindowController;
import io.xeres.ui.controller.debug.UiCheckWindowController;
import io.xeres.ui.controller.file.FileAddDownloadViewController;
import io.xeres.ui.controller.forum.ForumCreationWindowController;
import io.xeres.ui.controller.forum.ForumEditorViewController;
import io.xeres.ui.controller.id.AddRsIdWindowController;
import io.xeres.ui.controller.identity.IdentitiesWindowController;
import io.xeres.ui.controller.messaging.BroadcastWindowController;
import io.xeres.ui.controller.messaging.MessagingWindowController;
import io.xeres.ui.controller.messaging.PeersWindowController;
import io.xeres.ui.controller.profile.ProfilesWindowController;
import io.xeres.ui.controller.qrcode.CameraWindowController;
import io.xeres.ui.controller.qrcode.QrCodeWindowController;
import io.xeres.ui.controller.settings.SettingsWindowController;
import io.xeres.ui.controller.share.ShareWindowController;
import io.xeres.ui.controller.statistics.StatisticsMainController;
import io.xeres.ui.model.profile.Profile;
import io.xeres.ui.support.markdown.MarkdownService;
import io.xeres.ui.support.preference.PreferenceService;
import io.xeres.ui.support.theme.AppThemeManager;
import io.xeres.ui.support.uri.*;
import io.xeres.ui.support.util.UiUtils;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;
import net.rgielen.fxweaver.core.FxWeaver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.*;
import java.util.prefs.BackingStoreException;

import static javafx.scene.control.Alert.AlertType.WARNING;
import static org.apache.commons.lang3.StringUtils.isEmpty;

/**
 * Class that tries to overcome the half-assed JavaFX window system.
 */
@Component
public class WindowManager
{
	private static FxWeaver fxWeaver;
	private final ProfileClient profileClient;
	private final MessageClient messageClient;
	private final ShareClient shareClient;
	private final MarkdownService markdownService;
	private final UriService uriService;
	private static ResourceBundle bundle;
	private static PreferenceService preferenceService;
	private static AppThemeManager appThemeManager;

	private static WindowBorder windowBorder;
	private final GeneralClient generalClient;
	private Window rootWindow;

	private UiWindow mainWindow;

	public WindowManager(FxWeaver fxWeaver, ProfileClient profileClient, MessageClient messageClient, ShareClient shareClient, MarkdownService markdownService, UriService uriService, ResourceBundle bundle, PreferenceService preferenceService, AppThemeManager appThemeManager, GeneralClient generalClient)
	{
		WindowManager.fxWeaver = fxWeaver;
		this.profileClient = profileClient;
		this.messageClient = messageClient;
		this.shareClient = shareClient;
		this.markdownService = markdownService;
		this.uriService = uriService;
		WindowManager.bundle = bundle;
		WindowManager.preferenceService = preferenceService;
		WindowManager.appThemeManager = appThemeManager;
		this.generalClient = generalClient;
	}

	public void setRootWindow(Window window)
	{
		rootWindow = window;
	}

	public void closeAllWindows()
	{
		Platform.runLater(() ->
		{
			var windows = getOpenedWindows();

			// There's a strange side effect here when windows are hidden, apparently JavaFX changes the list, so
			// we make a copy.
			var copyOfWindows = new ArrayList<>(windows);
			copyOfWindows.forEach(Window::hide);
		});
	}

	@EventListener
	public void handleOpenUriEvents(OpenUriEvent event)
	{
		switch (event.uri())
		{
			case CertificateUri certificateUri -> openAddPeer(certificateUri.radix());
			case FileUri(String name, long size, Sha1Sum hash) -> openAddDownload(new AddDownloadRequest(name, size, hash, null));
			case ChatRoomUri ignored ->
			{
				// Nothing to do. This is handled in ChatViewController
			}
			case ForumUri ignored ->
			{
				// Nothing to do. This is handled in ForumViewController
			}
			case SearchUri ignored ->
			{
				// Nothing to do. This is handled in SearchViewController
			}
			default -> UiUtils.alert(WARNING, "The link for '" + event.uri().getClass().getSimpleName().replace("Uri", "") + "' is not supported yet.");
		}
	}

	public void openPeers()
	{
		Platform.runLater(() ->
		{
			var peers = getOpenedWindow(PeersWindowController.class).orElse(null);
			if (peers != null)
			{
				peers.requestFocus();
			}
			else
			{
				UiWindow.builder(PeersWindowController.class)
						.setRememberEnvironment(true)
						.build()
						.open();
			}
		});
	}

	public void openMessaging(String locationId, ChatMessage chatMessage)
	{
		Platform.runLater(() ->
				getOpenedWindow(MessagingWindowController.class, locationId).ifPresentOrElse(window ->
						{
							window.requestFocus();
							((MessagingWindowController) window.getUserData()).showMessage(chatMessage);
						},
						() ->
						{
							if (chatMessage == null || !chatMessage.isEmpty()) // Don't open a window for a typing notification, we're not psychic (but do open when we double click)
							{
								var messaging = new MessagingWindowController(profileClient, generalClient, this, uriService, messageClient, shareClient, markdownService, locationId, bundle);

								UiWindow.builder("/view/messaging/messaging.fxml", messaging)
										.setLocalId(locationId)
										.setUserData(chatMessage)
										.build()
										.open();
							}
						}));
	}

	public void sendMessaging(String locationId, ChatAvatar chatAvatar)
	{
		Platform.runLater(() ->
				getOpenedWindow(MessagingWindowController.class, locationId).ifPresent(window ->
						((MessagingWindowController) window.getUserData()).showAvatar(chatAvatar)
				)
		);
	}

	public void openAbout()
	{
		Platform.runLater(() ->
				UiWindow.builder(AboutWindowController.class)
						.setParent(rootWindow)
						.setTitle(MessageFormat.format(bundle.getString("about.window-title"), AppName.NAME))
						.build()
						.open());
	}

	public void openShare()
	{
		Platform.runLater(() ->
				UiWindow.builder(ShareWindowController.class)
						.setParent(rootWindow)
						.setTitle("Shares")
						.build()
						.open());
	}

	public void openSystemProperties()
	{
		Platform.runLater(() ->
				UiWindow.builder(PropertiesWindowController.class)
						.setParent(rootWindow)
						.setTitle("System Properties")
						.build()
						.open());
	}

	public void openUiCheck()
	{
		Platform.runLater(() ->
				UiWindow.builder(UiCheckWindowController.class)
						.setParent(rootWindow)
						.setTitle("Custom UI")
						.build()
						.open());
	}

	public void openQrCode(RSIdResponse rsIdResponse)
	{
		Platform.runLater(() ->
				UiWindow.builder(QrCodeWindowController.class)
						.setParent(rootWindow)
						.setTitle(bundle.getString("qrcode.window-title"))
						.setUserData(rsIdResponse)
						.build()
						.open());
	}

	public void openCamera(AddRsIdWindowController parentController)
	{
		Platform.runLater(() ->
				UiWindow.builder(CameraWindowController.class)
						.setParent(rootWindow)
						.setTitle(bundle.getString("camera.window-title"))
						.setResizeable(false)
						.setUserData(parentController)
						.build()
						.open());
	}

	public void openChatRoomCreation()
	{
		Platform.runLater(() ->
				UiWindow.builder(ChatRoomCreationWindowController.class)
						.setParent(rootWindow)
						.setTitle(bundle.getString("chat.room.create.window-title"))
						.build()
						.open());
	}

	public void openBroadcast()
	{
		Platform.runLater(() ->
				UiWindow.builder(BroadcastWindowController.class)
						.setParent(rootWindow)
						.setTitle(bundle.getString("broadcast.window-title"))
						.build()
						.open());
	}

	public void openProfiles()
	{
		Platform.runLater(() ->
				UiWindow.builder(ProfilesWindowController.class)
						.setParent(rootWindow)
						.setTitle(bundle.getString("profiles.window-title"))
						.build()
						.open());
	}

	public void openIdentities()
	{
		Platform.runLater(() ->
				UiWindow.builder(IdentitiesWindowController.class)
						.setParent(rootWindow)
						.setTitle(bundle.getString("identities.window-title"))
						.build()
						.open());
	}

	public void openStatistics()
	{
		Platform.runLater(() -> {
			var stats = getOpenedWindow(StatisticsMainController.class).orElse(null);
			if (stats != null)
			{
				stats.requestFocus();
			}
			else
			{
				UiWindow.builder(StatisticsMainController.class)
						.setRememberEnvironment(true)
						.setTitle("Statistics")
						.build()
						.open();
			}
		});
	}

	public void openSettings()
	{
		Platform.runLater(() ->
				UiWindow.builder(SettingsWindowController.class)
						.setParent(rootWindow)
						.setTitle(bundle.getString("settings.window-title"))
						.build()
						.open());
	}

	public void openAddPeer()
	{
		openAddPeer(null);
	}

	public void openAddPeer(String rsId)
	{
		Platform.runLater(() ->
				UiWindow.builder(AddRsIdWindowController.class)
						.setParent(rootWindow)
						.setTitle(bundle.getString("rsid.add.window-title"))
						.setUserData(rsId)
						.build()
						.open());
	}

	public void openAddDownload(AddDownloadRequest addDownloadRequest)
	{
		Platform.runLater(() ->
				UiWindow.builder(FileAddDownloadViewController.class)
						.setParent(rootWindow)
						.setTitle(bundle.getString("download.add.window-title"))
						.setUserData(addDownloadRequest)
						.build()
						.open());
	}

	public void openInvite(long chatRoom)
	{
		Platform.runLater(() ->
				UiWindow.builder(ChatRoomInvitationWindowController.class)
						.setParent(rootWindow)
						.setTitle(bundle.getString("chat.room.invite.window-title"))
						.setUserData(chatRoom)
						.build()
						.open());
	}

	public void openForumEditor(PostRequest postRequest)
	{
		Platform.runLater(() ->
				UiWindow.builder(ForumEditorViewController.class)
						.setParent(rootWindow) // XXX: needs to become multi modal to avoid blocking (useful to browse other posts while we write)
						.setTitle(bundle.getString("forum.new-message.window-title"))
						.setUserData(postRequest)
						.build()
						.open());
	}

	public void openForumCreation()
	{
		Platform.runLater(() ->
				UiWindow.builder(ForumCreationWindowController.class)
						.setParent(rootWindow)
						.setTitle(bundle.getString("forum.create.window-title"))
						.build()
						.open());
	}

	public void openMain(Stage stage, Profile profile, boolean iconified)
	{
		Platform.runLater(() -> {

			if (mainWindow != null && !iconified)
			{
				mainWindow.open();
			}
			else
			{
				var location = profile.getLocations().stream().findFirst().orElseThrow();
				preferenceService.setLocation(location);

				appThemeManager.applyCurrentTheme();

				mainWindow = UiWindow.builder(MainWindowController.class)
						.setStage(stage)
						.setRememberEnvironment(true)
						.setTitle(AppName.NAME + " - " + profile.getName() + " @ " + location.getName())
						.build();

				if (!iconified)
				{
					mainWindow.open();
				}
			}
		});
	}

	public Stage getMainStage()
	{
		return mainWindow.stage;
	}

	public void openAccountCreation(Stage stage)
	{
		appThemeManager.applyCurrentTheme();

		Platform.runLater(() -> UiWindow.builder(AccountCreationWindowController.class)
				.setStage(stage)
				.build()
				.open());
	}

	/**
	 * Calculates the window's decoration. This must be performed on the first stage so
	 * that the next opened windows will have the correct sizes.
	 *
	 * @param stage the primary stage
	 */
	public void calculateWindowDecorationSizes(Stage stage)
	{
		windowBorder = UiBorders.calculateWindowDecorationSizes(stage);
	}

	static Optional<Window> getOpenedWindow(Class<? extends WindowController> controllerClass)
	{
		return Window.getWindows().stream()
				.filter(window -> Objects.equals(window.getScene().getRoot().getId(), controllerClass.getName()))
				.findFirst();
	}

	static Optional<Window> getOpenedWindow(Class<? extends WindowController> controllerClass, String localId)
	{
		return Window.getWindows().stream()
				.filter(window -> Objects.equals(window.getScene().getRoot().getId(), controllerClass.getName() + ":" + localId))
				.findFirst();
	}

	static List<Window> getOpenedWindows()
	{
		return Window.getWindows();
	}


	static final class UiWindow
	{
		private static final Logger log = LoggerFactory.getLogger(UiWindow.class);

		private static final String KEY_WINDOW_X = "PosX";
		private static final String KEY_WINDOW_Y = "PosY";
		private static final String KEY_WINDOW_WIDTH = "Width";
		private static final String KEY_WINDOW_HEIGHT = "Height";
		public static final String NODE_WINDOWS = "Windows";

		final Scene scene;
		final Stage stage;

		private UiWindow(Builder builder)
		{
			scene = new Scene(builder.root);
			UiUtils.setDefaultStyle(scene);
			stage = Objects.requireNonNullElseGet(builder.stage, Stage::new);
			UiUtils.setDefaultIcon(stage);

			if (builder.parent != null)
			{
				stage.initOwner(builder.parent);
				stage.initModality(Modality.WINDOW_MODAL);
			}
			if (builder.localId != null)
			{
				if (!builder.root.getId().contains(":"))
				{
					throw new IllegalArgumentException("LocalId used for unique window " + builder.root.getId());
				}
				var tokens = builder.root.getId().split(":");
				builder.root.setId(tokens[0] + ":" + builder.localId);
			}
			else
			{
				if (builder.root.getId().contains(":"))
				{
					throw new IllegalArgumentException("Missing localId for non unique window " + builder.root.getId());
				}
			}
			if (builder.userData != null)
			{
				builder.root.setUserData(builder.userData);
			}

			// Set the minimums to the root's minimums + decorations.
			stage.setMinWidth(builder.root.minWidth(-1) + (int) windowBorder.leftSize() + (int) windowBorder.rightSize()); // There's some rounding errors in JavaFX somewhere. int is a bit better
			stage.setMinHeight(builder.root.minHeight(-1) + (int) windowBorder.topSize() + (int) windowBorder.bottomSize());

			stage.setTitle(builder.title);
			stage.setScene(scene);

			loadWindowPreferences(stage, builder);

			if (!builder.resizeable)
			{
				stage.setResizable(false);
			}

			stage.setOnShowing(event -> builder.controller.onShowing());
			stage.setOnShown(event -> {
				builder.controller.onShown();
				UiBorders.setDarkModeOnOpeningWindow(appThemeManager.getCurrentTheme().isDark());
			});
			stage.setOnHiding(event -> {
				saveWindowPreferences(stage, builder);
				builder.controller.onHiding();
			});
			stage.setOnHidden(event -> builder.controller.onHidden());

			scene.getWindow().setUserData(builder.controller);
		}

		private void loadWindowPreferences(Stage stage, Builder builder)
		{
			var id = builder.root.getId();

			if (!builder.rememberEnvironment)
			{
				return;
			}

			if (isEmpty(id))
			{
				throw new IllegalArgumentException("A Window requires an ID");
			}

			boolean preferencesExist;
			try
			{
				preferencesExist = preferenceService.getPreferences().nodeExists(NODE_WINDOWS + "/" + id);
			}
			catch (BackingStoreException e)
			{
				log.debug("Error while trying to retrieve Windows' preferences: {}", e.getMessage());
				preferencesExist = false;
			}

			if (preferencesExist)
			{
				var preferences = preferenceService.getPreferences().node(NODE_WINDOWS).node(id);
				stage.setX(preferences.getDouble(KEY_WINDOW_X, 0));
				stage.setY(preferences.getDouble(KEY_WINDOW_Y, 0));
				stage.setWidth(preferences.getDouble(KEY_WINDOW_WIDTH, 0));
				stage.setHeight(preferences.getDouble(KEY_WINDOW_HEIGHT, 0));
			}
		}

		private void saveWindowPreferences(Stage stage, Builder builder)
		{
			var id = builder.root.getId();

			if (!builder.rememberEnvironment)
			{
				return;
			}

			if (isEmpty(id))
			{
				throw new IllegalArgumentException("A Window requires an ID");
			}

			var preferences = preferenceService.getPreferences().node(NODE_WINDOWS).node(id);
			preferences.putDouble(KEY_WINDOW_X, stage.getX());
			preferences.putDouble(KEY_WINDOW_Y, stage.getY());
			preferences.putDouble(KEY_WINDOW_WIDTH, stage.getWidth());
			preferences.putDouble(KEY_WINDOW_HEIGHT, stage.getHeight());
			log.debug("Saving Window {}, x: {}, y: {}, width: {}, height: {}", id, stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
		}

		/**
		 * Opens the window.
		 */
		void open()
		{
			stage.show();
		}

		/**
		 * Closes the window.
		 */
		void close()
		{
			stage.close();
		}

		static Builder builder(Class<? extends WindowController> controllerClass)
		{
			var parent = (Parent) fxWeaver.loadView(controllerClass, bundle);
			parent.setId(controllerClass.getName());
			return new Builder(parent, fxWeaver.getBean(controllerClass));
		}

		static Builder builder(String resource, WindowController controller)
		{
			var fxmlLoader = new FXMLLoader(UiWindow.class.getResource(resource), bundle);
			fxmlLoader.setController(controller);
			Parent parent;
			try
			{
				parent = fxmlLoader.load();
			}
			catch (IOException e)
			{
				throw new IllegalArgumentException("Failed to load FXML: " + e.getMessage(), e);
			}
			parent.setId(controller.getClass().getName() + ":" + UUID.randomUUID()); // This is a default ID to enforce uniqueness
			return new Builder(parent, controller);
		}

		/**
		 * This class is used to build UiWindows.
		 */
		static final class Builder
		{
			private Stage stage;
			private final Parent root;
			private final WindowController controller;
			private Window parent;
			private String title = AppName.NAME;
			private String localId;
			private Object userData;
			private boolean rememberEnvironment;
			private boolean resizeable = true;

			private Builder(Parent root, WindowController controller)
			{
				this.root = root;
				this.controller = controller;
			}

			/**
			 * Sets a parent for the window, hence making it a modal window.
			 *
			 * @param parent the parent
			 * @return the builder
			 */
			Builder setParent(Window parent)
			{
				this.parent = parent;
				return this;
			}

			/**
			 * Sets a stage for the window. If not provided, a default stage will be created.
			 *
			 * @param stage the stage
			 * @return the builder
			 */
			Builder setStage(Stage stage)
			{
				this.stage = stage;
				return this;
			}

			/**
			 * Sets a title for the window that will be shown in the title bar.
			 *
			 * @param title the window title
			 * @return the builder
			 */
			Builder setTitle(String title)
			{
				this.title = title;
				return this;
			}

			/**
			 * Sets a custom window Id
			 *
			 * @param id the window id
			 * @return the builder
			 */
			Builder setLocalId(String id)
			{
				localId = id;
				return this;
			}

			/**
			 * Remembers the window size and position.
			 *
			 * @param remember true if remembering is needed (defaults to false)
			 * @return the builder
			 */
			Builder setRememberEnvironment(boolean remember)
			{
				rememberEnvironment = remember;
				return this;
			}

			/**
			 * Allows the window to be resized.
			 *
			 * @param resizeable true if resizeable, false if fixed (defaults to true)
			 * @return the builder
			 */
			Builder setResizeable(boolean resizeable)
			{
				this.resizeable = resizeable;
				return this;
			}

			/**
			 * Sets a user data in the window. Can be used for anything.
			 *
			 * @param userData the user data
			 * @return the builder
			 */
			Builder setUserData(Object userData)
			{
				this.userData = userData;
				return this;
			}

			/**
			 * Builds the UiWindow.
			 *
			 * @return the UiWindow
			 */
			UiWindow build()
			{
				return new UiWindow(this);
			}
		}
	}
}
