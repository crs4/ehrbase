package org.ehrbase.rest.openehr;

import com.nedap.archie.rm.changecontrol.OriginalVersion;
import com.nedap.archie.rm.ehr.EhrStatus;
import com.nedap.archie.rm.ehr.VersionedEhrStatus;
import com.nedap.archie.rm.generic.RevisionHistory;
import io.swagger.annotations.*;
import org.ehrbase.api.exception.InternalServerException;
import org.ehrbase.api.exception.InvalidApiParameterException;
import org.ehrbase.api.exception.ObjectNotFoundException;
import org.ehrbase.api.service.ContributionService;
import org.ehrbase.api.service.EhrService;
import org.ehrbase.response.ehrscape.ContributionDto;
import org.ehrbase.response.openehr.OriginalVersionResponseData;
import org.ehrbase.response.openehr.RevisionHistoryResponseData;
import org.ehrbase.response.openehr.VersionedObjectResponseData;
import org.ehrbase.rest.BaseController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Controller for /ehr/{ehrId}/versioned_ehr_status resource of openEHR REST API
 */
@Api
@RestController
@RequestMapping(path = "${openehr-api.context-path:/rest/openehr}/v1/ehr/{ehr_id}/versioned_ehr_status", produces = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_XML_VALUE})
public class OpenehrVersionedEhrStatusController extends BaseController {

    private final EhrService ehrService;
    private final ContributionService contributionService;

    @Autowired
    public OpenehrVersionedEhrStatusController(EhrService ehrService, ContributionService contributionService) {
        this.ehrService = Objects.requireNonNull(ehrService);
        this.contributionService = Objects.requireNonNull(contributionService);
    }

