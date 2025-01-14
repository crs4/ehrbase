/*
 * Copyright (c) 2019 Vitasystems GmbH,  Jake Smolka (Hannover Medical School), and Luis Marco-Ruiz(Hannover Medical School).
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

package org.ehrbase.rest.openehr;

import java.net.URI;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import com.nedap.archie.rm.directory.Folder;
import com.nedap.archie.rm.support.identification.ObjectVersionId;

import org.ehrbase.api.exception.InternalServerException;
import org.ehrbase.api.exception.ObjectNotFoundException;
import org.ehrbase.api.exception.PreconditionFailedException;
import org.ehrbase.api.exception.StateConflictException;
import org.ehrbase.api.service.EhrService;
import org.ehrbase.api.service.FolderService;
import org.ehrbase.response.ehrscape.FolderDto;
import org.ehrbase.response.openehr.DirectoryResponseData;
import org.ehrbase.response.openehr.ErrorResponseData;
import org.ehrbase.rest.BaseController;
import org.ehrbase.rest.util.OperationNotesResourcesReaderOpenehr.ApiNotes;
import org.joda.time.DateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import io.swagger.annotations.ResponseHeader;

/**
 * Controller for openEHR /directory endpoints
 */
@Api(tags = "Directory")
@RestController
@RequestMapping(path = "${openehr-api.context-path:/rest/openehr}/v1/ehr")
public class OpenehrDirectoryController extends BaseController {

    private final FolderService folderService;
    private final EhrService ehrService;

    @Autowired
    public OpenehrDirectoryController(FolderService folderService, EhrService ehrService) {
        this.folderService = Objects.requireNonNull(folderService);
        this.ehrService = Objects.requireNonNull(ehrService);
    }

    @PostMapping(value = "/{ehr_id}/directory")
    @ApiOperation("Create a new directory folder associated with the EHR identified by ehr_id.")
    @ApiNotes("directoryPost.md")
    @ApiResponses(value = {
            @ApiResponse(
                    code = 201,
                    response = DirectoryResponseData.class,
                    message = "Created successfully - new FOLDER created was created. Content body is only returned when Prefer header has return=representation, otherwise only headers are returned.",
                    responseHeaders = {
                            @ResponseHeader(name = CONTENT_TYPE, description = RESP_CONTENT_TYPE_DESC, response = MediaType.class),
                            @ResponseHeader(name = LOCATION, description = RESP_LOCATION_DESC, response = URI.class),
                            @ResponseHeader(name = ETAG, description = RESP_ETAG_DESC, response = String.class),
                            @ResponseHeader(name = LAST_MODIFIED, description = RESP_LAST_MODIFIED_DESC, response = long.class)
                    }
            ),
            @ApiResponse(
                    code = 204,
                    message = "No Content - New FOLDER was created but not full representation requested. Details in response headers.",
                    responseHeaders = {
                            @ResponseHeader(name = LOCATION, description = RESP_LOCATION_DESC, response = URI.class),
                            @ResponseHeader(name = ETAG, description = RESP_ETAG_DESC, response = String.class),
                            @ResponseHeader(name = LAST_MODIFIED, description = RESP_LAST_MODIFIED_DESC, response = long.class)
                    }
            ),
            @ApiResponse(
                    code = 400,
                    message = "Bad Request - New FOLDER could not be created due to a malformed request data. Request must be modified to match the expected formats.",
                    response = ErrorResponseData.class
            ),
            @ApiResponse(
                    code = 404,
                    message = "Not Found - New FOLDER could not be created due to the EHR identified by the ehr_id could not be found.",
                    response = ErrorResponseData.class
            )
    })
    @ResponseStatus(value = HttpStatus.CREATED)
    public ResponseEntity<DirectoryResponseData> createFolder(
            @ApiParam(value = REQ_OPENEHR_VERSION) @RequestHeader(value = "openEHR-VERSION", required = false) String openEhrVersion,
            @ApiParam(value = REQ_OPENEHR_AUDIT) @RequestHeader(value = "openEHR-AUDIT_DETAILS", required = false) String openEhrAuditDetails,
            @ApiParam(value = REQ_CONTENT_TYPE_BODY) @RequestHeader(value = CONTENT_TYPE) String contentType,
            @ApiParam(value = REQ_ACCEPT) @RequestHeader(value = ACCEPT, required = false, defaultValue = MediaType.APPLICATION_JSON_VALUE) String accept,
            @ApiParam(value = REQ_PREFER) @RequestHeader(value = PREFER, required = false, defaultValue = RETURN_MINIMAL) String prefer,
            @ApiParam(value = "EHR identifier from resource path after ehr/", required = true) @PathVariable(value = "ehr_id") UUID ehrId,
            @ApiParam(value = "The FOLDER to create.", required = true) @RequestBody Folder folder
    ) {

        // Check for existence of EHR record
        checkEhrExists(ehrId);

        // Check for duplicate directories
        if (this.ehrService.getDirectoryId(ehrId) != null) {
            throw new StateConflictException(
                    String.format("EHR with id %s already contains a directory.", ehrId.toString())
            );
        }

        // Insert New folder
        Optional<FolderDto> dtoOptional = this.folderService.create(
                ehrId,
                folder
        );
        ObjectVersionId folderId;
        var folderDto = dtoOptional.orElseThrow(() -> new InternalServerException("Error creating folder."));
        if (folderDto.getUid() instanceof ObjectVersionId) {
            folderId = (ObjectVersionId) folderDto.getUid();
        } else {
            throw new InternalServerException("Unexpected folder DTO ID type.");
        }

        // Fetch inserted folder for response data
        Optional<FolderDto> newFolder = this.folderService.get(folderId, null);

        if (newFolder.isEmpty()) {
            throw new InternalServerException(
                    "Something went wrong. Folder could be persisted but not fetched again."
            );
        }

        // No representation desired
        return createDirectoryResponse(HttpMethod.POST, prefer, accept, newFolder.get(), ehrId);
    }

