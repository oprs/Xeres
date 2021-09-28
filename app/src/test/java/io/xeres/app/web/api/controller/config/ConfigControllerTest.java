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

package io.xeres.app.web.api.controller.config;

import io.xeres.app.database.model.connection.Connection;
import io.xeres.app.database.model.identity.IdentityFakes;
import io.xeres.app.database.model.location.Location;
import io.xeres.app.net.protocol.PeerAddress;
import io.xeres.app.service.IdentityService;
import io.xeres.app.service.LocationService;
import io.xeres.app.service.ProfileService;
import io.xeres.app.web.api.controller.AbstractControllerTest;
import io.xeres.common.identity.Type;
import io.xeres.common.rest.config.IpAddressRequest;
import io.xeres.common.rest.config.OwnIdentityRequest;
import io.xeres.common.rest.config.OwnLocationRequest;
import io.xeres.common.rest.config.OwnProfileRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.security.cert.CertificateException;
import java.util.NoSuchElementException;
import java.util.Optional;

import static io.xeres.common.rest.PathConfig.*;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ConfigController.class)
class ConfigControllerTest extends AbstractControllerTest
{
	private static final String BASE_URL = CONFIG_PATH;

	@MockBean
	private ProfileService profileService;

	@MockBean
	private LocationService locationService;

	@MockBean
	private IdentityService identityService;

	@Autowired
	public MockMvc mvc;