    @GetMapping
    @ApiOperation(value = "Retrieves a VERSIONED_EHR_STATUS associated with an EHR identified by ehr_id.", response = VersionedObjectResponseData.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Ok - requested VERSIONED_EHR_STATUS is successfully retrieved.",
                    responseHeaders = {
                            @ResponseHeader(name = CONTENT_TYPE, description = RESP_CONTENT_TYPE_DESC, response = MediaType.class)
                    }),
            @ApiResponse(code = 404, message = "Not Found - EHR with ehr_id does not exist."),
            @ApiResponse(code = 406, message = "Not Acceptable - Service can not fulfil requested Accept format.")})
    public ResponseEntity<VersionedObjectResponseData<EhrStatus>> retrieveVersionedEhrStatusByEhr(
            @ApiParam(value = "Client should specify expected response format") @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept,
            @ApiParam(value = "User supplied EHR ID", required = true) @PathVariable(value = "ehr_id") String ehrIdString) {

        UUID ehrId = getEhrUuid(ehrIdString);

        // check if EHR is valid
        if(ehrService.hasEhr(ehrId).equals(Boolean.FALSE)) {
            throw new ObjectNotFoundException("ehr", "No EHR with this ID can be found");
        }

        VersionedEhrStatus versionedEhrStatus = ehrService.getVersionedEhrStatus(ehrId);

        VersionedObjectResponseData<EhrStatus> response = new VersionedObjectResponseData<>(versionedEhrStatus);

        HttpHeaders respHeaders = new HttpHeaders();
        respHeaders.setContentType(resolveContentType(accept));

        return ResponseEntity.ok().headers(respHeaders).body(response);
    }

    @GetMapping(path = "/revision_history")
    @ApiOperation(value = "Retrieves a VERSIONED_EHR_STATUS associated with an EHR identified by ehr_id.", response = RevisionHistoryResponseData.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Ok - requested VERSIONED_EHR_STATUS is successfully retrieved.",
                    responseHeaders = {
                            @ResponseHeader(name = CONTENT_TYPE, description = RESP_CONTENT_TYPE_DESC, response = MediaType.class)
                    }),
            @ApiResponse(code = 404, message = "Not Found - EHR with ehr_id does not exist."),
            @ApiResponse(code = 406, message = "Not Acceptable - Service can not fulfil requested Accept format.")})
    public ResponseEntity<RevisionHistoryResponseData> retrieveVersionedEhrStatusRevisionHistoryByEhr(
            @ApiParam(value = "Client should specify expected response format") @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept,
            @ApiParam(value = "User supplied EHR ID", required = true) @PathVariable(value = "ehr_id") String ehrIdString) {

        UUID ehrId = getEhrUuid(ehrIdString);

        // check if EHR is valid
        if(ehrService.hasEhr(ehrId).equals(Boolean.FALSE)) {
            throw new ObjectNotFoundException("ehr", "No EHR with this ID can be found");
        }

        RevisionHistory revisionHistory = ehrService.getRevisionHistoryOfVersionedEhrStatus(ehrId);

        RevisionHistoryResponseData response = new RevisionHistoryResponseData(revisionHistory);

        HttpHeaders respHeaders = new HttpHeaders();
        respHeaders.setContentType(resolveContentType(accept));

        return ResponseEntity.ok().headers(respHeaders).body(response);
    }

    @GetMapping(path = "/version")
    // checkAbacPre /-Post attributes (type, subject, payload, content type)
    @PreAuthorize("checkAbacPre(@openehrVersionedEhrStatusController.EHR_STATUS, "
        + "@ehrService.getSubjectExtRef(#ehrIdString), null, null)")
    @ApiOperation(value = "Retrieves the VERSION of an EHR_STATUS associated with the EHR identified by ehr_id. If version_at_time is supplied, retrieves the VERSION extant at specified time, otherwise retrieves the latest VERSION.", response = OriginalVersionResponseData.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Ok - requested VERSION is successfully retrieved.",
                    responseHeaders = {
                            @ResponseHeader(name = CONTENT_TYPE, description = RESP_CONTENT_TYPE_DESC, response = MediaType.class)
                    }),
            @ApiResponse(code = 404, message = "Not Found - EHR with ehr_id does not exist."),
            @ApiResponse(code = 406, message = "Not Acceptable - Service can not fulfil requested Accept format.")})
    public ResponseEntity<OriginalVersionResponseData<EhrStatus>> retrieveVersionOfEhrStatusByTime(
            @ApiParam(value = "Client should specify expected response format") @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept,
            @ApiParam(value = "User supplied EHR ID", required = true) @PathVariable(value = "ehr_id") String ehrIdString,
            @ApiParam(value = "A timestamp in the ISO8601 format", hidden = true) @RequestParam(value = "version_at_time", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime versionAtTime) {

        UUID ehrId = getEhrUuid(ehrIdString);

        // check if EHR is valid
        if(ehrService.hasEhr(ehrId).equals(Boolean.FALSE)) {
            throw new ObjectNotFoundException("ehr", "No EHR with this ID can be found");
        }

        UUID versionedObjectId = ehrService.getEhrStatusVersionedObjectUidByEhr(ehrId);
        int version;
        if (versionAtTime != null) {
            version = ehrService.getEhrStatusVersionByTimestamp(ehrId, Timestamp.valueOf(versionAtTime));
        } else {
            version = Integer.parseInt(ehrService.getLatestVersionUidOfStatus(ehrId).split("::")[2]);
        }

        Optional<OriginalVersion<EhrStatus>> ehrStatusOriginalVersion = ehrService.getEhrStatusAtVersion(ehrId, versionedObjectId, version);
        UUID contributionId = ehrStatusOriginalVersion
                .map(i -> UUID.fromString(i.getContribution().getId().getValue()))
                .orElseThrow(() -> new InvalidApiParameterException("Couldn't retrieve EhrStatus with given parameters"));

        Optional<ContributionDto> optionalContributionDto = contributionService.getContribution(ehrId, contributionId);
        ContributionDto contributionDto = optionalContributionDto.orElseThrow(() -> new InternalServerException("Couldn't fetch contribution for existing EhrStatus")); // shouldn't happen

        OriginalVersionResponseData<EhrStatus> originalVersionResponseData = new OriginalVersionResponseData<>(ehrStatusOriginalVersion.get(), contributionDto);

        HttpHeaders respHeaders = new HttpHeaders();
        respHeaders.setContentType(resolveContentType(accept));

        return ResponseEntity.ok().headers(respHeaders).body(originalVersionResponseData);
    }

    @GetMapping(path = "/version/{version_uid}")
    // checkAbacPre /-Post attributes (type, subject, payload, content type)
    @PreAuthorize("checkAbacPre(@openehrVersionedEhrStatusController.EHR_STATUS, "
        + "@ehrService.getSubjectExtRef(#ehrIdString), null, null)")
    @ApiOperation(value = "Retrieves a VERSION identified by version_uid of an EHR_STATUS associated with the EHR identified by ehr_id.", response = OriginalVersionResponseData.class)
    @ApiResponses(value = {
            @ApiResponse(code = 200, message = "Ok - requested VERSION is successfully retrieved.",
                    responseHeaders = {
                            @ResponseHeader(name = CONTENT_TYPE, description = RESP_CONTENT_TYPE_DESC, response = MediaType.class)
                    }),
            @ApiResponse(code = 404, message = "Not Found - EHR with ehr_id does not exist."),
            @ApiResponse(code = 406, message = "Not Acceptable - Service can not fulfil requested Accept format.")})
    public ResponseEntity<OriginalVersionResponseData<EhrStatus>> retrieveVersionOfEhrStatusByVersionUid(
            @ApiParam(value = "Client should specify expected response format") @RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept,
            @ApiParam(value = "User supplied EHR ID", required = true) @PathVariable(value = "ehr_id") String ehrIdString,
            @ApiParam(value = "User supplied VERSION identifier", required = true) @PathVariable(value = "version_uid") String versionUid) {

        UUID ehrId = getEhrUuid(ehrIdString);

        // check if EHR is valid
        if(ehrService.hasEhr(ehrId).equals(Boolean.FALSE)) {
            throw new ObjectNotFoundException("ehr", "No EHR with this ID can be found.");
        }

        // parse given version uid
        UUID versionedObjectId;
        int version;
        try {
            versionedObjectId = UUID.fromString(versionUid.split("::")[0]);
            version = Integer.parseInt(versionUid.split("::")[2]);
        } catch (Exception e) {
            throw new InvalidApiParameterException("VERSION UID parameter has wrong format: " + e.getMessage());
        }

        if (version < 1)
            throw new InvalidApiParameterException("Version can't be negative.");

        if(!ehrService.hasStatus(versionedObjectId)) {
            throw new ObjectNotFoundException("ehr_status", "No EHR_STATUS with given ID can be found.");
        }

        Optional<OriginalVersion<EhrStatus>> ehrStatusOriginalVersion = ehrService.getEhrStatusAtVersion(ehrId, versionedObjectId, version);
        UUID contributionId = ehrStatusOriginalVersion
                .map(i -> UUID.fromString(i.getContribution().getId().getValue()))
                .orElseThrow(() -> new InvalidApiParameterException("Couldn't retrieve EhrStatus with given parameters"));

        Optional<ContributionDto> optionalContributionDto = contributionService.getContribution(ehrId, contributionId);
        ContributionDto contributionDto = optionalContributionDto.orElseThrow(() -> new InternalServerException("Couldn't fetch contribution for existing EhrStatus")); // shouldn't happen

        OriginalVersionResponseData<EhrStatus> originalVersionResponseData = new OriginalVersionResponseData<>(ehrStatusOriginalVersion.get(), contributionDto);

        HttpHeaders respHeaders = new HttpHeaders();
        respHeaders.setContentType(resolveContentType(accept));

        return ResponseEntity.ok().headers(respHeaders).body(originalVersionResponseData);
    }
}