    @GetMapping(path = "/{ehr_id}/directory/{version_uid}{?path}")
    @ApiOperation("Get an existing FOLDER in EHR identified by id ")
    @ApiResponses(value = {
            @ApiResponse(
                    code = 200,
                    response = DirectoryResponseData.class,
                    message = "Success - FOLDER found and will be returned inside response body.",
                    responseHeaders = {
                            @ResponseHeader(name = CONTENT_TYPE, description = RESP_CONTENT_TYPE_DESC, response = MediaType.class),
                            @ResponseHeader(name = LOCATION, description = RESP_LOCATION_DESC, response = URI.class),
                            @ResponseHeader(name = ETAG, description = RESP_ETAG_DESC, response = String.class),
                            @ResponseHeader(name = LAST_MODIFIED, description = RESP_LAST_MODIFIED_DESC, response = long.class)
                    }
            ),
            @ApiResponse(
                    code = 204,
                    message = "No Content - No FOLDER found at specified path."
            ),
            @ApiResponse(
                    code = 404,
                    message = ("Not Found - Either specified EHR with ehr_id or directory with the directory_id do not exist.")
            )
    })
    public ResponseEntity<DirectoryResponseData> getFolder(
            @ApiParam(value = REQ_ACCEPT) @RequestHeader(value = ACCEPT, required = false, defaultValue = MediaType.APPLICATION_JSON_VALUE) String accept,
            @ApiParam(value = "EHR identifier from resource path after ehr/", required = true) @PathVariable(value = "ehr_id") UUID ehrId,
            @ApiParam(value = "DIRECTORY identifier from resource path after directory/", required = true) @PathVariable(value = "version_uid") ObjectVersionId folderId,
            @ApiParam(value = "Path parameter to specify a subfolder at directory") @RequestParam(value = "path", required = false) String path
    ) {

        // Path value
        if (path != null && !isValidPath(path)) {
            throw new IllegalArgumentException("Value for path is malformed. Expecting a unix like notation, e.g. '/episodes/a/b/c'");
        }

        // Check if EHR for the folder exists
        checkEhrExists(ehrId);
        checkEhrExists(ehrId);

        // Get the folder entry from database
        Optional<FolderDto> foundFolder = folderService.get(
                folderId,
                path
        );
        if (foundFolder.isEmpty()) {
            throw new ObjectNotFoundException(
                    "DIRECTORY",
                    String.format(
                            "Folder with id %s does not exist.",
                            folderId.toString()
                    )
            );
        }

        return createDirectoryResponse(HttpMethod.GET, RETURN_REPRESENTATION, accept, foundFolder.get(), ehrId);
    }

