/*
 * Copyright (c) 2019 Stefan Spiska (Vitasystems GmbH) and Jake Smolka (Hannover Medical School).
 *
 * This file is part of project EHRbase
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ehrbase.rest.ehrscape.controller;

import com.nedap.archie.rm.composition.Composition;
import com.nedap.archie.rm.support.identification.ObjectVersionId;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.apache.commons.lang3.StringUtils;
import org.ehrbase.api.exception.InternalServerException;
import org.ehrbase.api.exception.InvalidApiParameterException;
import org.ehrbase.api.service.CompositionService;
import org.ehrbase.response.ehrscape.CompositionDto;
import org.ehrbase.response.ehrscape.CompositionFormat;
import org.ehrbase.response.ehrscape.StructuredString;
import org.ehrbase.rest.ehrscape.responsedata.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping(path = "/rest/ecis/v1/composition", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
@Api(value = "Composition Endpoint")
public class CompositionController extends BaseController {

    private final CompositionService compositionService;

    @Autowired
    public CompositionController(CompositionService compositionService) {

        this.compositionService = Objects.requireNonNull(compositionService);
    }

    @PostMapping()
    @ApiOperation(value = "Add a Composition to an EHR")
    public ResponseEntity<CompositionWriteRestResponseData> createComposition(@ApiParam(value = "Format of the Composition", allowableValues = "XML, ECISFLAT, FLAT") @RequestParam(value = "format", defaultValue = "XML") CompositionFormat format,
                                                                              @RequestParam(value = "templateId", required = false) String templateId,
                                                                              @RequestParam(value = "link", required = false) UUID linkId,
                                                                              @ApiParam(value = "Id of the EHR to which the Composition is added") @RequestParam(value = "ehrId") UUID ehrId,
                                                                              @RequestBody String content) {

        if ((format == CompositionFormat.FLAT || format == CompositionFormat.ECISFLAT) && StringUtils.isEmpty(templateId)) {
            throw new InvalidApiParameterException(String.format("Template Id needs to specified for format %s", format));
        }

        var compoObj = compositionService.buildComposition(content, format, templateId);

        Optional<CompositionDto> optionalCompositionDto = compositionService.create(ehrId, compoObj);

        var compositionUuid = optionalCompositionDto.orElseThrow(() ->
            new InternalServerException("Failed to create composition"))
            .getUuid();

        CompositionWriteRestResponseData responseData = new CompositionWriteRestResponseData();
        responseData.setAction(Action.CREATE);
        responseData.setCompositionUid(compositionUuid + "::" + compositionService.getServerConfig().getNodename() + "::" + 1);
        responseData.setMeta(buildMeta(responseData.getCompositionUid()));
        return ResponseEntity.ok(responseData);
    }


    @GetMapping(path = "/{id}")
    @ApiOperation(value = "Retrieve a Composition by Id")
    public ResponseEntity<CompositionResponseData> getComposition(@ApiParam(value = "Id of the Composition in the form uuid::version") @PathVariable("id") String compositionId, @ApiParam(value = "Format of the Composition", allowableValues = "XML, ECISFLAT, FLAT, RAW") @RequestParam(value = "format", defaultValue = "XML") CompositionFormat format) {
        final Integer version;
        final UUID uuid;
        if (compositionId.contains("::")) {
            version = getCompositionVersion(compositionId); //version number is inorder: 1, 2, 3 etc.
            uuid = getCompositionUid(compositionId);
        } else {
            uuid = getCompositionUid(compositionId);
            version = null;
        }
        Optional<CompositionDto> compositionDto = compositionService.retrieve(uuid, version);
        if (compositionDto.isPresent()) {

            // Serialize onto target format
            StructuredString serialize = compositionService.serialize(compositionDto.get(), format);

            CompositionResponseData responseDto = new CompositionResponseData();
            responseDto.setComposition(serialize);
            responseDto.setAction(Action.RETRIEVE);
            responseDto.setFormat(format);
            responseDto.setTemplateId(compositionDto.get().getTemplateId());
            responseDto.setCompositionUid(compositionDto.get().getUuid().toString());
            responseDto.setEhrId(compositionDto.get().getEhrId());
            Meta meta = buildMeta(responseDto.getCompositionUid());
            responseDto.setMeta(meta);
            return ResponseEntity.ok(responseDto);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping(path = "/{id}")
    @ApiOperation(value = "Update a Composition")
    public ResponseEntity<ActionRestResponseData> update(@ApiParam(value = "UUID of the Composition ") @PathVariable("id") UUID compositionId, @ApiParam(value = "Format of the Composition", allowableValues = "XML, ECISFLAT, FLAT") @RequestParam(value = "format", defaultValue = "XML") CompositionFormat format,
                                                         @RequestParam(value = "templateId", required = false) String templateId,
                                                         @RequestBody String content) {

        if ((format == CompositionFormat.FLAT || format == CompositionFormat.ECISFLAT) && StringUtils.isEmpty(templateId)) {
            throw new InvalidApiParameterException(String.format("Template Id needs to specified for format %s", format));
        }

        var compoObj = compositionService.buildComposition(content, format, templateId);
        ObjectVersionId latestVersionId = getLatestVersionId(compositionId);
        UUID ehrId = getEhrId(compositionId);
        // Actual update
        Optional<CompositionDto> dtoOptional = compositionService
            .update(ehrId, latestVersionId, compoObj);

        var compositionVersionUid = dtoOptional.orElseThrow(() ->
            new InternalServerException("Failed to create composition"))
            .getComposition().getUid().toString();
        ActionRestResponseData responseData = new ActionRestResponseData();
        responseData.setAction(Action.UPDATE);
        responseData.setMeta(buildMeta(compositionVersionUid));
        return ResponseEntity.ok(responseData);
    }

    @DeleteMapping(path = "/{id}")
    @ApiOperation(value = "Delete a Composition")
    public ResponseEntity<ActionRestResponseData> delete(@ApiParam(value = "UUID of the Composition ") @PathVariable("id") UUID compositionId) {

        ObjectVersionId latestVersionId = getLatestVersionId(compositionId);
        UUID ehrId = getEhrId(compositionId);
        compositionService.delete(ehrId, latestVersionId);
        ActionRestResponseData responseData = new ActionRestResponseData();
        responseData.setAction(Action.DELETE);
        responseData.setMeta(buildMeta(""));
        return ResponseEntity.ok(responseData);
    }

    private UUID getEhrId(UUID compositionId) {
        // EhrScape API doesn't have access to the EHR ID here, so it needs to be retrieved.
        // Version 1 is enough because EHR never changes & it is always available.
        Optional<CompositionDto> dtoOptionalForEhr = compositionService.retrieve(compositionId, 1);
        return dtoOptionalForEhr
            .orElseThrow(() -> new InvalidApiParameterException("Invalid composition ID."))
            .getEhrId();
    }

    private ObjectVersionId getLatestVersionId(UUID compositionId) {
        // EhrScape API doesn't have access to the "If-Match" header or previous version, so it needs to be retrieved.
        return new ObjectVersionId(compositionId.toString(),
            compositionService.getServerConfig().getNodename(),
            compositionService.getLastVersionNumber(compositionId).toString());
    }

    private Meta buildMeta(String compositionUid) {
        RestHref url = new RestHref();
        url.setUrl(getBaseEnvLinkURL() + "/rest/ecis/v1/composition/" + compositionUid);
        Meta meta = new Meta();
        meta.setHref(url);
        return meta;
    }

    private UUID getCompositionUid(String fullcompositionUid) {
        if (!fullcompositionUid.contains("::"))
            return UUID.fromString(fullcompositionUid);
        return UUID.fromString(fullcompositionUid.substring(0, fullcompositionUid.indexOf("::")));
    }

    private int getCompositionVersion(String fullcompositionUid) {
        if (!fullcompositionUid.contains("::"))
            return 1; //current version
        return Integer.valueOf(fullcompositionUid.substring(fullcompositionUid.lastIndexOf("::") + 2));
    }
}
