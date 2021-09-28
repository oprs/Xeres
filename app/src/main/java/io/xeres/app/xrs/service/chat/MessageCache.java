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

package io.xeres.app.xrs.service.chat;

import io.xeres.app.crypto.chatcipher.ChatChallenge;
import io.xeres.common.id.LocationId;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class MessageCache
{
	private static final int CONNECTION_CHALLENGE_MAX_TIME = 30; // maximum age in seconds a message can be used in a connection challenge
	private static final int LIFETIME_MAX = 1200; // maximum age of a message in seconds

	private final Map<Long, Integer> messages = new ConcurrentHashMap<>();


	/**
	 * Check if a message has been recorded already. If yes, update
	 * its time to prevent echoes.
	 *
	 * @param id the id of the message to check
	 * @return true if it exists
	 */
	public boolean exists(long id)
	{
		return messages.replace(id, (int) Instant.now().getEpochSecond()) != null;
	}

	/**
	 * Add a message id to the cache.
	 *
	 * @param id the message id
	 */
	public void add(long id)
	{
		messages.put(id, (int) Instant.now().getEpochSecond());
	}

	/**
	 * Update the time of a message id.
	 *
	 * @param id the message id
	 */
	public void update(long id)
	{
		add(id);
	}

	/**
	 * Get a new unique message id
	 *
	 * @return the message id
	 */
	public long getNewMessageId()
	{
		long newId;

		do
		{
			newId = ThreadLocalRandom.current().nextLong();
		}
		while (messages.containsKey(newId));

		return newId;
	}

	/**
	 * Check if this message cache contains a challenge code.
	 *
	 * @param locationId    the location id of the peer
	 * @param chatRoomId    the chat room id
	 * @param challengeCode the challenge code to be matched against
	 * @return true if challengeCode is in one of a suitable message
	 */
	public boolean hasConnectionChallenge(LocationId locationId, long chatRoomId, long challengeCode)
	{
		int now = (int) Instant.now().getEpochSecond();

		for (Map.Entry<Long, Integer> message : messages.entrySet())
		{
			if (message.getValue() + CONNECTION_CHALLENGE_MAX_TIME + 5 > now && challengeCode == ChatChallenge.code(locationId, chatRoomId, message.getKey()))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Return a recent message id.
	 *
	 * @return the message id of a recent message. If there's nothing suitable, return 0
	 */
	public long getRecentMessage()
	{
		int now = (int) Instant.now().getEpochSecond();

		for (Map.Entry<Long, Integer> message : messages.entrySet())
		{
			if (message.getValue() + CONNECTION_CHALLENGE_MAX_TIME > now)
			{
				return message.getKey();
			}
		}
		return 0L;
	}

	/**
	 * Remove all messages older than LIFETIME_MAX seconds.
	 */
	public void purge()
	{
		int now = (int) Instant.now().getEpochSecond();
		messages.entrySet().removeIf(entry -> entry.getValue() + LIFETIME_MAX < now);
	}
}