    @GetMapping(path = "/{ehr_id}/directory{?version_at_time,path}")
    @ApiOperation("Get an existing FOLDER in EHR which was actual at given version at time.")
    @ApiResponses(value = {
            @ApiResponse(
                    code = 200,
                    response = DirectoryResponseData.class,
                    message = "Success - FOLDER found and will be returned inside response body.",
                    responseHeaders = {
                            @ResponseHeader(name = CONTENT_TYPE, description = RESP_CONTENT_TYPE_DESC, response = MediaType.class),
                            @ResponseHeader(name = LOCATION, description = RESP_LOCATION_DESC, response = URI.class),
                            @ResponseHeader(name = ETAG, description = RESP_ETAG_DESC, response = String.class),
                            @ResponseHeader(name = LAST_MODIFIED, description = RESP_LAST_MODIFIED_DESC, response = long.class)
                    }
            ),
            @ApiResponse(
                    code = 204,
                    message = "No Content - EHR has no version at time or no folder at path"
            ),
            @ApiResponse(
                    code = 404,
                    message = ("Not Found - Specified EHR with ehr_id does not exist.")
            )
    })
    public ResponseEntity<DirectoryResponseData> getFolderVersionAtTime(
            @ApiParam(value = REQ_ACCEPT) @RequestHeader(value = ACCEPT, required = false, defaultValue = MediaType.APPLICATION_JSON_VALUE) String accept,
            @ApiParam(value = "EHR identifier from resource path after ehr/", required = true) @PathVariable(value = "ehr_id") UUID ehrId,
            @ApiParam(value = "Timestamp in extended ISO8601 format to identify version of folder.") @RequestParam(value = "version_at_time", required = false) Instant versionAtTime,
            @ApiParam(value = "Path parameter to specify a sub folder at directory") @RequestParam(value = "path", required = false) String path
    ) {
        // Check path string if they are valid
        if (path != null && !isValidPath(path)) {
            throw new IllegalArgumentException("Value for path is malformed. Expecting a unix like notation, e.g. '/episodes/a/b/c'");
        }

        // Check ehr exists
        checkEhrExists(ehrId);

        // Get directory root entry for ehr
        UUID directoryUuid = ehrService.getDirectoryId(ehrId);
        if (directoryUuid == null) {
            throw new ObjectNotFoundException(
                    "DIRECTORY",
                    String.format(
                            "There is no directory stored for EHR with id %s. Maybe it has been deleted?",
                            ehrId.toString()
                    )
            );
        }
        ObjectVersionId directoryId = new ObjectVersionId(directoryUuid.toString());

        final Optional<FolderDto> foundFolder;
        // Get the folder entry from database
        if (versionAtTime != null) {
            foundFolder = folderService.getByTimeStamp(
                    directoryId,
                    Timestamp.from(versionAtTime),
                    path
            );
        } else {
            foundFolder = folderService.getLatest(directoryId, path);
        }
        if (foundFolder.isEmpty()) {
            throw new ObjectNotFoundException("folder",
                    "The FOLDER for ehrId " +
                            ehrId.toString() +
                            " does not exist.");
        }

        return createDirectoryResponse(HttpMethod.GET, RETURN_REPRESENTATION, accept, foundFolder.get(), ehrId);
    }

