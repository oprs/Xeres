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

package io.xeres.app.database.model.profile;

import io.xeres.app.database.converter.TrustConverter;
import io.xeres.app.database.model.location.Location;
import io.xeres.common.id.ProfileFingerprint;
import io.xeres.common.pgp.Trust;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.util.encoders.Hex;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static io.xeres.common.dto.profile.ProfileConstants.*;

@Entity
@XmlAccessorType(XmlAccessType.NONE)
public class Profile
{
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private long id;

	@NotNull
	@Size(min = NAME_LENGTH_MIN, max = NAME_LENGTH_MAX)
	private String name;

	@NotNull
	private long pgpIdentifier;

	private Instant created;

	@Embedded
	@NotNull
	@AttributeOverride(name = "identifier", column = @Column(name = "pgp_fingerprint"))
	private ProfileFingerprint profileFingerprint;

	private byte[] pgpPublicKeyData; // if null, this is not a valid profile yet

	private boolean accepted;

	@Convert(converter = TrustConverter.class)
	private Trust trust = Trust.UNKNOWN;

	@OneToMany(fetch = FetchType.LAZY, cascade = CascadeType.ALL, mappedBy = "profile", orphanRemoval = true)
	private final List<Location> locations = new ArrayList<>();

	protected Profile()
	{
	}

	// This is only used for unit tests
	protected Profile(long id, String name, long pgpIdentifier, Instant created, ProfileFingerprint profileFingerprint, byte[] pgpPublicKeyData)
	{
		this(name, pgpIdentifier, created, profileFingerprint, pgpPublicKeyData);
		this.id = id;
	}

	public static Profile createOwnProfile(String name, long pgpIdentifier, Instant created, ProfileFingerprint profileFingerprint, byte[] pgpPublicKeyData)
	{
		var profile = new Profile(name, pgpIdentifier, created, profileFingerprint, pgpPublicKeyData);
		profile.setTrust(Trust.ULTIMATE);
		profile.setAccepted(true);
		return profile;
	}

	public static Profile createProfile(String name, long pgpIdentifier, Instant created, ProfileFingerprint pgpFingerprint, PGPPublicKey pgpPublicKey)
	{
		try
		{
			return createProfile(name, pgpIdentifier, created, pgpFingerprint, pgpPublicKey.getEncoded());
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	public static Profile createProfile(String name, long pgpIdentifier, Instant created, ProfileFingerprint profileFingerprint, byte[] pgpPublicKeyData)
	{
		return new Profile(name, pgpIdentifier, created, profileFingerprint, pgpPublicKeyData);
	}

	public static Profile createEmptyProfile(String name, long pgpIdentifier, ProfileFingerprint profileFingerprint)
	{
		return new Profile(name, pgpIdentifier, null, profileFingerprint, null);
	}

	private Profile(String name, long pgpIdentifier, Instant created, ProfileFingerprint profileFingerprint, byte[] pgpPublicKeyData)
	{
		this.name = sanitizeProfileName(name);
		this.pgpIdentifier = pgpIdentifier;
		this.created = created;
		this.profileFingerprint = profileFingerprint;
		this.pgpPublicKeyData = pgpPublicKeyData;
	}

	private static String sanitizeProfileName(String profileName)
	{
		var index = profileName.indexOf(" (Generated by");
		if (index == -1)
		{
			index = profileName.indexOf(" (generated by"); // Workaround for some user who had this somehow
		}
		if (index > 0)
		{
			return profileName.substring(0, index);
		}
		return profileName;
	}

	public Profile updateWith(Profile other)
	{
		if (isPartial() && other.isComplete())
		{
			setPgpPublicKeyData(other.getPgpPublicKeyData()); // Promote to full profile
		}
		Location.addOrUpdateLocations(this, other.getLocations().stream().findFirst().orElseThrow(() -> new IllegalStateException("Missing location")));
		return this;
	}

	public void addLocation(Location location)
	{
		location.setProfile(this);
		getLocations().add(location);
	}

	public long getId()
	{
		return id;
	}

	void setId(long id)
	{
		this.id = id;
	}

	@XmlAttribute
	public String getName()
	{
		return name;
	}

	void setName(String name)
	{
		this.name = name;
	}

	@XmlAttribute
	public long getPgpIdentifier()
	{
		return pgpIdentifier;
	}

	void setPgpIdentifier(long pgpIdentifier)
	{
		this.pgpIdentifier = pgpIdentifier;
	}

	public Instant getCreated()
	{
		return created;
	}

	public void setCreated(Instant created)
	{
		this.created = created;
	}

	public ProfileFingerprint getProfileFingerprint()
	{
		return profileFingerprint;
	}

	public void setProfileFingerprint(ProfileFingerprint profileFingerprint)
	{
		this.profileFingerprint = profileFingerprint;
	}

	@XmlAttribute
	public byte[] getPgpPublicKeyData()
	{
		return pgpPublicKeyData;
	}

	public void setPgpPublicKeyData(byte[] pgpPublicKeyData)
	{
		this.pgpPublicKeyData = pgpPublicKeyData;
	}

	public boolean isAccepted()
	{
		return accepted;
	}

	public void setAccepted(boolean accepted)
	{
		this.accepted = accepted;
	}

	@XmlAttribute
	public Trust getTrust()
	{
		return trust;
	}

	public void setTrust(Trust trust)
	{
		this.trust = trust;
	}

	@XmlElement(name = "location")
	public List<Location> getLocations()
	{
		return locations;
	}

	public static boolean isOwn(long id)
	{
		return id == OWN_PROFILE_ID;
	}

	public boolean isOwn()
	{
		return id == OWN_PROFILE_ID;
	}

	public boolean isComplete()
	{
		return pgpPublicKeyData != null;
	}

	public boolean isPartial()
	{
		return pgpPublicKeyData == null;
	}

	public boolean isConnected()
	{
		return getLocations().stream().anyMatch(Location::isConnected);
	}

	@Override
	public String toString()
	{
		return "Profile{" +
				"id=" + id +
				", name='" + name + '\'' +
				", pgpIdentifier=" + io.xeres.common.id.Id.toString(pgpIdentifier) +
				", profileFingerprint=" + profileFingerprint +
				", pgpPublicKeyData=" + new String(Hex.encode(pgpPublicKeyData)) +
				", accepted=" + accepted +
				", trust=" + trust +
				", locations=" + locations +
				'}';
	}
}