	@Test
	void ConfigController_CreateProfile_OK() throws Exception
	{
		var profileRequest = new OwnProfileRequest("test node");

		when(profileService.generateProfileKeys(profileRequest.name())).thenReturn(true);

		mvc.perform(postJson(BASE_URL + "/profile", profileRequest))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "http://localhost" + PROFILES_PATH + "/" + 1L));

		verify(profileService).generateProfileKeys(profileRequest.name());
	}

	@Test
	void ConfigController_CreateProfile_Fail() throws Exception
	{
		var ownProfileRequest = new OwnProfileRequest("test node");

		when(profileService.generateProfileKeys(ownProfileRequest.name())).thenReturn(false);

		mvc.perform(postJson(BASE_URL + "/profile", ownProfileRequest))
				.andExpect(status().isInternalServerError());

		verify(profileService).generateProfileKeys(ownProfileRequest.name());
	}

	@Test
	void ConfigController_CreateProfile_NameTooLong() throws Exception
	{
		var ownProfileRequest = new OwnProfileRequest("This name is way too long and there's no chance it ever gets created as a node");

		mvc.perform(postJson(BASE_URL + "/profile", ownProfileRequest))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(profileService);
	}

	@Test
	void ConfigController_CreateProfile_NameTooShort() throws Exception
	{
		var ownProfileRequest = new OwnProfileRequest("");

		mvc.perform(postJson(BASE_URL + "/profile", ownProfileRequest))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(profileService);
	}

	@Test
	void ConfigController_CreateProfile_MissingName() throws Exception
	{
		var ownProfileRequest = new OwnProfileRequest(null);

		mvc.perform(postJson(BASE_URL + "/profile", ownProfileRequest))
				.andExpect(status().isBadRequest());

		verifyNoInteractions(profileService);
	}

	@Test
	void ConfigController_CreateLocation_OK() throws Exception
	{
		var ownLocationRequest = new OwnLocationRequest("test location");

		mvc.perform(postJson(BASE_URL + "/location", ownLocationRequest))
				.andExpect(status().isCreated());

		verify(locationService).createOwnLocation(anyString());
	}

	@Test
	void ConfigController_CreateLocation_Fail() throws Exception
	{
		doThrow(CertificateException.class).when(locationService).createOwnLocation(anyString());

		mvc.perform(post(BASE_URL + "/location")
				.accept(APPLICATION_JSON))
				.andExpect(status().isInternalServerError());
	}

	@Test
	void ConfigController_UpdateExternalIpAddress_Create_OK() throws Exception
	{
		String IP = "1.1.1.1";
		int PORT = 6667;

		when(locationService.findOwnLocation()).thenReturn(Optional.of(Location.createLocation("foo")));
		when(locationService.updateConnection(any(Location.class), any(PeerAddress.class))).thenReturn(LocationService.UpdateConnectionStatus.ADDED);

		var request = new IpAddressRequest(IP, PORT);

		mvc.perform(putJson(BASE_URL + "/externalIp", request))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "http://localhost" + CONFIG_PATH + "/externalIp"));

		verify(locationService).updateConnection(any(Location.class), any(PeerAddress.class));
	}

	@Test
	void ConfigController_UpdateExternalIpAddress_Update_OK() throws Exception
	{
		String IP = "1.1.1.1";
		int PORT = 6667;

		when(locationService.findOwnLocation()).thenReturn(Optional.of(Location.createLocation("foo")));
		when(locationService.updateConnection(any(Location.class), any(PeerAddress.class))).thenReturn(LocationService.UpdateConnectionStatus.UPDATED);

		var request = new IpAddressRequest(IP, PORT);


		mvc.perform(putJson(BASE_URL + "/externalIp", request))
				.andExpect(status().isNoContent());

		verify(locationService).updateConnection(any(Location.class), any(PeerAddress.class));
	}

	@Test
	void ConfigController_UpdateExternalIpAddress_Update_NoConnection_Fail() throws Exception
	{
		String IP = "1.1.1.1";
		int PORT = 6667;

		when(locationService.findOwnLocation()).thenReturn(Optional.of(Location.createLocation("foo")));
		when(locationService.updateConnection(any(Location.class), any(PeerAddress.class))).thenThrow(NoSuchElementException.class);

		var request = new IpAddressRequest(IP, PORT);

		mvc.perform(putJson(BASE_URL + "/externalIp", request))
				.andExpect(status().isNotFound());

		verify(locationService).updateConnection(any(Location.class), any(PeerAddress.class));
	}

	@Test
	void ConfigController_UpdateExternalIpAddress_Update_WrongIp_Fail() throws Exception
	{
		String IP = "1.1.1.1.1";
		int PORT = 6667;

		when(locationService.updateConnection(any(Location.class), any(PeerAddress.class))).thenThrow(NoSuchElementException.class);

		var request = new IpAddressRequest(IP, PORT);

		mvc.perform(putJson(BASE_URL + "/externalIp", request))
				.andExpect(status().isInternalServerError());
	}

	@Test
	void ConfigController_GetExternalIpAddress_OK() throws Exception
	{
		String IP = "1.1.1.1";
		int PORT = 6667;

		Location location = Location.createLocation("test");
		Connection connection = Connection.from(PeerAddress.from(IP, PORT));
		location.addConnection(connection);

		when(locationService.findOwnLocation()).thenReturn(Optional.of(location));

		mvc.perform(getJson(BASE_URL + "/externalIp"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ip", is(IP)))
				.andExpect(jsonPath("$.port", is(PORT)));
	}

	@Test
	void ConfigController_GetExternalIpAddress_NoLocationOrIpAddress_OK() throws Exception
	{
		when(locationService.findOwnLocation()).thenReturn(Optional.empty());

		mvc.perform(getJson(BASE_URL + "/externalIp"))
				.andExpect(status().isNotFound());
	}

	@Test
	void ConfigController_GetHostname_OK() throws Exception
	{
		String HOSTNAME = "foo.bar.com";

		when(locationService.getHostname()).thenReturn(HOSTNAME);

		mvc.perform(getJson(BASE_URL + "/hostname"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.hostname", is(HOSTNAME)));
	}

	@Test
	void ConfigController_GetUsername_OK() throws Exception
	{
		String USERNAME = "foobar";
		when(locationService.getUsername()).thenReturn(USERNAME);

		mvc.perform(getJson(BASE_URL + "/username"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.username", is(USERNAME)));
	}

	@Test
	void ConfigController_CreateIdentity_Anonymous_OK() throws Exception
	{
		var identity = IdentityFakes.createOwnIdentity("test", Type.ANONYMOUS);
		var identityRequest = new OwnIdentityRequest(identity.getGxsIdGroupItem().getName(), true);

		when(identityService.createOwnIdentity(identityRequest.name(), Type.ANONYMOUS)).thenReturn(identity.getId());

		mvc.perform(postJson(BASE_URL + "/identity", identityRequest))
				.andExpect(status().isCreated())
				.andExpect(header().string("Location", "http://localhost" + IDENTITY_PATH + "/" + identity.getId()));

		verify(identityService).createOwnIdentity(identityRequest.name(), Type.ANONYMOUS);
	}
}
