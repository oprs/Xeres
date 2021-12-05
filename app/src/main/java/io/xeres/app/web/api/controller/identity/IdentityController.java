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

package io.xeres.app.web.api.controller.identity;

import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.xeres.app.database.model.identity.Identity;
import io.xeres.app.database.model.identity.IdentityMapper;
import io.xeres.app.service.IdentityService;
import io.xeres.app.web.api.error.Error;
import io.xeres.common.dto.identity.IdentityDTO;
import io.xeres.common.id.GxsId;
import io.xeres.common.id.Id;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static io.xeres.app.database.model.identity.IdentityMapper.toDTO;
import static io.xeres.app.database.model.identity.IdentityMapper.toGxsIdDTOs;
import static io.xeres.common.rest.PathConfig.IDENTITY_PATH;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

@Tag(name = "Identity", description = "Identities", externalDocs = @ExternalDocumentation(url = "https://xeres.io/docs/api/identity", description = "Identity documentation"))
@RestController
@RequestMapping(value = IDENTITY_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
public class IdentityController
{
	private final IdentityService identityService;

	public IdentityController(IdentityService identityService)
	{
		this.identityService = identityService;
	}

	@GetMapping("/{id}")
	@Operation(summary = "Return an identity")
	@ApiResponse(responseCode = "200", description = "Identity found")
	@ApiResponse(responseCode = "404", description = "Identity not found", content = @Content(schema = @Schema(implementation = Error.class)))
	public IdentityDTO findIdentityById(@PathVariable long id)
	{
		// XXX: identities or gxs?
		return toDTO(identityService.findIdentityById(id).orElseThrow());
	}

	@GetMapping
	@Operation(summary = "Search all gxs identities", description = "If no search parameters are provided, return all gxs identities")
	@ApiResponse(responseCode = "200", description = "All matched gxs identities")
	public List<IdentityDTO> findIdentities(
			@RequestParam(value = "name", required = false) String name,
			@RequestParam(value = "gxsId", required = false) String gxsId)
	{
		if (isNotBlank(name))
		{
			// XXX: fix to use gxsIdentities
			List<Identity> identity = identityService.findAllIdentitiesByName(name);
			return identity.stream()
					.map(IdentityMapper::toDTO)
					.toList();
		}
		else if (isNotBlank(gxsId))
		{
			// XXX: fix to  use gxsIdentities
			Optional<Identity> identity = identityService.findIdentityByGxsId(new GxsId(Id.toBytes(gxsId)));
			return identity.map(iden -> List.of(toDTO(iden))).orElse(Collections.emptyList());
		}
		return toGxsIdDTOs(identityService.getAllGxsIdentities());
	}
}
