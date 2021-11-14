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

package io.xeres.app.xrs.service.gxs;

import io.xeres.app.net.peer.PeerConnection;
import io.xeres.app.net.peer.PeerConnectionManager;
import io.xeres.app.xrs.service.gxs.Transaction.Type;
import io.xeres.app.xrs.service.gxs.item.*;
import io.xeres.common.id.LocationId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static io.xeres.app.xrs.service.gxs.Transaction.State;
import static io.xeres.app.xrs.service.gxs.item.TransactionFlags.*;

@Service
public class GxsTransactionManager
{
	private static final Logger log = LoggerFactory.getLogger(GxsTransactionManager.class);

	private final PeerConnectionManager peerConnectionManager;

	private final Map<LocationId, Map<Integer, Transaction<?>>> incomingTransactions = new ConcurrentHashMap<>();
	private final Map<LocationId, Map<Integer, Transaction<?>>> outgoingTransactions = new ConcurrentHashMap<>();

	public GxsTransactionManager(PeerConnectionManager peerConnectionManager)
	{
		this.peerConnectionManager = peerConnectionManager;
	}

	private void addIncomingTransaction(PeerConnection peerConnection, Transaction<?> transaction)
	{
		log.debug("Adding incoming transaction for {}", peerConnection);
		addTransaction(peerConnection, transaction, incomingTransactions);
	}

	private void addOutgoingTransaction(PeerConnection peerConnection, Transaction<?> transaction)
	{
		log.debug("Adding outgoing transaction for {}", peerConnection);
		addTransaction(peerConnection, transaction, outgoingTransactions);
	}

	private void addTransaction(PeerConnection peerConnection, Transaction<?> transaction, Map<LocationId, Map<Integer, Transaction<?>>> transactionList)
	{
		Map<Integer, Transaction<?>> transactionMap = transactionList.computeIfAbsent(peerConnection.getLocation().getLocationId(), key -> new HashMap<>());
		if (transactionMap.putIfAbsent(transaction.getId(), transaction) != null)
		{
			throw new IllegalStateException("Transaction " + transaction.getId() + " for peer " + peerConnection + " already exists. Should not happen (tm)");
		}
	}

	private Transaction<?> getTransaction(PeerConnection peerConnection, int id, Type type)
	{
		var locationId = peerConnection.getLocation().getLocationId();

		Map<Integer, Transaction<?>> transactionMap = type == Type.INCOMING ? incomingTransactions.get(locationId) : outgoingTransactions.get(locationId);
		if (transactionMap == null)
		{
			throw new IllegalStateException("No existing transaction for peer " + peerConnection);
		}
		Transaction<?> transaction = transactionMap.get(id);
		if (transaction == null)
		{
			throw new IllegalStateException("No existing transaction for peer " + peerConnection);
		}
		if (transaction.hasTimeout())
		{
			throw new IllegalStateException("Transaction timed out for peer " + peerConnection);
		}
		return transaction;
	}

	private void removeTransaction(PeerConnection peerConnection, Transaction<?> transaction)
	{
		var locationId = peerConnection.getLocation().getLocationId();

		Map<Integer, Transaction<?>> transactionMap = transaction.getType() == Type.INCOMING ? incomingTransactions.get(locationId) : outgoingTransactions.get(locationId);
		if (transactionMap == null)
		{
			throw new IllegalStateException("No existing transaction for removal for peer " + peerConnection);
		}
		if (!transactionMap.remove(transaction.getId(), transaction))
		{
			throw new IllegalStateException("No existing transaction for removal for peer " + peerConnection);
		}
		// XXX: remove, and possible check if the state is right before doing so (ie. COMPLETED, etc...)
	}

	public void startOutgoingTransactionForGroupIdResponse(PeerConnection peerConnection, List<GxsSyncGroupItem> items, Instant update, int transactionId, GxsService gxsService)
	{
		var transaction = new Transaction<>(transactionId, items, items.size(), gxsService, Type.OUTGOING);
		startOutgoingTransaction(peerConnection, transaction, EnumSet.of(BEGIN_INCOMING, TYPE_GROUP_LIST_RESPONSE), update);
	}

