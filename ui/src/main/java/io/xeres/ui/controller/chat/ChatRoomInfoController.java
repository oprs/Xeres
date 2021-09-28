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

import io.xeres.common.id.Id;
import io.xeres.common.message.chat.RoomInfo;
import io.xeres.ui.controller.Controller;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class ChatRoomInfoController implements Controller
{
	@FXML
	private Label roomName;

	@FXML
	private Label roomId;

	@FXML
	private Label roomTopic;

	@FXML
	private Label roomType;

	@FXML
	private Label roomSecurity;

	@FXML
	private Label roomCount;

	@Override
	public void initialize()
	{
		// Nothing to do
	}

	public void setRoomInfo(RoomInfo roomInfo)
	{
		this.roomName.setText(roomInfo.getName());

		this.roomId.setText(roomInfo.getId() != 0L ? Id.toString(roomInfo.getId()) : "");

		this.roomTopic.setText(roomInfo.getTopic() != null ? roomInfo.getTopic() : "");

		this.roomType.setText(roomInfo.getRoomType() != null ? roomInfo.getRoomType().toString() : "");
		this.roomSecurity.setText(roomInfo.isSigned() ? "Signed IDs" : "All IDs");
		this.roomCount.setText(String.valueOf(roomInfo.getCount()));
	}
}
