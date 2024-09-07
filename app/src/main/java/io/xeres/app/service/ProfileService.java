/*
 * Copyright (c) 2019-2023 by David Gerber - https://zapek.com
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

package io.xeres.app.service;

import io.xeres.app.application.events.PeerDisconnectedEvent;
import io.xeres.app.crypto.pgp.PGP;
import io.xeres.app.crypto.rsid.RSId;
import io.xeres.app.database.model.location.Location;
import io.xeres.app.database.model.profile.Profile;
import io.xeres.app.database.repository.ProfileRepository;
import io.xeres.app.net.peer.PeerConnectionManager;
import io.xeres.common.AppName;
import io.xeres.common.dto.profile.ProfileConstants;
import io.xeres.common.id.Id;
import io.xeres.common.id.LocationId;
import io.xeres.common.id.ProfileFingerprint;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.Security;
import java.util.*;
import java.util.stream.Collectors;

import static io.xeres.app.service.ResourceCreationState.*;

@Service
public class ProfileService
{
	private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

	private static final int KEY_SIZE = 3072;
	private static final int KEY_ID_LENGTH_MIN = ProfileConstants.NAME_LENGTH_MIN;
	private static final int KEY_ID_LENGTH_MAX = ProfileConstants.NAME_LENGTH_MAX;
	private static final String KEY_ID_SUFFIX = "(Generated by " + AppName.NAME + ")";

	private final ProfileRepository profileRepository;
	private final SettingsService settingsService;
	private final PeerConnectionManager peerConnectionManager;

	private final Map<Profile, Set<LocationId>> profilesToDelete = HashMap.newHashMap(2);

	public ProfileService(ProfileRepository profileRepository, SettingsService settingsService, PeerConnectionManager peerConnectionManager)
	{
		this.profileRepository = profileRepository;
		this.settingsService = settingsService;
		this.peerConnectionManager = peerConnectionManager;

		Security.addProvider(new BouncyCastleProvider());
	}

	@Transactional
	public ResourceCreationState generateProfileKeys(String name)
	{
		if (hasOwnProfile())
		{
			return ALREADY_EXISTS;
		}

		if (name.length() < KEY_ID_LENGTH_MIN)
		{
			throw new IllegalArgumentException("Profile name is too short, minimum is " + KEY_ID_LENGTH_MIN);
		}

		if (name.length() > KEY_ID_LENGTH_MAX)
		{
			throw new IllegalArgumentException("Profile name is too long, maximum is " + KEY_ID_LENGTH_MAX);
		}

		log.info("Generating PGP keys, algorithm: RSA, bits: {} ...", KEY_SIZE);

		try
		{
			var pgpSecretKey = PGP.generateSecretKey(name, KEY_ID_SUFFIX, KEY_SIZE);
			var pgpPublicKey = pgpSecretKey.getPublicKey();

			log.info("Successfully generated PGP key pair, id: {}", Id.toString(pgpSecretKey.getKeyID()));

			createOwnProfile(name, pgpSecretKey, pgpPublicKey);
			return CREATED;
		}
		catch (PGPException | IOException e)
		{
			log.error("Failed to generate PGP key pair", e);
		}
		return FAILED;
	}

	@Transactional
	public void createOwnProfile(String name, PGPSecretKey pgpSecretKey, PGPPublicKey pgpPublicKey) throws IOException
	{
		var ownProfile = Profile.createOwnProfile(name, pgpPublicKey.getKeyID(), new ProfileFingerprint(pgpPublicKey.getFingerprint()), pgpPublicKey.getEncoded());
		profileRepository.save(ownProfile);
		settingsService.saveSecretProfileKey(pgpSecretKey.getEncoded());
	}

	public Profile getOwnProfile()
	{
		return profileRepository.findById(ProfileConstants.OWN_PROFILE_ID).orElseThrow(() -> new IllegalStateException("Missing own profile"));
	}

	public boolean hasOwnProfile()
	{
		return profileRepository.findById(ProfileConstants.OWN_PROFILE_ID).isPresent();
	}

	public Optional<Profile> findProfileById(long id)
	{
		return profileRepository.findById(id);
	}

	public List<Profile> findProfilesByName(String name)
	{
		return profileRepository.findAllByNameContaining(name);
	}

	public Optional<Profile> findProfileByPgpFingerprint(ProfileFingerprint profileFingerprint)
	{
		return profileRepository.findByProfileFingerprint(profileFingerprint);
	}

	public Optional<Profile> findProfileByPgpIdentifier(long pgpIdentifier)
	{
		return profileRepository.findByPgpIdentifier(pgpIdentifier);
	}

	public List<Profile> findAllCompleteProfilesByPgpIdentifiers(Set<Long> pgpIdentifiers)
	{
		return profileRepository.findAllCompleteByPgpIdentifiers(pgpIdentifiers);
	}

	public Optional<Profile> findDiscoverableProfileByPgpIdentifier(long pgpIdentifier)
	{
		return profileRepository.findDiscoverableProfileByPgpIdentifier(pgpIdentifier);
	}

	public List<Profile> findAllDiscoverableProfilesByPgpIdentifiers(Set<Long> pgpIdentifiers)
	{
		return profileRepository.findAllDiscoverableProfilesByPgpIdentifiers(pgpIdentifiers);
	}

	public Optional<Profile> findProfileByLocationId(LocationId locationId)
	{
		return profileRepository.findProfileByLocationId(locationId);
	}

	@Transactional
	public Optional<Profile> createOrUpdateProfile(final Profile profile)
	{
		Objects.requireNonNull(profile);

		return Optional.of(profileRepository.save(findProfileByPgpFingerprint(profile.getProfileFingerprint())
				.map(foundProfile -> foundProfile.updateWith(profile))
				.orElse(profile)));
	}

	public Profile getProfileFromRSId(RSId rsId)
	{
		var profile = findProfileByPgpFingerprint(rsId.getPgpFingerprint()).orElseGet(() -> createNewProfile(rsId));
		profile.setAccepted(true);
		profile.addLocation(Location.createLocation(rsId));
		return profile;
	}

	private static Profile createNewProfile(RSId rsId)
	{
		if (rsId.getPgpPublicKey().isPresent())
		{
			var pgpPublicKey = rsId.getPgpPublicKey().get();
			return Profile.createProfile(pgpPublicKey.getUserIDs().next(), pgpPublicKey.getKeyID(), rsId.getPgpFingerprint(), pgpPublicKey);
		}
		return Profile.createEmptyProfile(rsId.getName(), rsId.getPgpIdentifier(), rsId.getPgpFingerprint());
	}

	@Transactional
	public void deleteProfile(long id)
	{
		var profile = profileRepository.findById(id).orElseThrow();
		profile.setAccepted(false);
		var connectedLocations = profile.getLocations().stream()
				.filter(Location::isConnected)
				.toList();

		// If there's no connected locations, just delete the profile
		// and we're done. Otherwise, we need to disconnect the locations
		// and wait until that's done before deleting the profile.
		if (connectedLocations.isEmpty())
		{
			profileRepository.delete(profile);
		}
		else
		{
			profilesToDelete.put(profile, connectedLocations.stream().map(Location::getLocationId).collect(Collectors.toSet()));
			var ids = connectedLocations.stream().map(Location::getId).toList();
			ids.forEach(location -> {
				var peer = peerConnectionManager.getPeerByLocation(location);
				if (peer != null)
				{
					peer.getCtx().close();
				}
			});
		}
	}

	@EventListener
	public void onPeerDisconnectedEvent(PeerDisconnectedEvent event)
	{
		if (profilesToDelete.isEmpty())
		{
			return;
		}
		profilesToDelete.forEach((profile, locationIds) -> locationIds.removeIf(locationId -> locationId.equals(event.locationId())));
		var it = profilesToDelete.entrySet().iterator();
		while (it.hasNext())
		{
			var profileSetEntry = it.next();
			if (profileSetEntry.getValue().isEmpty())
			{
				profileRepository.delete(profileSetEntry.getKey());
				it.remove();
			}
		}
	}

	public List<Profile> getAllProfiles()
	{
		return profileRepository.findAll();
	}

	public List<Profile> getAllDiscoverableProfiles()
	{
		return profileRepository.getAllDiscoverableProfiles();
	}
}