    @PutMapping(path = "/{ehr_id}/directory")
    @ApiOperation("Update an existing folder in directory. The folder will be identified by the latest version_uid specified in the If-Match header")
    @ApiNotes("directoryPut.md")
    @ApiResponses(value = {
            @ApiResponse(
                    code = 200,
                    response = DirectoryResponseData.class,
                    message = "Success - FOLDER has been updated successfully.",
                    responseHeaders = {
                            @ResponseHeader(name = CONTENT_TYPE, description = RESP_CONTENT_TYPE_DESC, response = MediaType.class),
                            @ResponseHeader(name = LOCATION, description = RESP_LOCATION_DESC, response = URI.class),
                            @ResponseHeader(name = ETAG, description = RESP_ETAG_DESC, response = String.class),
                            @ResponseHeader(name = LAST_MODIFIED, description = RESP_LAST_MODIFIED_DESC, response = long.class)
                    }
            ),
            @ApiResponse(
                    code = 204,
                    message = "Success - FOLDER has been updated successfully but no representation has been requested. Details at response headers.",
                    responseHeaders = {
                            @ResponseHeader(name = LOCATION, description = RESP_LOCATION_DESC, response = URI.class),
                            @ResponseHeader(name = ETAG, description = RESP_ETAG_DESC, response = String.class),
                            @ResponseHeader(name = LAST_MODIFIED, description = RESP_LAST_MODIFIED_DESC, response = long.class)
                    }
            ),
            @ApiResponse(
                    code = 400,
                    response = ErrorResponseData.class,
                    message = "Bad Request - FOLDER could not be updated due to one or more malformed request parameters or data."
            ),
            @ApiResponse(
                    code = 404,
                    response = ErrorResponseData.class,
                    message = "Not Found - EHR with given id from path could not be found."
            ),
            @ApiResponse(
                    code = 412,
                    response = ErrorResponseData.class,
                    message = "Precondition failed - The version specified in the If-Match header does not match the latest version of the FOLDER. Returns the latest version_uid in Location and ETag header.",
                    responseHeaders = {
                            @ResponseHeader(name = LOCATION, description = RESP_LOCATION_DESC, response = URI.class),
                            @ResponseHeader(name = ETAG, description = RESP_ETAG_DESC, response = URI.class)
                    }
            )
    })
    public ResponseEntity<DirectoryResponseData> updateFolder(
            @ApiParam(value = REQ_OPENEHR_VERSION) @RequestHeader(value = "openEHR-VERSION", required = false) String openEhrVersion,
            @ApiParam(value = REQ_OPENEHR_AUDIT) @RequestHeader(value = "openEHR-AUDIT_DETAILS", required = false) String openEhrAuditDetails,
            @ApiParam(value = REQ_CONTENT_TYPE_BODY) @RequestHeader(value = CONTENT_TYPE) String contentType,
            @ApiParam(value = REQ_ACCEPT) @RequestHeader(value = ACCEPT, required = false, defaultValue = MediaType.APPLICATION_JSON_VALUE) String accept,
            @ApiParam(value = REQ_PREFER) @RequestHeader(value = PREFER, required = false, defaultValue = RETURN_MINIMAL) String prefer,
            @ApiParam(value = "{preceding_version_uid}", required = true) @RequestHeader(value = IF_MATCH) ObjectVersionId folderId,
            @ApiParam(value = "EHR identifier from resource path after ehr/", required = true) @PathVariable(value = "ehr_id") UUID ehrId,
            @ApiParam(value = "Update data for the target FOLDER") @RequestBody Folder folder
    ) {

        // Check if directory is set and ehr exists
        checkEhrExists(ehrId);
        checkDirectoryExists(ehrId);

        // Check version conflicts and throw precondition failed exception if not
        checkDirectoryVersionConflicts(folderId, ehrId);

        // Update folder and get new version
        Optional<FolderDto> updatedFolder = this.folderService.update(
            ehrId,
            folderId,
            folder
        );


        if (updatedFolder.isEmpty()) {
            throw new InternalServerException(
                    "Something went wrong. Folder could be persisted but not fetched again."
            );
        }

        return createDirectoryResponse(HttpMethod.PUT, prefer, accept, updatedFolder.get(), ehrId);
    }

    @DeleteMapping(path = "/{ehr_id}/directory")
    @ApiOperation("Delete an existing folder in directory. The folder will be identified by the latest version_uid specified in the If-Match header")
    @ApiResponses(value = {
            @ApiResponse(
                    code = 204,
                    message = "Success - DIRECTORY has been deleted successfully.",
                    responseHeaders = {
                            @ResponseHeader(name = ETAG, description = RESP_ETAG_DESC, response = String.class),
                            @ResponseHeader(name = LAST_MODIFIED, description = RESP_LAST_MODIFIED_DESC, response = long.class)
                    }
            ),
            @ApiResponse(
                    code = 400,
                    response = ErrorResponseData.class,
                    message = "Bad Request - FOLDER could not be deleted due to one or more malformed request parameters or data."
            ),
            @ApiResponse(
                    code = 404,
                    response = ErrorResponseData.class,
                    message = "Not Found - EHR with given id from path could not be found."
            ),
            @ApiResponse(
                    code = 412,
                    response = ErrorResponseData.class,
                    message = "Precondition failed - The version specified in the If-Match header does not match the latest version of the FOLDER. Returns the latest version_uid in Location and ETag header.",
                    responseHeaders = {
                            @ResponseHeader(name = LOCATION, description = RESP_LOCATION_DESC, response = URI.class),
                            @ResponseHeader(name = ETAG, description = RESP_ETAG_DESC, response = URI.class)
                    }
            )
    })
    public ResponseEntity<DirectoryResponseData> deleteFolder(
            @ApiParam(value = REQ_OPENEHR_VERSION) @RequestHeader(value = "openEHR-VERSION", required = false) String openEhrVersion,
            @ApiParam(value = REQ_OPENEHR_AUDIT) @RequestHeader(value = "openEHR-AUDIT_DETAILS", required = false) String openEhrAuditDetails,
            @ApiParam(value = REQ_ACCEPT) @RequestHeader(value = ACCEPT, required = false, defaultValue = MediaType.APPLICATION_JSON_VALUE) String accept,
            @ApiParam(value = "{preceding_version_uid}", required = true) @RequestHeader(value = IF_MATCH) ObjectVersionId folderId,
            @ApiParam(value = "EHR identifier from resource path after ehr/", required = true) @PathVariable(value = "ehr_id") String ehrIdString
    ) {
        UUID ehrId = getEhrUuid(ehrIdString);

        // Check if directory is set and ehr exists
        checkEhrExists(ehrId);
        checkDirectoryExists(ehrId);

        // Check version conflicts and throw precondition failed exception if not
        checkDirectoryVersionConflicts(folderId, ehrId);

        this.folderService.delete(ehrId, folderId);
        this.ehrService.removeDirectory(ehrId);
        return createDirectoryResponse(HttpMethod.DELETE, null, accept, null, ehrId);
    }

