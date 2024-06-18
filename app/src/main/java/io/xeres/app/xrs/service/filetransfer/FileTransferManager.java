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

import io.xeres.app.database.model.location.Location;
import io.xeres.app.service.SettingsService;
import io.xeres.app.service.file.FileService;
import io.xeres.app.xrs.service.filetransfer.item.FileTransferChunkMapRequestItem;
import io.xeres.app.xrs.service.filetransfer.item.FileTransferDataItem;
import io.xeres.app.xrs.service.filetransfer.item.FileTransferDataRequestItem;
import io.xeres.app.xrs.service.filetransfer.item.FileTransferSingleChunkCrcRequestItem;
import io.xeres.common.id.Sha1Sum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

import static io.xeres.app.xrs.service.filetransfer.FileTransferRsService.CHUNK_SIZE;

/**
 * File transfer class.
 * <p>
 * <img src="doc-files/filetransfer.png" alt="File transfer diagram">
 */
class FileTransferManager implements Runnable
{
	private static final Logger log = LoggerFactory.getLogger(FileTransferManager.class);

	private final FileTransferRsService fileTransferRsService;
	private final SettingsService settingsService;
	private final Location ownLocation;
	private final BlockingQueue<FileTransferCommand> queue;

	private final Map<Sha1Sum, FileCreator> leechers = new HashMap<>();
	private final Map<Sha1Sum, FileProvider> seeders = new HashMap<>();

	public FileTransferManager(FileTransferRsService fileTransferRsService, SettingsService settingsService, Location ownLocation, BlockingQueue<FileTransferCommand> queue)
	{
		this.fileTransferRsService = fileTransferRsService;
		this.settingsService = settingsService;
		this.ownLocation = ownLocation;
		this.queue = queue;
	}

	@Override
	public void run()
	{
		var done = false;

		while (!done)
		{
			try
			{
				var command = queue.take();
				processCommand(command);
			}
			catch (InterruptedException e)
			{
				log.debug("FileTransferManager thread interrupted");
				done = true;
				Thread.currentThread().interrupt();
			}
		}
	}

	private void processCommand(FileTransferCommand command)
	{
		log.debug("Processing command {}...", command);

		if (command instanceof FileTransferCommandItem commandItem)
		{
			processItem(commandItem);
		}
		else if (command instanceof FileTransferCommandAction commandAction)
		{
			processAction(commandAction);
		}

	}

	private void processItem(FileTransferCommandItem commandItem)
	{
		if (commandItem.item() instanceof FileTransferDataRequestItem item)
		{
			handleReceiveDataRequest(commandItem.location(), item);
		}
		else if (commandItem.item() instanceof FileTransferDataItem item)
		{
			handleReceiveData(commandItem.location(), item);
		}
		else if (commandItem.item() instanceof FileTransferChunkMapRequestItem item)
		{
			if (item.isLeecher())
			{
				handleReceiveLeecherChunkMapRequest(commandItem.location(), item);
			}
			else
			{
				handleReceiveSeederChunkMapRequest(commandItem.location(), item);
			}
		}
		else if (commandItem.item() instanceof FileTransferSingleChunkCrcRequestItem item)
		{
			handleReceiveChunkCrcRequest(commandItem.location(), item);
		}
	}

	private void processAction(FileTransferCommandAction commandAction)
	{
		if (commandAction.action() instanceof ActionDownload actionDownload)
		{
			leechers.computeIfAbsent(actionDownload.hash(), hash -> {
				var file = Paths.get(settingsService.getIncomingDirectory(), hash + FileService.DOWNLOAD_EXTENSION).toFile(); // XXX: check if the path is OK!
				log.debug("Downloading file {}, size: {}", file, actionDownload.size());
				var fileCreator = new FileCreator(file, actionDownload.size());
				if (fileCreator.open())
				{
					return fileCreator;
				}
				else
				{
					log.error("Couldn't create downloaded file");
					return null;
				}
			});
		}
	}

	private void handleReceiveDataRequest(Location location, FileTransferDataRequestItem item)
	{
		FileProvider fileProvider = null;

		if (location.equals(ownLocation))
		{
			// Own requests must be passed to seeders
		}
		else
		{
			fileProvider = leechers.get(item.getFileItem().hash());
			if (fileProvider == null)
			{
				fileProvider = seeders.get(item.getFileItem().hash());
			}
			if (fileProvider != null)
			{
				handleSeederRequest(location, fileProvider, item.getFileItem().hash(), item.getFileItem().size(), item.getFileOffset(), item.getChunkSize());
				return;
			}
		}

		// Add to search queue
		// XXX: add, and also handle it
	}

	private void handleReceiveData(Location location, FileTransferDataItem item)
	{
		var fileCreator = leechers.get(item.getFileData().fileItem().hash());
		if (fileCreator == null)
		{
			log.error("No matching leecher for hash {}", item.getFileData().fileItem().hash());
			return;
		}

		try
		{
			fileCreator.write(location, item.getFileData().offset(), item.getFileData().data());
		}
		catch (IOException e)
		{
			log.error("Failed to write to file", e);
		}
	}

	private void handleReceiveLeecherChunkMapRequest(Location location, FileTransferChunkMapRequestItem item)
	{
		var fileCreator = leechers.get(item.getHash());
		if (fileCreator == null)
		{
			log.error("No matching leecher for hash {}", item.getHash());
			return;
		}
		var compressedChunkMap = fileCreator.getCompressedChunkMap();
		fileTransferRsService.sendChunkMap(location, item.getHash(), false, compressedChunkMap);
	}

	private void handleReceiveSeederChunkMapRequest(Location location, FileTransferChunkMapRequestItem item)
	{
		var fileProvider = seeders.get(item.getHash());
		if (fileProvider == null)
		{
			// XXX: do a search request, handleSearchRequest(location, hash)

			fileProvider = seeders.get(item.getHash());
		}

		if (fileProvider == null)
		{
			log.error("Search request succeeded but no seeder available");
			return;
		}

		var compressedChunkMap = fileProvider.getCompressedChunkMap();
		fileTransferRsService.sendChunkMap(location, item.getHash(), true, compressedChunkMap);
	}

	private void handleReceiveChunkCrcRequest(Location location, FileTransferSingleChunkCrcRequestItem item)
	{
		// XXX: not sure what to do yet, complicated
	}

	private void handleSeederRequest(Location location, FileProvider provider, Sha1Sum hash, long size, long offset, int chunkSize)
	{
		if (chunkSize > CHUNK_SIZE)
		{
			log.warn("Peer {} is requesting a large chunk ({}) for hash {}", location, chunkSize, hash);
			return;
		}

		try
		{
			var data = provider.read(location, offset, chunkSize);
			fileTransferRsService.sendData(location, hash, size, offset, data);
		}
		catch (IOException e)
		{
			log.error("Failed to read file", e);
		}
	}
}
