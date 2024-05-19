/*
 * Copyright (c) 2024 by David Gerber - https://zapek.com
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

package io.xeres.app.xrs.service.filetransfer;

import io.xeres.app.crypto.hash.sha1.Sha1MessageDigest;
import io.xeres.app.crypto.rscrypto.RsCrypto;
import io.xeres.app.database.model.location.Location;
import io.xeres.app.net.peer.PeerConnection;
import io.xeres.app.net.peer.PeerConnectionManager;
import io.xeres.app.properties.NetworkProperties;
import io.xeres.app.service.file.FileService;
import io.xeres.app.service.notification.file.FileSearchNotificationService;
import io.xeres.app.xrs.item.Item;
import io.xeres.app.xrs.item.ItemUtils;
import io.xeres.app.xrs.service.RsService;
import io.xeres.app.xrs.service.RsServiceRegistry;
import io.xeres.app.xrs.service.RsServiceType;
import io.xeres.app.xrs.service.filetransfer.item.*;
import io.xeres.app.xrs.service.turtle.TurtleRouter;
import io.xeres.app.xrs.service.turtle.TurtleRsClient;
import io.xeres.app.xrs.service.turtle.item.*;
import io.xeres.common.id.LocationId;
import io.xeres.common.id.Sha1Sum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static io.xeres.app.xrs.service.RsServiceType.FILE_TRANSFER;
import static io.xeres.app.xrs.service.RsServiceType.TURTLE;

@Component
public class FileTransferRsService extends RsService implements TurtleRsClient
{
	private static final Logger log = LoggerFactory.getLogger(FileTransferRsService.class);
	private TurtleRouter turtleRouter;

	private final FileService fileService;
	private final PeerConnectionManager peerConnectionManager;
	private final FileSearchNotificationService fileSearchNotificationService;
	private final RsServiceRegistry rsServiceRegistry;
	private final RsCrypto.EncryptionFormat encryptionFormat;

	private final Map<Sha1Sum, Sha1Sum> encryptedHashes = new ConcurrentHashMap<>();

	public FileTransferRsService(RsServiceRegistry rsServiceRegistry, FileService fileService, PeerConnectionManager peerConnectionManager, FileSearchNotificationService fileSearchNotificationService, NetworkProperties networkProperties)
	{
		super(rsServiceRegistry);
		this.fileService = fileService;
		this.peerConnectionManager = peerConnectionManager;
		this.fileSearchNotificationService = fileSearchNotificationService;
		this.rsServiceRegistry = rsServiceRegistry;
		encryptionFormat = getEncryptionFormat(networkProperties);
	}

	private static RsCrypto.EncryptionFormat getEncryptionFormat(NetworkProperties networkProperties)
	{
		final RsCrypto.EncryptionFormat encryptionFormat;
		if (networkProperties.getTunnelEncryption().equals("chacha20-sha256"))
		{
			encryptionFormat = RsCrypto.EncryptionFormat.CHACHA20_SHA256;
		}
		else if (networkProperties.getTunnelEncryption().equals("chacha20-poly1305"))
		{
			encryptionFormat = RsCrypto.EncryptionFormat.CHACHA20_POLY1305;
		}
		else
		{
			throw new IllegalArgumentException("Unsupported encryption format: " + networkProperties.getTunnelEncryption());
		}
		return encryptionFormat;
	}

	@Override
	public RsServiceType getServiceType()
	{
		return FILE_TRANSFER;
	}

	@Override
	public RsServiceType getMasterServiceType()
	{
		return TURTLE;
	}

	@Override
	public void initializeTurtle(TurtleRouter turtleRouter)
	{
		this.turtleRouter = turtleRouter;
	}

	@Override
	public void handleItem(PeerConnection sender, Item item)
	{
		// XXX
	}

	@Override
	public boolean handleTunnelRequest(PeerConnection sender, Sha1Sum hash)
	{
		var file = fileService.findFile(hash);
		if (file.isPresent())
		{
			log.debug("Found file {}", file.get());
			// XXX: don't forget to handle encrypted hashes, files currently being swarmed and tons of other things
			// XXX: sender might not necessarily be needed (it's for the permissions)
			return true;
		}
		return false;
	}

	@Override
	public void receiveTurtleData(TurtleGenericTunnelItem item, Sha1Sum hashOfHash, LocationId virtualLocationId, TunnelDirection tunnelDirection)
	{
		if (item instanceof TurtleGenericDataItem turtleGenericDataItem)
		{
			var hash = findRealHash(hashOfHash);
			if (hash == null)
			{
				log.error("Cannot find the real hash of hash {}", hashOfHash);
				return;
			}

			var decryptedItem = decryptItem(turtleGenericDataItem, hash);
			receiveTurtleData(decryptedItem, hash, virtualLocationId, tunnelDirection);
			decryptedItem.dispose();
			return;
		}
		else if (item instanceof TurtleFileRequestItem turtleFileRequestItem)
		{
			// XXX: implement
		}
		else if (item instanceof TurtleFileDataItem turtleFileDataItem)
		{
			// XXX: implement
		}
		else if (item instanceof TurtleFileMapItem turtleFileMapItem)
		{
			// XXX: implement
		}
		else if (item instanceof TurtleFileMapRequestItem turtleFileMapRequestItem)
		{
			// XXX: implement
		}
		else if (item instanceof TurtleChunkCrcItem turtleChunkCrcItem)
		{
			// XXX: implement
		}
		else if (item instanceof TurtleChunkCrcRequestItem turtleChunkCrcRequestItem)
		{
			// XXX: implement
		}
		log.warn("Unknown packet type received: {}", item.getSubType());
	}

	@Override
	public List<byte[]> receiveSearchRequest(byte[] query, int maxHits)
	{
		return List.of();
	}

	@Override
	public void receiveSearchResult(int requestId, TurtleSearchResultItem item)
	{
		if (item instanceof TurtleFileSearchResultItem fileItem)
		{
			log.debug("Forwarding search result id {} as notification", requestId);
			fileItem.getResults().forEach(fileInfo -> fileSearchNotificationService.foundFile(requestId, fileInfo.getFileName(), fileInfo.getFileSize(), fileInfo.getFileHash()));
		}
	}

	@Override
	public void addVirtualPeer(Sha1Sum hash, LocationId virtualLocationId, TunnelDirection direction)
	{

	}

	@Override
	public void removeVirtualPeer(Sha1Sum hash, LocationId virtualLocationId)
	{

	}

	public int turtleSearch(String search) // XXX: maybe make a generic version or so...
	{
		if (turtleRouter != null) // Happens if the service is not enabled
		{
			return turtleRouter.turtleSearch(search, this);
		}
		return 0;
	}

	@Override
	public void shutdown()
	{
		fileSearchNotificationService.shutdown();
	}

	private void sendTurtleItem(LocationId virtualLocationId, Sha1Sum hash, TurtleGenericTunnelItem item)
	{
		// We only send encrypted tunnels. They're available since Retroshare 0.6.2
		turtleRouter.sendTurtleData(virtualLocationId, encryptItem(item, hash));
	}

	private TurtleGenericDataItem encryptItem(TurtleGenericTunnelItem item, Sha1Sum hash)
	{
		var key = new FileTransferEncryptionKey(hash);
		var serializedItem = ItemUtils.serializeItem(item, this);
		return new TurtleGenericDataItem(RsCrypto.encryptAuthenticateData(key, serializedItem, encryptionFormat));
	}

	private TurtleGenericTunnelItem decryptItem(TurtleGenericDataItem item, Sha1Sum hash)
	{
		var key = new FileTransferEncryptionKey(hash);
		return (TurtleGenericTunnelItem) ItemUtils.deserializeItem(RsCrypto.decryptAuthenticateData(key, item.getTunnelData()), rsServiceRegistry);
	}

	private void activateTunnels(Sha1Sum hash)
	{
		var hashOfHash = encryptHash(hash);
		encryptedHashes.put(hashOfHash, hash);

		turtleRouter.startMonitoringTunnels(hashOfHash, this, true);
	}

	private void deactivateTunnels(Sha1Sum hash)
	{
		var hashOfHash = encryptHash(hash);
		encryptedHashes.put(hashOfHash, hash);

		turtleRouter.stopMonitoringTunnels(hashOfHash);
	}

	private Sha1Sum encryptHash(Sha1Sum hash)
	{
		var digest = new Sha1MessageDigest();
		digest.update(hash.getBytes());
		return digest.getSum();
	}

	private Sha1Sum findRealHash(Sha1Sum hashOfHash)
	{
		return encryptedHashes.get(hashOfHash);
	}

	private void sendDataRequest(Location location, Sha1Sum hash, long size, long offset, int chunkSize)
	{
		if (turtleRouter.isVirtualPeer(location.getLocationId()))
		{
			var item = new TurtleFileRequestItem(offset, chunkSize);

			sendTurtleItem(location.getLocationId(), hash, item);
		}
		else
		{
			var item = new FileTransferDataRequestItem(size, hash, offset, chunkSize);
			peerConnectionManager.writeItem(location, item, this);
		}
	}

	private void sendChunkMapRequest(Location location, Sha1Sum hash, boolean isClient)
	{
		if (turtleRouter.isVirtualPeer(location.getLocationId()))
		{
			var item = new TurtleFileMapRequestItem();
			sendTurtleItem(location.getLocationId(), hash, item);
		}
		else
		{
			var item = new FileTransferChunkMapRequestItem(hash, isClient);
			peerConnectionManager.writeItem(location, item, this);
		}
	}

	private void sendChunkMap(Location location, Sha1Sum hash, boolean isClient, List<Integer> compressedChunkMap)
	{
		if (turtleRouter.isVirtualPeer(location.getLocationId()))
		{
			var item = new TurtleFileMapItem(compressedChunkMap);
			sendTurtleItem(location.getLocationId(), hash, item);
		}
		else
		{
			var item = new FileTransferChunkMapItem(hash, compressedChunkMap, isClient);
			peerConnectionManager.writeItem(location, item, this);
		}
	}

	private void sendSingleChunkCrcRequest(Location location, Sha1Sum hash, int chunkNumber)
	{
		if (turtleRouter.isVirtualPeer(location.getLocationId()))
		{
			var item = new TurtleChunkCrcRequestItem(chunkNumber);
			sendTurtleItem(location.getLocationId(), hash, item);
		}
		else
		{
			var item = new FileTransferSingleChunkCrcRequestItem(hash, chunkNumber);
			peerConnectionManager.writeItem(location, item, this);
		}
	}

	private void sendSingleChunkCrc(Location location, Sha1Sum hash, int chunkNumber, Sha1Sum checkSum)
	{
		if (turtleRouter.isVirtualPeer(location.getLocationId()))
		{
			var item = new TurtleChunkCrcItem(chunkNumber, checkSum);
			sendTurtleItem(location.getLocationId(), hash, item);
		}
		else
		{
			var item = new FileTransferSingleChunkCrcItem(hash, chunkNumber, checkSum);
			peerConnectionManager.writeItem(location, item, this);
		}
	}
}