    private DirectoryResponseData buildResponse(FolderDto folderDto) {

        DirectoryResponseData resBody = new DirectoryResponseData();
        resBody.setDetails(folderDto.getDetails());
        resBody.setFolders(folderDto.getFolders());
        resBody.setItems(folderDto.getItems());
        resBody.setName(folderDto.getName());
        resBody.setUid(folderDto.getUid());
        return resBody;
    }

    private ResponseEntity<DirectoryResponseData> createDirectoryResponse(HttpMethod method, String prefer, String accept, FolderDto folderDto, UUID ehrId) {

        HttpHeaders headers = new HttpHeaders();
        HttpStatus successStatus;
        DirectoryResponseData body;

        if (prefer != null && prefer.equals(RETURN_REPRESENTATION)) {
            headers.setContentType(resolveContentType(accept, MediaType.APPLICATION_XML));
            body = buildResponse(folderDto);
            successStatus = getSuccessStatus(method);
        } else {
            body = null;
            successStatus = HttpStatus.NO_CONTENT;
        }

        if (folderDto != null) {

            String versionUid = folderDto.getUid().toString();

            headers.setETag("\"" + versionUid + "\"");
            headers.setLocation(
                    URI.create(encodePath(getBaseEnvLinkURL() +
                            "/rest/openehr/v1/ehr/" + ehrId.toString() +
                            "/directory/" + versionUid))
            );
            // TODO: Extract last modified from SysPeriod timestamp of fetched folder record
            headers.setLastModified(DateTime.now().getMillis());
        }

        return new ResponseEntity<>(body, headers, successStatus);

    }

    private HttpStatus getSuccessStatus(HttpMethod method) {

        switch (method) {
            case POST: {
                return HttpStatus.CREATED;
            }
            case DELETE: {
                return HttpStatus.NO_CONTENT;
            }
            default: {
                return HttpStatus.OK;
            }
        }
    }

    /**
     * Checks if a given path is a valid path value, i.e. a unix like notation of a path which allows trailing forward
     * slashes as well as only one slash which is equivalent to the root folder and would not have any effect on the
     * result.
     *
     * @param path - String to check
     * @return String is a valid path value or not
     */
    private boolean isValidPath(String path) {
        Pattern pathPattern = Pattern.compile("^(?:/?(?:\\w+|\\s|-)*/?)+$");
        return pathPattern.matcher(path).matches();
    }

    private void checkEhrExists(UUID ehrId) {
        if (!this.ehrService.doesEhrExist(ehrId)) {
            throw new ObjectNotFoundException(
                    "DIRECTORY",
                    String.format("EHR with id %s not found", ehrId.toString())
            );
        }
    }

    private void checkDirectoryExists(UUID ehrId) {

        if (this.ehrService.getDirectoryId(ehrId) == null) {
            throw new PreconditionFailedException(
                    String.format(
                            "EHR with id %s does not contain a directory. Maybe it has been deleted?",
                            ehrId.toString()
                    )
            );
        }
    }

    private void checkDirectoryVersionConflicts(ObjectVersionId requestedFolderId, UUID ehrId) {

        UUID directoryUuid = this.ehrService.getDirectoryId(ehrId);

        int latestVersion = this.folderService.getLastVersionNumber(new ObjectVersionId(directoryUuid.toString()));
        // TODO: Change column 'directory' in EHR to String with ObjectVersionId
        String directoryId = String.format(
                "%s::%s::%d",
                directoryUuid,
                this.ehrService.getServerConfig().getNodename(),
                latestVersion
        );


        if (requestedFolderId != null && !requestedFolderId.toString().equals(directoryId)) {

            throw new PreconditionFailedException(
                    "If-Match version_uid does not match latest version.",
                    directoryId,
                    encodePath(getBaseEnvLinkURL()
                            + "/rest/openehr/v1/ehr/"
                            + ehrId.toString()
                            + "/directory/" + directoryId
                    )
            );
        }
    }
}

