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

package io.xeres.ui.support.chat;

import io.xeres.ui.JavaFxApplication;
import javafx.scene.Node;
import javafx.scene.control.Hyperlink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public class ChatContentURI implements ChatContent
{
	private static final Logger log = LoggerFactory.getLogger(ChatContentURI.class);

	private final Hyperlink node;

	public ChatContentURI(URI uri)
	{
		node = new Hyperlink(uri.toString());
		node.setOnAction(event -> JavaFxApplication.openUrl(node.getText()));
	}

	public ChatContentURI(URI uri, String description)
	{
		node = new Hyperlink(description);
		node.setOnAction(event -> log.info("Would add certificate for {}", uri.toString())); // XXX: call AddCertificateWindowController, just need a way to give it arguments!
	}

	@Override
	public Node getNode()
	{
		return node;
	}

	public String getUri()
	{
		return node.getText();
	}
}
