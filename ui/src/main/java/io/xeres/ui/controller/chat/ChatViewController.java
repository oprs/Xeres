/*
 * Copyright (c) 2019-2021 by David Gerber - https://zapek.com
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

package io.xeres.ui.controller.chat;

import io.xeres.common.AppName;
import io.xeres.common.dto.identity.IdentityConstants;
import io.xeres.common.message.chat.*;
import io.xeres.common.rest.location.RSIdResponse;
import io.xeres.common.rsid.Type;
import io.xeres.ui.client.ChatClient;
import io.xeres.ui.client.LocationClient;
import io.xeres.ui.client.ProfileClient;
import io.xeres.ui.client.message.MessageClient;
import io.xeres.ui.controller.Controller;
import io.xeres.ui.support.util.ImageUtils;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import net.rgielen.fxweaver.core.FxmlView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

import static io.xeres.common.dto.location.LocationConstants.OWN_LOCATION_ID;
import static io.xeres.common.message.chat.ChatConstants.TYPING_NOTIFICATION_DELAY;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.ObjectUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Component
@FxmlView(value = "/view/chat/chatview.fxml")
public class ChatViewController implements Controller
{
	private static final Logger log = LoggerFactory.getLogger(ChatViewController.class);

	private static final int IMAGE_WIDTH_MAX = 640;
	private static final int IMAGE_HEIGHT_MAX = 480;
	private static final int MESSAGE_MAXIMUM_SIZE = 31000; // XXX: put that on chat service too as we shouldn't forward them. also this is only for chat rooms, not private chats

	@FXML
	private TreeView<RoomHolder> roomTree;

	@FXML
	private SplitPane splitPane;

	@FXML
	private VBox content;

	@FXML
	private TextField send;

	@FXML
	private VBox sendGroup;

	@FXML
	private Label typingNotification;

	@FXML
	private HBox previewGroup;

	@FXML
	private ImageView imagePreview;

	@FXML
	private Button previewSend;

	@FXML
	private Button previewCancel;

	@FXML
	private VBox userListContent;

	private final MessageClient messageClient;
	private final ChatClient chatClient;
	private final ProfileClient profileClient;
	private final LocationClient locationClient;

	private final TreeItem<RoomHolder> subscribedRooms = new TreeItem<>(new RoomHolder("Subscribed"));
	private final TreeItem<RoomHolder> privateRooms = new TreeItem<>(new RoomHolder("Private"));
	private final TreeItem<RoomHolder> publicRooms = new TreeItem<>(new RoomHolder("Public"));

	private String nickname;

	private ChatRoomInfo selectedRoom;
	private ChatListView selectedChatListView;
	private Node roomInfoView;
	private ChatRoomInfoController chatRoomInfoController;

	private Instant lastTypingNotification = Instant.EPOCH;

	private double[] dividerPositions;

	private Timeline lastTypingTimeline;

	public ChatViewController(MessageClient messageClient, ChatClient chatClient, ProfileClient profileClient, LocationClient locationClient)
	{
		this.messageClient = messageClient;
		this.chatClient = chatClient;
		this.profileClient = profileClient;
		this.locationClient = locationClient;
	}

	public void initialize() throws IOException
	{
		profileClient.getOwn().doOnSuccess(profile -> nickname = profile.getName())
				.subscribe();

		var root = new TreeItem<>(new RoomHolder());
		//noinspection unchecked
		root.getChildren().addAll(subscribedRooms, privateRooms, publicRooms);
		root.setExpanded(true);
		roomTree.setRoot(root);
		roomTree.setShowRoot(false);
		roomTree.setCellFactory(ChatRoomCell::new);
		roomTree.addEventHandler(ChatRoomContextMenu.JOIN, event -> joinChatRoom(event.getTreeItem().getValue().getRoomInfo()));
		roomTree.addEventHandler(ChatRoomContextMenu.LEAVE, event -> {
			var roomInfo = event.getTreeItem().getValue().getRoomInfo();
			subscribedRooms.getChildren().stream()
					.filter(roomHolderTreeItem -> roomHolderTreeItem.getValue().getRoomInfo().equals(roomInfo))
					.findAny()
					.ifPresent(roomHolderTreeItem -> chatClient.leaveChatRoom(roomInfo.getId())
							.subscribe());
		});

		// We need Platform.runLater() because when an entry is moved, the selection can change
		roomTree.getSelectionModel().selectedItemProperty()
				.addListener((observable, oldValue, newValue) -> Platform.runLater(() -> changeSelectedRoom(newValue.getValue().getRoomInfo())));

		roomTree.setOnMouseClicked(event -> {
			if (event.getClickCount() == 2 && selectedRoom != null && selectedRoom.getId() != 0)
			{
				joinChatRoom(selectedRoom);
			}
		});

		send.setOnKeyPressed(event ->
		{
			if (selectedRoom != null && selectedRoom.getId() != 0)
			{
				if (event.getCode().equals(KeyCode.ENTER) && isNotBlank(send.getText()))
				{
					sendChatMessage(send.getText());
				}
				else
				{
					var now = Instant.now();
					if (Duration.between(lastTypingNotification, now).compareTo(TYPING_NOTIFICATION_DELAY) > 0)
					{
						var chatMessage = new ChatMessage();
						messageClient.sendToChatRoom(selectedRoom.getId(), chatMessage);
						lastTypingNotification = now;
					}
				}
			}
		});

		var loader = new FXMLLoader(getClass().getResource("/view/chat/chat_roominfo.fxml"));
		roomInfoView = loader.load();
		chatRoomInfoController = loader.getController();

		VBox.setVgrow(roomInfoView, Priority.ALWAYS);
		switchChatContent(roomInfoView, null);
		sendGroup.setVisible(false);
		setPreviewGroupVisibility(false);

		previewSend.setOnAction(event -> sendImage());
		previewCancel.setOnAction(event -> cancelImage());

		lastTypingTimeline = new Timeline(new KeyFrame(javafx.util.Duration.seconds(5),
				ae -> typingNotification.setText("")));

		send.addEventHandler(KeyEvent.KEY_PRESSED, this::handleInputKeys);
		send.setContextMenu(createChatInputContextMenu(send));

		getChatRoomContext();
	}

	private void joinChatRoom(ChatRoomInfo chatRoomInfo)
	{
		var found = subscribedRooms.getChildren().stream()
				.anyMatch(roomHolderTreeItem -> roomHolderTreeItem.getValue().getRoomInfo().equals(chatRoomInfo));

		if (!found)
		{
			chatClient.joinChatRoom(selectedRoom.getId())
					.subscribe(); // XXX: sometimes the id of the roomInfo is wrong... of course! because we set the context menu BEFORE the room id is refreshed into it
		}
	}

	private void getChatRoomContext()
	{
		chatClient.getChatRoomContext()
				.doOnSuccess(context -> {
					addRooms(context.getChatRoomLists());
					context.getChatRoomLists().getSubscribed().forEach(chatRoomInfo -> userJoined(chatRoomInfo.getId(), new ChatRoomUserEvent(context.getOwnUser().getGxsId(), context.getOwnUser().getNickname())));
				})
				.subscribe();
	}

	public void addRooms(ChatRoomLists chatRoomLists)
	{
		var subscribedTree = subscribedRooms.getChildren();
		var publicTree = publicRooms.getChildren();
		var privateTree = privateRooms.getChildren();

		chatRoomLists.getSubscribed().stream()
				.sorted(Comparator.comparing(ChatRoomInfo::getName))
				.forEach(roomInfo -> addOrUpdate(subscribedTree, roomInfo));

		// Make sure we don't add rooms that we're already subscribed to
		var unsubscribedRooms = chatRoomLists.getAvailable().stream()
				.filter(roomInfo -> !isInside(subscribedTree, roomInfo))
				.toList();

		unsubscribedRooms.stream()
				.filter(roomInfo -> roomInfo.getRoomType() == RoomType.PUBLIC)
				.sorted(Comparator.comparing(ChatRoomInfo::getName))
				.forEach(roomInfo -> addOrUpdate(publicTree, roomInfo));

		unsubscribedRooms.stream()
				.filter(roomInfo -> roomInfo.getRoomType() == RoomType.PRIVATE)
				.sorted(Comparator.comparing(ChatRoomInfo::getName))
				.forEach(roomInfo -> addOrUpdate(privateTree, roomInfo));
	}

	public void roomJoined(long roomId)
	{
		// Must be idempotent
		publicRooms.getChildren().stream()
				.filter(roomInfoTreeItem -> roomInfoTreeItem.getValue().getRoomInfo().getId() == roomId)
				.findFirst()
				.ifPresent(roomHolderTreeItem -> {
					publicRooms.getChildren().remove(roomHolderTreeItem);
					subscribedRooms.getChildren().add(roomHolderTreeItem);
				});
		// XXX: also do it for private
	}

	public void roomLeft(long roomId)
	{
		// Must be idempotent
		subscribedRooms.getChildren().stream()
				.filter(roomInfoTreeItem -> roomInfoTreeItem.getValue().getRoomInfo().getId() == roomId)
				.findFirst()
				.ifPresent(roomHolderTreeItem -> {
					subscribedRooms.getChildren().remove(roomHolderTreeItem);
					publicRooms.getChildren().add(roomHolderTreeItem); // XXX: could be public too...
					roomHolderTreeItem.getValue().clearChatListView();
				});
		// XXX: remove ourselves from the room
	}

	public void userJoined(long roomId, ChatRoomUserEvent event)
	{
		performOnChatListView(roomId, chatListView -> chatListView.addUser(event));
	}

	public void userLeft(long roomId, ChatRoomUserEvent event)
	{
		performOnChatListView(roomId, chatListView -> chatListView.removeUser(event));
	}

	public void userKeepAlive(long roomId, ChatRoomUserEvent event)
	{
		performOnChatListView(roomId, chatListView -> chatListView.addUser(event)); // XXX: use this to know if a user is "idle"
	}

	private void switchChatContent(Node contentNode, Node userListNode)
	{
		if (content.getChildren().size() > 1)
		{
			content.getChildren().remove(0);
		}
		content.getChildren().add(0, contentNode);

		if (userListNode == null)
		{
			if (!isEmpty(userListContent.getChildren()))
			{
				userListContent.getChildren().remove(0);
			}
			if (splitPane.getItems().contains(userListContent))
			{
				dividerPositions = splitPane.getDividerPositions();
				splitPane.getItems().remove(userListContent);
			}
		}
		else
		{
			if (!isEmpty(userListContent.getChildren()))
			{
				userListContent.getChildren().remove(0);
			}
			userListContent.getChildren().add(0, userListNode);

			if (!splitPane.getItems().contains(userListContent))
			{
				splitPane.getItems().add(userListContent);
				splitPane.setDividerPositions(dividerPositions);
			}
		}
		typingNotification.setText("");
	}

	// XXX: also we should merge/refresh... (ie. new rooms added, older rooms removed, etc...). merging properly is very difficult it seems
	// right now I use a simple implementation. It also has a drawback that it doesn't sort new entries and doesn't update the counter
	private void addOrUpdate(ObservableList<TreeItem<RoomHolder>> tree, ChatRoomInfo chatRoomInfo)
	{
		if (tree.stream()
				.map(TreeItem::getValue)
				.noneMatch(existingRoom -> existingRoom.getRoomInfo().equals(chatRoomInfo)))
		{
			tree.add(new TreeItem<>(new RoomHolder(chatRoomInfo)));
		}
	}

	private boolean isInside(ObservableList<TreeItem<RoomHolder>> tree, ChatRoomInfo chatRoomInfo)
	{
		return tree.stream()
				.map(TreeItem::getValue)
				.anyMatch(roomHolder -> roomHolder.getRoomInfo().equals(chatRoomInfo));
	}

	private void changeSelectedRoom(ChatRoomInfo chatRoomInfo)
	{
		selectedRoom = chatRoomInfo;

		var roomTreeItem = subscribedRooms.getChildren().stream()
				.filter(roomInfoTreeItem -> roomInfoTreeItem.getValue().getRoomInfo().equals(chatRoomInfo))
				.findFirst();

		if (roomTreeItem.isPresent())
		{
			var roomInfoTreeItem = roomTreeItem.get();
			var chatListView = getChatListViewOrCreate(roomInfoTreeItem);
			selectedChatListView = chatListView;
			switchChatContent(chatListView.getChatView(), chatListView.getUserListView());
			sendGroup.setVisible(true);
			send.requestFocus();
			selectedChatListView.jumpToBottom(true);
		}
		else
		{
			chatRoomInfoController.setRoomInfo(chatRoomInfo);
			switchChatContent(roomInfoView, null);
			sendGroup.setVisible(false);
			selectedChatListView = null;
		}
	}

	public void showMessage(ChatRoomMessage chatRoomMessage)
	{
		if (chatRoomMessage.isEmpty())
		{
			if (selectedRoom != null && chatRoomMessage.getRoomId() == selectedRoom.getId())
			{
				typingNotification.setText(chatRoomMessage.getSenderNickname() + " is typing...");
				lastTypingTimeline.playFromStart();
			}
		}
		else
		{
			performOnChatListView(chatRoomMessage.getRoomId(), chatListView -> chatListView.addUserMessage(chatRoomMessage.getSenderNickname(), chatRoomMessage.getContent()));
		}
	}

	private void performOnChatListView(long roomId, Consumer<ChatListView> action)
	{
		subscribedRooms.getChildren().stream()
				.map(this::getChatListViewOrCreate)
				.filter(chatListView -> chatListView.getId() == roomId)
				.findFirst()
				.ifPresent(action);
	}

	private ChatListView getChatListViewOrCreate(TreeItem<RoomHolder> roomInfoTreeItem)
	{
		var chatListView = roomInfoTreeItem.getValue().getChatListView();
		if (chatListView == null)
		{
			chatListView = new ChatListView(nickname, roomInfoTreeItem.getValue().getRoomInfo().getId());
			roomInfoTreeItem.getValue().setChatListView(chatListView);
		}
		return chatListView;
	}

	private final KeyCodeCombination TAB_KEY = new KeyCodeCombination(KeyCode.TAB);
	private final KeyCodeCombination PASTE_KEY = new KeyCodeCombination(KeyCode.V, KeyCombination.SHORTCUT_DOWN);
	private final KeyCodeCombination ENTER_KEY = new KeyCodeCombination(KeyCode.ENTER);
	private final KeyCodeCombination BACKSPACE_KEY = new KeyCodeCombination(KeyCode.BACK_SPACE);
	private int completionIndex;

	private void handleInputKeys(KeyEvent event)
	{
		if (TAB_KEY.match(event))
		{
			var line = send.getText();
			String suggestedNickname = null;
			if (line.length() == 0)
			{
				// empty line, insert the first nickname
				suggestedNickname = selectedChatListView.getUsername("", completionIndex);
			}
			else
			{
				if (send.getCaretPosition() <= IdentityConstants.NAME_LENGTH_MAX)
				{
					var separator = line.indexOf(":");
					if (separator == -1)
					{
						separator = line.length();
					}
					suggestedNickname = selectedChatListView.getUsername(line.substring(0, separator), completionIndex);
				}
			}
			if (suggestedNickname != null)
			{
				send.setText(suggestedNickname + ": ");
				send.positionCaret(suggestedNickname.length() + 2);
			}
			completionIndex++;
			event.consume(); // XXX: find a way to tab into another field (shift + tab currently does)
		}
		else if (PASTE_KEY.match(event))
		{
			var image = Clipboard.getSystemClipboard().getImage();
			if (image != null)
			{
				imagePreview.setImage(image);

				ImageUtils.limitMaximumImageSize(imagePreview, IMAGE_WIDTH_MAX, IMAGE_HEIGHT_MAX);

				setPreviewGroupVisibility(true);
				event.consume();
			}
		}
		else if (ENTER_KEY.match(event))
		{
			if (imagePreview.getImage() != null)
			{
				sendImage();
				event.consume();
			}
		}
		else if (BACKSPACE_KEY.match(event))
		{
			if (imagePreview.getImage() != null)
			{
				cancelImage();
				event.consume();
			}
		}
		else
		{
			completionIndex = 0;
		}
	}

	private void sendImage()
	{
		sendChatMessage("<img src=\"" + ImageUtils.writeImageAsJpegData(imagePreview.getImage(), MESSAGE_MAXIMUM_SIZE) + "\"/>");

		imagePreview.setImage(null);
		setPreviewGroupVisibility(false);

		// Reset the size so that smaller images aren't magnified
		imagePreview.setFitWidth(0);
		imagePreview.setFitHeight(0);
	}

	private void cancelImage()
	{
		imagePreview.setImage(null);
		setPreviewGroupVisibility(false);
	}

	private void sendChatMessage(String message)
	{
		var chatMessage = new ChatMessage(message);
		messageClient.sendToChatRoom(selectedRoom.getId(), chatMessage);
		selectedChatListView.addOwnMessage(message);
		send.clear();
	}

	private void setPreviewGroupVisibility(boolean visible)
	{
		previewGroup.setVisible(visible);
		previewGroup.setManaged(visible);
	}

	private ContextMenu createChatInputContextMenu(TextInputControl textInputControl)
	{
		var contextMenu = new ContextMenu();

		contextMenu.getItems().addAll(createDefaultChatInputMenuItems(textInputControl));
		var pasteId = new MenuItem("Paste own ID");
		pasteId.setOnAction(event -> appendOwnId(textInputControl));
		contextMenu.getItems().addAll(new SeparatorMenuItem(), pasteId);
		return contextMenu;
	}

	private void appendOwnId(TextInputControl textInputControl)
	{
		var rsIdResponse = locationClient.getRSId(OWN_LOCATION_ID, Type.CERTIFICATE);
		rsIdResponse.subscribe(reply -> Platform.runLater(() -> textInputControl.appendText(buildRetroshareUrl(reply))));
	}

	private String buildRetroshareUrl(RSIdResponse rsIdResponse)
	{
		var uri = URI.create("retroshare://certificate?" +
				"radix=" + URLEncoder.encode(rsIdResponse.rsId().replace("\n", ""), UTF_8) + // Removing the '\n' is in case this is a certificate which is sliced for presentation
				"&amp;name=" + URLEncoder.encode(rsIdResponse.name(), UTF_8) +
				"&amp;location=" + URLEncoder.encode(rsIdResponse.location(), UTF_8));
		return "<a href=\"" + uri + "\">" + AppName.NAME + " Certificate (" + rsIdResponse.name() + ", @" + rsIdResponse.location() + ")</a>";
	}

	private List<MenuItem> createDefaultChatInputMenuItems(TextInputControl textInputControl)
	{
		var undo = new MenuItem("Undo");
		undo.setOnAction(event -> textInputControl.undo());

		var redo = new MenuItem("Redo");
		redo.setOnAction(event -> textInputControl.redo());

		var cut = new MenuItem("Cut");
		cut.setOnAction(event -> textInputControl.cut());

		var copy = new MenuItem("Copy");
		copy.setOnAction(event -> textInputControl.copy());

		var paste = new MenuItem("Paste");
		paste.setOnAction(event -> textInputControl.paste());

		var delete = new MenuItem("Delete");
		delete.setOnAction(event -> textInputControl.deleteText(textInputControl.getSelection()));

		var selectAll = new MenuItem("Select All");
		selectAll.setOnAction(event -> textInputControl.selectAll());

		var emptySelection = Bindings.createBooleanBinding(() -> textInputControl.getSelection().getLength() == 0, textInputControl.selectionProperty());

		cut.disableProperty().bind(emptySelection);
		copy.disableProperty().bind(emptySelection);
		delete.disableProperty().bind(emptySelection);

		var canUndo = Bindings.createBooleanBinding(() -> !textInputControl.isUndoable(), textInputControl.undoableProperty());
		var canRedo = Bindings.createBooleanBinding(() -> !textInputControl.isRedoable(), textInputControl.redoableProperty());

		undo.disableProperty().bind(canUndo);
		redo.disableProperty().bind(canRedo);

		return List.of(undo, redo, cut, copy, paste, delete, new SeparatorMenuItem(), selectAll);
	}

}
