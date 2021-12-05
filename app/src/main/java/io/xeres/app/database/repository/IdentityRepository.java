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

package io.xeres.app.database.repository;

import io.xeres.app.database.model.identity.Identity;
import io.xeres.common.id.GxsId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IdentityRepository extends JpaRepository<Identity, Long>
{
	@Query("SELECT i FROM Identity i, IN(i.gxsIdGroupItem) g WHERE g.name = :name")
	List<Identity> findAllByName(String name);

	@Query("SELECT i FROM Identity i, IN(i.gxsIdGroupItem) g WHERE g.gxsId = :gxsId")
	Optional<Identity> findByGxsId(GxsId gxsId);
}
