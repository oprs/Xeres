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

import org.junit.jupiter.api.Test;

import java.util.BitSet;
import java.util.Optional;
import java.util.Set;

import static io.xeres.app.xrs.service.filetransfer.FileTransferStrategy.LINEAR;
import static io.xeres.app.xrs.service.filetransfer.FileTransferStrategy.RANDOM;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ChunkDistributorTest
{
	@Test
	void ChunkDistributor_Linear_Given()
	{
		var chunkMap = new BitSet(4);
		var chunkDistributor = new ChunkDistributor(chunkMap, 4, LINEAR);

		assertEquals(0, chunkDistributor.getNextChunk().orElseThrow());
		assertEquals(1, chunkDistributor.getNextChunk().orElseThrow());
		assertEquals(2, chunkDistributor.getNextChunk().orElseThrow());
		assertEquals(3, chunkDistributor.getNextChunk().orElseThrow());
		assertEquals(Optional.empty(), chunkDistributor.getNextChunk());
	}

	@Test
	void ChunkDistributor_Linear_GivenAndUsed()
	{
		var chunkMap = new BitSet(4);
		var chunkDistributor = new ChunkDistributor(chunkMap, 4, LINEAR);

		assertEquals(0, chunkDistributor.getNextChunk().orElseThrow());
		chunkMap.set(0);
		assertEquals(1, chunkDistributor.getNextChunk().orElseThrow());
		chunkMap.set(1);
		chunkMap.set(2);
		assertEquals(3, chunkDistributor.getNextChunk().orElseThrow());
		chunkMap.set(3);
		assertEquals(Optional.empty(), chunkDistributor.getNextChunk());
	}

	@Test
	void ChunkDistributor_Random_Given()
	{
		var chunkMap = new BitSet(4);
		var chunkDistributor = new ChunkDistributor(chunkMap, 4, RANDOM);

		var chunk1 = chunkDistributor.getNextChunk().orElseThrow();
		var chunk2 = chunkDistributor.getNextChunk().orElseThrow();
		var chunk3 = chunkDistributor.getNextChunk().orElseThrow();
		var chunk4 = chunkDistributor.getNextChunk().orElseThrow();

		assertEquals(Optional.empty(), chunkDistributor.getNextChunk());
		var all = Set.of(chunk1, chunk2, chunk3, chunk4);
		assertEquals(4, all.size());
	}
}