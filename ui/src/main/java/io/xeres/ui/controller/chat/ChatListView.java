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

import io.xeres.common.id.GxsId;
import io.xeres.common.message.chat.ChatRoomInfo;
import io.xeres.common.message.chat.ChatRoomUserEvent;
import io.xeres.ui.custom.ChatListCell;
import io.xeres.ui.support.chat.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.ListView;
import javafx.scene.image.Image;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.fxmisc.flowless.VirtualFlow;
import org.fxmisc.flowless.VirtualizedScrollPane;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static io.xeres.ui.support.chat.ChatAction.Type.*;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

public class ChatListView
{
	private static final Logger log = LoggerFactory.getLogger(ChatListView.class);

	private final ObservableList<ChatLine> messages = FXCollections.observableArrayList();
	private final Map<GxsId, ChatRoomUser> userMap = new HashMap<>();
	private final ObservableList<ChatRoomUser> users = FXCollections.observableArrayList();

	private String nickname;
	private final ChatRoomInfo chatRoomInfo;

	private final VirtualizedScrollPane<VirtualFlow<ChatLine, ChatListCell>> chatView;
	private final ListView<ChatRoomUser> userListView;

	public ChatListView(String nickname, ChatRoomInfo chatRoomInfo)
	{
		this.nickname = nickname;
		this.chatRoomInfo = chatRoomInfo;

		chatView = createChatView();
		userListView = createUserListView();
	}

	private VirtualizedScrollPane<VirtualFlow<ChatLine, ChatListCell>> createChatView()
	{
		final VirtualFlow<ChatLine, ChatListCell> view = VirtualFlow.createVertical(messages, ChatListCell::new, VirtualFlow.Gravity.REAR);
		view.setFocusTraversable(false);
		view.getStyleClass().add("chat-list");
		return new VirtualizedScrollPane<>(view);
	}

	private ListView<ChatRoomUser> createUserListView()
	{
		final ListView<ChatRoomUser> view;
		view = new ListView<>();
		view.getStyleClass().add("chat-user-list");
		VBox.setVgrow(view, Priority.ALWAYS);

		view.setCellFactory(ChatUserCell::new);
		view.setItems(users);
		return view;
	}

	public void addMessage(String message)
	{
		addMessage(nickname, message); // XXX: this will decode images twice but well...
	}

	public void addMessage(String from, String message)
	{
		var chatAction = new ChatAction(SAY, from, null);

		var img = Jsoup.parse(message).selectFirst("img");

		if (img != null)
		{
			var data = img.absUrl("src");
			if (isNotEmpty(data))
			{
				var image = new Image(data);
				if (!image.isError())
				{
					addMessageLine(chatAction, image);
				}
			}
		}
		else
		{
			var chatContents = ChatParser.parse(message);
			var chatLine = new ChatLine(Instant.now(), chatAction, chatContents.toArray(ChatContent[]::new));
			addMessageLine(chatLine);
		}
	}

	public void addUser(ChatRoomUserEvent user)
	{
		if (!userMap.containsKey(user.getGxsId()))
		{
			var chatRoomUser = new ChatRoomUser(user.getGxsId(), user.getNickname());
			users.add(chatRoomUser);
			userMap.put(user.getGxsId(), chatRoomUser);
			users.sort((o1, o2) -> o1.nickname().compareToIgnoreCase(o2.nickname()));
			if (!nickname.equals(user.getNickname()))
			{
				addMessageLine(new ChatAction(JOIN, user.getNickname(), user.getGxsId()));
			}
		}
	}

	public void removeUser(ChatRoomUserEvent user)
	{
		var chatRoomUser = userMap.remove(user.getGxsId());

		if (chatRoomUser != null)
		{
			users.remove(chatRoomUser);
			addMessageLine(new ChatAction(LEAVE, user.getNickname(), user.getGxsId()));
		}
	}

	public String getUsername(String prefix, int index)
	{
		var prefixLower = prefix.toLowerCase(Locale.ENGLISH);
		if (isEmpty(prefix))
		{
			return users.get(index % users.size()).nickname();
		}
		else
		{
			var matchingUsers = users.stream()
					.filter(chatRoomUser -> !chatRoomUser.nickname().equals(nickname) && chatRoomUser.nickname().toLowerCase(Locale.ENGLISH).startsWith(prefixLower))
					.toList();

			if (matchingUsers.isEmpty())
			{
				return null;
			}
			return matchingUsers.get(index % matchingUsers.size()).nickname();
		}
	}

	public void setNickname(String nickname)
	{
		this.nickname = nickname;
	}

	public Node getChatView()
	{
		// We use an anchor to force the VirtualFlow to be bigger
		// than its default size of 100 x 100. It doesn't behave
		// well in a VBox only.
		var anchor = new AnchorPane(chatView);
		AnchorPane.setTopAnchor(chatView, 0.0);
		AnchorPane.setLeftAnchor(chatView, 0.0);
		AnchorPane.setRightAnchor(chatView, 0.0);
		AnchorPane.setBottomAnchor(chatView, 0.0);
		VBox.setVgrow(anchor, Priority.ALWAYS);
		return anchor;
	}

	public Node getUserListView()
	{
		return userListView;
	}

	public ChatRoomInfo getRoomInfo()
	{
		return chatRoomInfo;
	}

	private void addMessageLine(ChatLine line)
	{
		messages.add(line);
		var lastIndex = messages.size() - 1;
		if (chatView.getContent().getLastVisibleIndex() == lastIndex - 1) // XXX: why -1?!
		{
			chatView.getContent().showAsFirst(lastIndex);
		}
	}

	private void addMessageLine(ChatAction action, Image image)
	{
		var chatLine = new ChatLine(Instant.now(), action, new ChatContentImage(image));
		addMessageLine(chatLine);
	}

	private void addMessageLine(ChatAction action)
	{
		var chatLine = new ChatLine(Instant.now(), action);
		addMessageLine(chatLine);
	}

	private void addMessageLine(ChatAction action, String message)
	{
		var chatLine = new ChatLine(Instant.now(), action, new ChatContentText(message));
		addMessageLine(chatLine);
	}
}
