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

package io.xeres.app.service;

import io.xeres.app.crypto.pgp.PGP;
import io.xeres.app.crypto.rsid.RSId;
import io.xeres.app.database.model.location.Location;
import io.xeres.app.database.model.profile.Profile;
import io.xeres.app.database.repository.ProfileRepository;
import io.xeres.common.AppName;
import io.xeres.common.dto.profile.ProfileConstants;
import io.xeres.common.id.Id;
import io.xeres.common.id.LocationId;
import io.xeres.common.id.ProfileFingerprint;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.Security;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class ProfileService
{
	private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

	private static final int KEY_SIZE = 3072;
	private static final int KEY_ID_LENGTH_MIN = ProfileConstants.NAME_LENGTH_MIN;
	private static final int KEY_ID_LENGTH_MAX = ProfileConstants.NAME_LENGTH_MAX;
	private static final String KEY_ID_SUFFIX = "(Generated by " + AppName.NAME + ")";

	private final ProfileRepository profileRepository;
	private final PrefsService prefsService;

	public ProfileService(ProfileRepository profileRepository, PrefsService prefsService)
	{
		this.profileRepository = profileRepository;
		this.prefsService = prefsService;

		Security.addProvider(new BouncyCastleProvider());
	}

	@Transactional
	public boolean generateProfileKeys(String name)
	{
		if (prefsService.getSecretProfileKey() != null)
		{
			throw new IllegalStateException("Private profile key already exists");
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

			var ownProfile = Profile.createOwnProfile(name, pgpPublicKey.getKeyID(), new ProfileFingerprint(pgpPublicKey.getFingerprint()), pgpPublicKey.getEncoded());
			profileRepository.save(ownProfile);
			prefsService.saveSecretProfileKey(pgpSecretKey.getEncoded());
			return true;
		}
		catch (PGPException | IOException e)
		{
			log.error("Failed to generate PGP key pair", e);
		}
		return false;
	}

	public Profile getOwnProfile()
	{
		return profileRepository.findById(ProfileConstants.OWN_PROFILE_ID).orElseThrow(() -> new IllegalStateException("Missing own profile"));
	}

	public Optional<Profile> findProfileById(long id)
	{
		return profileRepository.findById(id);
	}

	public Optional<Profile> findProfileByName(String name)
	{
		return profileRepository.findByName(name);
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
	public Optional<Profile> createOrUpdateProfile(Profile profile)
	{
		Optional<Profile> savedProfile = findProfileByPgpFingerprint(profile.getProfileFingerprint());
		if (savedProfile.isPresent())
		{
			profile = savedProfile.get().updateWith(profile);
		}

		try
		{
			return Optional.of(profileRepository.save(profile));
		}
		catch (IllegalArgumentException e)
		{
			return Optional.empty();
		}
	}

	public Profile getProfileFromRSId(RSId rsId)
	{
		var profileFingerprint = new ProfileFingerprint(rsId.getPgpFingerprint());
		var profile = findProfileByPgpFingerprint(profileFingerprint).orElseGet(() -> Profile.createEmptyProfile(rsId.getName(), rsId.getPgpIdentifier(), profileFingerprint));
		profile.setAccepted(true);
		profile.addLocation(Location.createLocation(rsId));
		return profile;
	}

	@Transactional
	public void deleteProfile(long id)
	{
		var profile = profileRepository.findById(id).orElseThrow();
		profileRepository.delete(profile);
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
