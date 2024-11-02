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

package io.xeres.app.api.controller.profile;

import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.xeres.app.api.exception.UnprocessableEntityException;
import io.xeres.app.crypto.rsid.RSId;
import io.xeres.app.database.model.profile.Profile;
import io.xeres.app.job.PeerConnectionJob;
import io.xeres.app.service.IdentityService;
import io.xeres.app.service.ProfileService;
import io.xeres.app.service.notification.status.StatusNotificationService;
import io.xeres.common.dto.profile.ProfileDTO;
import io.xeres.common.id.LocationId;
import io.xeres.common.pgp.Trust;
import io.xeres.common.rest.Error;
import io.xeres.common.rest.profile.ProfileKeyAttributes;
import io.xeres.common.rest.profile.RsIdRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Collections;
import java.util.List;

import static io.xeres.app.database.model.profile.ProfileMapper.*;
import static io.xeres.common.rest.PathConfig.PROFILES_PATH;
import static io.xeres.common.rsid.Type.ANY;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Tag(name = "Profile", description = "User's profiles", externalDocs = @ExternalDocumentation(url = "https://xeres.io/docs/api/profile", description = "Profile documentation"))
@RestController
@RequestMapping(value = PROFILES_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public class ProfileController
{
	private final ProfileService profileService;
	private final IdentityService identityService;

	private final PeerConnectionJob peerConnectionJob;
	private final StatusNotificationService statusNotificationService;

	public ProfileController(ProfileService profileService, IdentityService identityService, PeerConnectionJob peerConnectionJob, StatusNotificationService statusNotificationService)
	{
		this.profileService = profileService;
		this.identityService = identityService;
		this.peerConnectionJob = peerConnectionJob;
		this.statusNotificationService = statusNotificationService;
	}

	@GetMapping("/{id}")
	@Operation(summary = "Return a profile")
	@ApiResponse(responseCode = "200", description = "Profile found")
	@ApiResponse(responseCode = "404", description = "Profile not found", content = @Content(schema = @Schema(implementation = Error.class)))
	public ProfileDTO findProfileById(@PathVariable long id)
	{
		return toDeepDTO(profileService.findProfileById(id).orElseThrow());
	}

	@GetMapping("/{id}/key-attributes")
	@Operation(summary = "Return the profile's key attributes")
	@ApiResponse(responseCode = "200", description = "Profile found")
	@ApiResponse(responseCode = "400", description = "Error in the profile's key")
	@ApiResponse(responseCode = "404", description = "Profile not found", content = @Content(schema = @Schema(implementation = Error.class)))
	public ProfileKeyAttributes findProfileKeyAttributes(@PathVariable long id)
	{
		return profileService.findProfileKeyAttributes(id);
	}

	@GetMapping
	@Operation(summary = "Search all profiles", description = "If no search parameters are provided, return all profiles")
	@ApiResponse(responseCode = "200", description = "All matched profiles")
	public List<ProfileDTO> findProfiles(@RequestParam(value = "name", required = false) String name,
	                                     @RequestParam(value = "locationId", required = false) String locationId,
	                                     @RequestParam(value = "withLocations", required = false) Boolean withLocations)
	{
		if (isNotBlank(name))
		{
			return toDTOs(profileService.findProfilesByName(name));
		}
		else if (isNotBlank(locationId))
		{
			var locationIdentifier = new LocationId(locationId);
			var profile = profileService.findProfileByLocationId(locationIdentifier);
			return profile.map(p -> List.of(Boolean.TRUE.equals(withLocations) ? toDeepDTO(p, locationIdentifier) : toDTO(p))).orElse(Collections.emptyList());
		}
		return toDTOs(profileService.getAllProfiles());
	}

	@PostMapping
	@Operation(summary = "Create a profile and its possible location from an RS ID")
	@ApiResponse(responseCode = "201", description = "Profile created successfully", headers = @Header(name = "location", description = "the location of the profile"))
	@ApiResponse(responseCode = "422", description = "Profile entity cannot be processed", content = @Content(schema = @Schema(implementation = Error.class)))
	@ApiResponse(responseCode = "500", description = "Serious error", content = @Content(schema = @Schema(implementation = Error.class)))
	public ResponseEntity<Void> createProfileFromRsId(@Valid @RequestBody RsIdRequest rsIdRequest,
	                                                  @RequestParam(value = "connectionIndex", required = false) Integer connectionIndex,
	                                                  @RequestParam(value = "trust", required = false) Trust trust)
	{
		var profile = profileService.getProfileFromRSId(RSId.parse(rsIdRequest.rsId(), ANY).orElseThrow(() -> new UnprocessableEntityException("RS id is invalid")));
		var locationToConnectTo = profile.getLocations().stream().findFirst();

		if (trust != null)
		{
			if (trust == Trust.ULTIMATE)
			{
				throw new IllegalArgumentException("ULTIMATE trust cannot be set");
			}
			profile.setTrust(trust);
		}

		var savedProfile = profileService.createOrUpdateProfile(profile);

		statusNotificationService.incrementTotalUsers(); // not correct if a certificate is used to update an existing profile (XXX: put a created date? or age? that would detect it)

		locationToConnectTo.ifPresent(location ->
		{
			if (connectionIndex != null && connectionIndex >= 0)
			{
				peerConnectionJob.connectImmediately(location, connectionIndex);
			}
		});

		var profileLocation = ServletUriComponentsBuilder.fromCurrentRequest()
				.path("/{id}")
				.replaceQuery(null)
				.buildAndExpand(savedProfile.getId()).toUri();
		return ResponseEntity.created(profileLocation).build();
	}

	@PostMapping("/check")
	@Operation(summary = "Check an RS ID")
	@ApiResponse(responseCode = "200", description = "RS ID is OK")
	@ApiResponse(responseCode = "422", description = "RS ID cannot be processed", content = @Content(schema = @Schema(implementation = Error.class)))
	@ApiResponse(responseCode = "500", description = "Serious error", content = @Content(schema = @Schema(implementation = Error.class)))
	public ProfileDTO checkProfileFromRsId(@Valid @RequestBody RsIdRequest rsIdRequest)
	{
		var rsId = RSId.parse(rsIdRequest.rsId(), ANY).orElseThrow(() -> new UnprocessableEntityException("RS id is invalid"));
		return toDeepDTO(profileService.getProfileFromRSId(rsId));
	}

	@PutMapping("/{id}/trust")
	@Operation(summary = "Set the trust of a profile")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void setTrust(@PathVariable long id, @RequestBody Trust trust)
	{
		var profile = profileService.findProfileById(id).orElseThrow(() -> new UnprocessableEntityException("Profile not found"));
		if (profile.isOwn())
		{
			throw new IllegalArgumentException("Cannot change the trust of own profile");
		}
		if (trust == Trust.ULTIMATE)
		{
			throw new IllegalArgumentException("ULTIMATE trust cannot be set");
		}
		profile.setTrust(trust);

		profileService.createOrUpdateProfile(profile);
	}

	@DeleteMapping("/{id}")
	@Operation(summary = "Delete a profile")
	@ApiResponse(responseCode = "200", description = "Profile successfully deleted")
	@ApiResponse(responseCode = "404", description = "Profile not found", content = @Content(schema = @Schema(implementation = Error.class)))
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteProfile(@PathVariable long id)
	{
		if (Profile.isOwn(id))
		{
			throw new UnprocessableEntityException("The main profile cannot be deleted");
		}
		identityService.removeAllLinksToProfile(id);
		profileService.deleteProfile(id);

		statusNotificationService.decrementTotalUsers(); // XXX: wrong, a profile can have many locations
	}
}