	public void startOutgoingTransactionForGroupTransfer(PeerConnection peerConnection, List<GxsTransferGroupItem> items, Instant update, int transactionId, GxsService gxsService)
	{
		var transaction = new Transaction<>(transactionId, items, items.size(), gxsService, Type.OUTGOING);
		startOutgoingTransaction(peerConnection, transaction, EnumSet.of(BEGIN_INCOMING, TYPE_GROUPS), update);
	}

	public void startOutgoingTransactionForGroupIdRequest(PeerConnection peerConnection, List<GxsSyncGroupItem> items, int transactionId, GxsService gxsService)
	{
		var transaction = new Transaction<>(transactionId, items, items.size(), gxsService, Type.OUTGOING);
		startOutgoingTransaction(peerConnection, transaction, EnumSet.of(BEGIN_INCOMING, TYPE_GROUP_LIST_REQUEST), Instant.EPOCH);
	}

	private void startOutgoingTransaction(PeerConnection peerConnection, Transaction<? extends GxsExchange> transaction, Set<TransactionFlags> flags, Instant update)
	{
		log.debug("Sending transaction (id: {})", transaction.getId());
		addOutgoingTransaction(peerConnection, transaction);

		var startTransactionItem = new GxsTransactionItem(
				flags,
				transaction.getItems().size(),
				(int) update.getEpochSecond(),
				transaction.getId());

		peerConnectionManager.writeItem(peerConnection, startTransactionItem, transaction.getService());

		// XXX: periodically check for the timeout in case the peer doesn't answer anymore
	}

	public void processIncomingTransaction(PeerConnection peerConnection, GxsTransactionItem item, GxsService gxsService)
	{
		log.debug("Processing transaction {}", item);
		if (item.getFlags().contains(BEGIN_INCOMING))
		{
			//  This is an incoming connection
			log.debug("Incoming transaction, sending back OUTGOING");
			var transaction = new Transaction<>(item.getTransactionId(), new ArrayList<>(), item.getItemCount(), gxsService, Type.INCOMING);
			addIncomingTransaction(peerConnection, transaction);

			Set<TransactionFlags> transactionFlags = EnumSet.copyOf(item.getFlags());
			transactionFlags.retainAll(TransactionFlags.ofTypes());
			transactionFlags.add(BEGIN_OUTGOING);

			var readyTransactionItem = new GxsTransactionItem(
					transactionFlags,
					item.getTransactionId()
			);
			peerConnectionManager.writeItem(peerConnection, readyTransactionItem, transaction.getService());
			transaction.setState(State.RECEIVING);
		}
		else if (item.getFlags().contains(BEGIN_OUTGOING))
		{
			// This is the confirmation by the peer of our outgoing connection
			log.debug("Outgoing transaction, sending items...");
			var transaction = getTransaction(peerConnection, item.getTransactionId(), Type.OUTGOING);
			transaction.setState(State.SENDING);

			log.debug("{} items to go", transaction.getItems().size());
			transaction.getItems().forEach(gxsExchange -> peerConnectionManager.writeItem(peerConnection, gxsExchange, transaction.getService()));
			log.debug("done");

			transaction.setState(State.WAITING_CONFIRMATION);
		}
		else if (item.getFlags().contains(END_SUCCESS))
		{
			// The peer confirms success
			log.debug("Got transaction success, removing the transaction");
			var transaction = getTransaction(peerConnection, item.getTransactionId(), Type.OUTGOING);
			transaction.setState(State.COMPLETED);
			removeTransaction(peerConnection, transaction);
		}
	}

	public boolean addIncomingItemToTransaction(PeerConnection peerConnection, GxsExchange item, GxsService gxsService)
	{
		log.debug("Adding transaction item: {}", item);
		var transaction = getTransaction(peerConnection, item.getTransactionId(), Type.INCOMING);
		transaction.addItem(item);

		if (transaction.hasAllItems())
		{
			log.debug("Transaction successful, sending COMPLETED");
			transaction.setState(State.COMPLETED);
			var successTransactionItem = new GxsTransactionItem(
					EnumSet.of(END_SUCCESS),
					transaction.getId()
			);
			peerConnectionManager.writeItem(peerConnection, successTransactionItem, transaction.getService());

			gxsService.processItems(peerConnection, transaction.getItems()); // XXX: how will processItems() know what the items are? should the transaction have something to know that?

			removeTransaction(peerConnection, transaction);
			// XXX: in the case that interest us, GxsIdService would call requestGxsGroups()

			return true;
		}
		return false;
	}
}
