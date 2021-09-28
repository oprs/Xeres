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

package io.xeres.app.net.peer.ssl;

import io.netty.handler.ssl.SslContext;
import io.xeres.app.crypto.pgp.PGP;
import io.xeres.app.crypto.rsa.RSA;
import io.xeres.app.crypto.rsid.RSSerialVersion;
import io.xeres.app.crypto.x509.X509;
import io.xeres.app.database.model.location.Location;
import io.xeres.app.database.model.location.LocationFakes;
import io.xeres.app.database.model.profile.ProfileFakes;
import io.xeres.app.service.LocationService;
import io.xeres.common.id.LocationId;
import io.xeres.testutils.TestUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPSecretKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.util.Date;
import java.util.Optional;

import static io.xeres.app.net.peer.ConnectionDirection.INCOMING;
import static io.xeres.app.net.peer.ConnectionDirection.OUTGOING;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(SpringExtension.class)
class SSLTest
{
	private static PGPSecretKey pgpKey;
	private static KeyPair rsaKey;
	private static X509Certificate certificate;

	@Mock
	private LocationService locationService;

	@BeforeAll
	static void setup() throws PGPException, IOException, CertificateException
	{
		Security.addProvider(new BouncyCastleProvider());

		pgpKey = PGP.generateSecretKey("foo", "", 512);
		rsaKey = RSA.generateKeys(512);
		certificate = X509.generateCertificate(pgpKey, rsaKey.getPublic(), "CN=me", "CN=foobar", new Date(0), new Date(0), RSSerialVersion.V07_0001.serialNumber());
	}

	@Test
	void SSL_NoInstance_OK() throws NoSuchMethodException
	{
		TestUtils.assertUtilityClass(SSL.class);
	}

	@Test
	void SSL_CreateClientContext_OK() throws InvalidKeySpecException, NoSuchAlgorithmException, SSLException
	{
		SslContext sslContext = SSL.createSslContext(rsaKey.getPrivate().getEncoded(), certificate, OUTGOING);

		assertNotNull(sslContext);
		assertTrue(sslContext.isClient());
	}

	@Test
	void SSL_CreateServerContext_OK() throws InvalidKeySpecException, NoSuchAlgorithmException, SSLException
	{
		SslContext sslContext = SSL.createSslContext(rsaKey.getPrivate().getEncoded(), certificate, INCOMING);

		assertNotNull(sslContext);
		assertTrue(sslContext.isServer());
	}

	@Test
	void SSL_CheckPeerCertificate_OK() throws CertificateException, IOException
	{
		var profile = ProfileFakes.createProfile("foo", pgpKey.getKeyID(), pgpKey.getPublicKey().getFingerprint(), pgpKey.getPublicKey().getEncoded());
		var location = LocationFakes.createLocation("bar", profile);

		when(locationService.findLocationById(any(LocationId.class))).thenReturn(Optional.of(location));

		Location result = SSL.checkPeerCertificate(locationService, new X509Certificate[]{certificate});

		assertEquals(result, location);
		verify(locationService).findLocationById(any(LocationId.class));
	}

	@Test
	void SSL_CheckPeerCertificate_EmptyCertificate_Fail()
	{
		assertThatThrownBy(() -> SSL.checkPeerCertificate(locationService, new X509Certificate[]{}))
				.isInstanceOf(CertificateException.class)
				.hasMessage("Empty certificate");

		verify(locationService, times(0)).findLocationById(any(LocationId.class));
	}

	@Test
	void SSL_CheckPeerCertificate_AlreadyConnected_Fail() throws IOException
	{
		var profile = ProfileFakes.createProfile("foo", pgpKey.getKeyID(), pgpKey.getPublicKey().getFingerprint(), pgpKey.getPublicKey().getEncoded());
		var location = LocationFakes.createLocation("bar", profile);
		location.setConnected(true);

		when(locationService.findLocationById(any(LocationId.class))).thenReturn(Optional.of(location));

		assertThatThrownBy(() -> SSL.checkPeerCertificate(locationService, new X509Certificate[]{certificate}))
				.isInstanceOf(CertificateException.class)
				.hasMessage("Already connected");

		verify(locationService).findLocationById(any(LocationId.class));
	}

	@Test
	void SSL_CheckPeerCertificate_WrongCertificate_Fail() throws CertificateException, IOException, PGPException
	{
		PGPSecretKey wrongPgpKey = PGP.generateSecretKey("notFoo", "", 512);
		X509Certificate wrongCertificate = X509.generateCertificate(wrongPgpKey, rsaKey.getPublic(), "CN=me", "CN=foobar", new Date(0), new Date(0), RSSerialVersion.V07_0001.serialNumber());
		var profile = ProfileFakes.createProfile("foo", pgpKey.getKeyID(), pgpKey.getPublicKey().getFingerprint(), pgpKey.getPublicKey().getEncoded());
		var location = LocationFakes.createLocation("bar", profile);

		when(locationService.findLocationById(any(LocationId.class))).thenReturn(Optional.of(location));

		assertThatThrownBy(() -> SSL.checkPeerCertificate(locationService, new X509Certificate[]{wrongCertificate}))
				.isInstanceOf(CertificateException.class)
				.hasMessageContaining("Wrong signature");

		verify(locationService).findLocationById(any(LocationId.class));
	}
}
