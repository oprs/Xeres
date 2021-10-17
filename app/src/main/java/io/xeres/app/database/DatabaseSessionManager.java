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

package io.xeres.app.database;

import org.springframework.orm.jpa.EntityManagerFactoryUtils;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.persistence.EntityManagerFactory;
import javax.persistence.PersistenceUnit;

/**
 * Allows using @Transaction from outside Spring Boot threads. Prefer using {@link DatabaseSession} which implements
 * an AutoCloseable interface.
 */
@Component
public class DatabaseSessionManager
{
	@PersistenceUnit
	private EntityManagerFactory entityManagerFactory;

	public boolean bindSession()
	{
		if (!TransactionSynchronizationManager.hasResource(entityManagerFactory))
		{
			var entityManager = entityManagerFactory.createEntityManager();
			TransactionSynchronizationManager.bindResource(entityManagerFactory, new EntityManagerHolder(entityManager));
			return true;
		}
		return false;
	}

	public void unbindSession()
	{
		EntityManagerHolder emHolder = (EntityManagerHolder) TransactionSynchronizationManager.unbindResource(entityManagerFactory);
		EntityManagerFactoryUtils.closeEntityManager(emHolder.getEntityManager());
	}
}
